package com.example.myapplication2222;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.StorageException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;

public class FaceVerificationActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 2001;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private ProgressBar progressBar;
    private Button verifyFaceButton;
    private ImageView liveFaceImageView;
    private Bitmap liveFaceBitmap;
    private static final float FACE_MATCH_THRESHOLD = 0.75f;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private String userId; // 사용자 UID
    private Bitmap storedFaceBitmap; // Firebase에서 불러온 저장된 얼굴 이미지
    private Interpreter tflite; // TensorFlow Lite 인터프리터
    private Button switchCameraButton; // 카메라 전환 버튼 추가
    private boolean isUsingFrontCamera = false; // 카메라 방향 상태를 저장
    private ImageView storedFaceImageView; // 저장된 얼굴 이미지 뷰 추가

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_verification);

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressBar);
        verifyFaceButton = findViewById(R.id.verifyFaceButton);
        storedFaceImageView = findViewById(R.id.storedFaceImageView);
        liveFaceImageView = findViewById(R.id.liveFaceImageView);
        switchCameraButton = findViewById(R.id.switchCameraButton); // 버튼 초기화
        switchCameraButton.setOnClickListener(v -> switchCamera()); // 클릭 리스너 추가

        // Firebase Storage에서 사용자 UID에 해당하는 얼굴 이미지 로드
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            loadStoredFaceImage(); // userId가 설정된 후에 loadStoredFaceImage를 호출해야 함
        } else {
            // 사용자 로그인되지 않은 경우 처리
            Toast.makeText(this, "사용자가 로그인되지 않았습니다.", Toast.LENGTH_SHORT).show();
        }
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }

        verifyFaceButton.setOnClickListener(v -> {
            takeLivePhotoAndCompare(); // 필요 시 실시간 얼굴 캡처 메서드를 호출하도록 수정
        });

        try {
            loadMobileFaceNetModel(); // TensorFlow Lite 모델 초기화
        } catch (IOException e) {
            Log.e("FaceVerificationActivity", "DNN 모델 로드 실패", e);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadMobileFaceNetModel() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("mobilefacenet.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        tflite = new Interpreter(tfliteModel);
    }
    private void switchCamera() {
        isUsingFrontCamera = !isUsingFrontCamera; // 카메라 방향 토글
        cameraSelector = isUsingFrontCamera ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA; // 카메라 선택기 설정
        startCamera(); // 카메라 다시 시작
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                if (cameraProvider != null) {
                    bindPreview(cameraProvider);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("FaceVerificationActivity", "카메라 초기화 실패", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().build();

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }
    private Bitmap resizeBitmap(Bitmap bitmap, int width, int height) {
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    private void loadStoredFaceImage() {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("faces/" + userId + "_face.jpg");
        Log.d("FaceVerificationActivity", "Loading face image from: " + storageRef.getPath());

        storageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener(bytes -> {
            storedFaceBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            Toast.makeText(this, "저장된 얼굴 이미지를 불러왔습니다.", Toast.LENGTH_SHORT).show();

            detectFaceUsingMLKit(storedFaceBitmap, new OnFaceDetectedListener() {
                @Override
                public void onFaceDetected(Bitmap croppedFace) {
                    if (croppedFace != null) {
                        runOnUiThread(() -> {
                            storedFaceImageView.setImageBitmap(croppedFace); // 크롭된 얼굴을 이미지뷰에 표시
                        });
                    } else {
                        Log.e("FaceVerificationActivity", "Cropped face is null");
                        runOnUiThread(() -> Toast.makeText(FaceVerificationActivity.this, "크롭된 얼굴이 null입니다.", Toast.LENGTH_SHORT).show());
                    }
                }

                @Override
                public void onFaceDetectionFailed() {
                    Log.e("FaceVerificationActivity", "저장된 얼굴 감지 실패");
                    runOnUiThread(() -> storedFaceImageView.setImageBitmap(storedFaceBitmap)); // 전체 얼굴을 보여줌
                }
            });
        }).addOnFailureListener(e -> {
            Log.e("FaceVerificationActivity", "얼굴 이미지 로드 실패", e);
            Toast.makeText(this, "저장된 얼굴 이미지를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
        });
    }


    private void takeLivePhotoAndCompare() {
        if (imageCapture == null) return;

        File livePhotoFile = new File(getExternalFilesDir(null), "live_photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(livePhotoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Bitmap originalBitmap = BitmapFactory.decodeFile(livePhotoFile.getAbsolutePath());
                if (originalBitmap != null) {
                    int rotationAngle = isUsingFrontCamera ? 270 : 90;  // 전면 카메라는 270도, 후면 카메라는 90도
                    Bitmap rotatedBitmap = rotateBitmap(originalBitmap, rotationAngle);

                    // 얼굴 검출 및 크롭
                    detectFaceUsingMLKit(rotatedBitmap, new OnFaceDetectedListener() {
                        @Override
                        public void onFaceDetected(Bitmap croppedFace) {
                            liveFaceBitmap = croppedFace; // 검출된 얼굴 비트맵 저장
                            runOnUiThread(() -> liveFaceImageView.setImageBitmap(croppedFace)); // 얼굴만 UI에 표시

                            // 얼굴 비교 시작
                            compareFacesUsingMLKit(croppedFace);
                        }

                        @Override
                        public void onFaceDetectionFailed() {
                            runOnUiThread(() -> Toast.makeText(FaceVerificationActivity.this, "실시간 얼굴 감지에 실패했습니다.", Toast.LENGTH_SHORT).show());
                        }
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(FaceVerificationActivity.this, "실시간 얼굴 사진 저장에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("FaceVerificationActivity", "실시간 얼굴 캡처 실패", exception);
            }
        });
    }




    private void compareFacesUsingMLKit(Bitmap liveFaceBitmap) {
        if (storedFaceBitmap == null) {
            runOnUiThread(() -> Toast.makeText(this, "저장된 얼굴 이미지가 없습니다.", Toast.LENGTH_SHORT).show());
            return;
        }

        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));

        detectFaceUsingMLKit(storedFaceBitmap, new OnFaceDetectedListener() {
            @Override
            public void onFaceDetected(Bitmap storedFace) {
                detectFaceUsingMLKit(liveFaceBitmap, new OnFaceDetectedListener() {
                    @Override
                    public void onFaceDetected(Bitmap liveFace) {
                        // 여기서만 리사이즈하여 정확도 확보
                        Bitmap resizedStoredFace = resizeBitmap(storedFace, 112, 112);
                        Bitmap resizedLiveFace = resizeBitmap(liveFace, 112, 112);

                        float similarity = calculateCosineSimilarity(extractFaceEmbedding(resizedStoredFace), extractFaceEmbedding(resizedLiveFace));
                        boolean isFaceMatched = similarity >= FACE_MATCH_THRESHOLD;

                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            TextView similarityTextView = findViewById(R.id.similarityTextView);
                            similarityTextView.setText(String.format("얼굴 비교 결과: %.2f%% 일치", similarity * 100));

                            if (isFaceMatched) {
                                Toast.makeText(FaceVerificationActivity.this, "얼굴 인증 완료.", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(FaceVerificationActivity.this, OrderSummaryActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(FaceVerificationActivity.this, "얼굴 인증 실패: 얼굴이 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFaceDetectionFailed() {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(FaceVerificationActivity.this, "실시간 얼굴 감지에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onFaceDetectionFailed() {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(FaceVerificationActivity.this, "저장된 얼굴 감지에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }



    private void detectFaceUsingMLKit(Bitmap bitmap, OnFaceDetectedListener listener) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() > 0) {
                        Face face = faces.get(0);

                        int left = Math.max((int) face.getBoundingBox().left, 0);
                        int top = Math.max((int) face.getBoundingBox().top, 0);

                        int width = Math.min((int) face.getBoundingBox().width(), bitmap.getWidth() - left);
                        int height = Math.min((int) (face.getBoundingBox().height()), bitmap.getHeight() - top);

                        Bitmap croppedFace = Bitmap.createBitmap(bitmap, left, top, width, height);

                        // 리사이즈는 여기서 하지 않고 고화질로 유지
                        listener.onFaceDetected(croppedFace);
                    } else {
                        listener.onFaceDetectionFailed();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FaceVerificationActivity", "ML Kit face detection failed", e);
                    listener.onFaceDetectionFailed();
                });
    }


    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    interface OnFaceDetectedListener {
        void onFaceDetected(Bitmap faceBitmap);
        void onFaceDetectionFailed();
    }

    private float[] extractFaceEmbedding(Bitmap bitmap) {
        // Bitmap을 112x112 크기로 리사이즈
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 112, 112, true);

        // Bitmap을 ByteBuffer로 변환
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[112 * 112];
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < 112; ++i) {
            for (int j = 0; j < 112; ++j) {
                final int val = intValues[pixel++];
                inputBuffer.putFloat(((val >> 16) & 0xFF) / 127.5f - 1.0f);
                inputBuffer.putFloat(((val >> 8) & 0xFF) / 127.5f - 1.0f);
                inputBuffer.putFloat((val & 0xFF) / 127.5f - 1.0f);
            }
        }

        // TFLite 모델을 사용하여 특징 벡터 추출
        float[][] embedding = new float[1][192];  // 모델의 출력 크기에 맞게 수정
        tflite.run(inputBuffer, embedding);

        return embedding[0];
    }

    private float calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (liveFaceBitmap != null) {
            liveFaceBitmap.recycle();
            liveFaceBitmap = null;
        }
        if (storedFaceBitmap != null) {
            storedFaceBitmap.recycle();
            storedFaceBitmap = null;
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        cameraExecutor.shutdown();
    }

}

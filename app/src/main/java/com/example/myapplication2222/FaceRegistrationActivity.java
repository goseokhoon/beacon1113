package com.example.myapplication2222;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import androidx.core.content.FileProvider;
import androidx.lifecycle.LifecycleOwner;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.graphics.Matrix; // 추가
public class FaceRegistrationActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 2001;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT; // 현재 카메라 방향
    private ProcessCameraProvider cameraProvider; // ProcessCameraProvider 변수 선언
    private boolean isTakingPhoto = false; // 중복 촬영 방지 변수

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FirebaseApp.initializeApp(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_registration);
        Button captureButton = findViewById(R.id.captureButton);
        Button switchCameraButton = findViewById(R.id.switchCameraButton);
        previewView = findViewById(R.id.previewView);

        // 카메라 실행기 초기화
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 카메라 권한 확인
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }

        // 버튼 리스너 설정
        captureButton.setOnClickListener(v -> takePhoto());
        switchCameraButton.setOnClickListener(v -> switchCamera());
    }

    // 모든 권한이 부여되었는지 확인
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraProvider != null) {
            startCamera(); // 카메라 시작
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll(); // 모든 사용 사례 해제
        }
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(this).get(); // cameraProvider 필드에 할당
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("FaceRegistrationActivity", "카메라 초기화 실패", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        cameraProvider.unbindAll(); // 이전 바인딩 해제
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        Log.d("FaceRegistrationActivity", "카메라 프리뷰 바인딩 완료");
    }

    private void takePhoto() {
        if (imageCapture == null || isTakingPhoto) return; // 촬영 중이면 추가 촬영 금지

        // 현재 로그인한 사용자 가져오기
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e("FaceRegistrationActivity", "사용자가 인증되지 않았습니다.");
            Toast.makeText(this, "사용자가 인증되지 않았습니다.", Toast.LENGTH_SHORT).show();
            return; // 사용자 인증이 되지 않았다면 사진을 찍지 않음
        } else {
            Log.d("FaceRegistrationActivity", "현재 인증된 사용자: " + user.getEmail());
        }

        isTakingPhoto = true; // 촬영 시작
        String userId = user.getUid(); // 현재 로그인한 사용자의 UID
        File photoFile = new File(getExternalFilesDir(null), userId + "_face.jpg"); // 파일 이름에 userId 추가

        // 기존 파일이 존재하는 경우 삭제
        if (photoFile.exists()) {
            photoFile.delete();
            Log.d("FaceRegistrationActivity", "기존 파일 삭제: " + photoFile.getAbsolutePath());
        }

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        Log.e("FaceRegistrationActivity", "사진 캡처 시작");
        imageCapture.takePicture(outputOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.e("FaceRegistrationActivity", "사진 캡처 성공: " + photoFile.getAbsolutePath());
                        long fileSize = photoFile.length(); // 파일 크기 (바이트 단위)
                        Log.d("FaceRegistrationActivity", "이미지 파일 크기: " + fileSize + " 바이트");

                        runOnUiThread(() -> {
                            previewView.setVisibility(View.GONE); // 카메라 프리뷰 숨기기
                            Toast.makeText(FaceRegistrationActivity.this, "업로드 중입니다...", Toast.LENGTH_SHORT).show();
                        });

                        saveImageToFirebase(photoFile); // Firebase에 이미지 저장
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> {
                            Toast.makeText(FaceRegistrationActivity.this, "사진 캡처 실패", Toast.LENGTH_SHORT).show();
                            isTakingPhoto = false; // 촬영 종료
                        });
                        Log.e("FaceRegistrationActivity", "사진 캡처 실패", exception);
                    }
                });
    }

    private void saveImageToFirebase(File imageFile) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "사용자가 인증되지 않았습니다.", Toast.LENGTH_SHORT).show();
                isTakingPhoto = false;
            });
            return;
        }

        String userId = user.getUid();
        StorageReference imageRef = storage.getReference()
                .child("faces")
                .child(userId + "_face.jpg");

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
            options.inSampleSize = calculateInSampleSize(options, 640, 480);
            options.inJustDecodeBounds = false;

            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

            // 여기서 90도 오른쪽으로 회전
            Bitmap rotatedBitmap = rotateBitmap(bitmap, 270);

            File rotatedImageFile = new File(getExternalFilesDir(null), userId + "_rotated_face.jpg");
            FileOutputStream out = new FileOutputStream(rotatedImageFile);
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            out.flush();
            out.close();

            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    rotatedImageFile
            );

            UploadTask uploadTask = imageRef.putFile(fileUri);
            uploadTask
                    .addOnProgressListener(taskSnapshot -> {
                        double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                        Log.d("FaceRegistrationActivity", "업로드 진행률: " + progress + "%");
                    })
                    .addOnSuccessListener(taskSnapshot -> {
                        runOnUiThread(() -> {
                            Toast.makeText(FaceRegistrationActivity.this, "이미지 업로드 성공", Toast.LENGTH_SHORT).show();
                            rotatedImageFile.delete(); // 업로드 후 회전된 이미지 파일 삭제

                            Intent intent = new Intent(FaceRegistrationActivity.this, ProfileManagementActivity.class);
                            startActivity(intent);
                            finish();
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e("FaceRegistrationActivity", "이미지 업로드 실패", e);
                        runOnUiThread(() -> {
                            Toast.makeText(FaceRegistrationActivity.this, "이미지 업로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            isTakingPhoto = false;
                        });
                    })
                    .addOnCompleteListener(task -> {
                        if (cameraProvider != null) {
                            cameraProvider.unbindAll();
                        }
                    });
        } catch (Exception e) {
            Log.e("FaceRegistrationActivity", "파일 처리 중 오류", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "파일 처리 중 오류 발생", Toast.LENGTH_SHORT).show();
                isTakingPhoto = false;
            });
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }




    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 원본 이미지 크기
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // 카메라 전환 메서드
    private void switchCamera() {
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        startCamera(); // 카메라 다시 시작
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll(); // 모든 사용 사례 해제
        }
        cameraExecutor.shutdown(); // Executor 서비스 종료
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}

package com.example.myapplication2222;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartActivity extends AppCompatActivity implements KartriderAdapter.OnProductClickListener {

    private static final int REQUEST_CODE_OCR = 1; // OcrActivity 요청 코드
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_IS_ADULT = "is_adult";

    private RecyclerView recyclerView;
    private KartriderAdapter productAdapter;
    private List<Kartrider> productList;
    private FirebaseFirestore db;
    private Context context;
    private TextView totalPriceTextView;
    private Map<String, Boolean> restrictedProducts = new HashMap<>();
    private boolean isDialogShowing = false; // 다이얼로그 표시 상태
    private boolean dataLoaded = false; // 데이터 로드 상태
    private ListenerRegistration listenerRegistration;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        context = this;

        // Firestore 객체 초기화
        db = FirebaseFirestore.getInstance();

        // RecyclerView 초기화
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        productList = new ArrayList<>();

        // ProductAdapter 초기화
        productAdapter = new KartriderAdapter(productList, this, this, false);
        recyclerView.setAdapter(productAdapter);

        // 총 결제액 TextView 초기화
        totalPriceTextView = findViewById(R.id.total_amount);

        // Firestore에서 상품 데이터와 미성년자 구매 불가 품목 데이터 가져오기
        fetchRestrictedProducts();

        // '결제' 버튼 설정
        Button payButton = findViewById(R.id.pay_button);
        payButton.setOnClickListener(v -> handlePayment());
    }


    private boolean containsRestrictedProducts() {
        for (Kartrider product : productList) {
            if (restrictedProducts.containsKey(product.getId()) && restrictedProducts.get(product.getId())) {
                return true;
            }
        }
        return false;
    }
    @Override
    protected void onStart() {
        super.onStart();
        setupFirestoreListener();
    }
    @Override
    protected void onStop() {
        super.onStop();
        // Activity가 중지될 때 리스너를 제거하여 리소스를 절약
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
    private void fetchRestrictedProducts() {
        db.collection("inventory")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        restrictedProducts.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Object allowObject = document.get("allow");
                            if (allowObject instanceof String) {
                                String allow = (String) allowObject;
                                restrictedProducts.put(document.getId(), "No".equalsIgnoreCase(allow));
                            } else {
                                Log.w("CartActivity", "Field 'allow' is not a String or is missing");
                                restrictedProducts.put(document.getId(), false);
                            }
                        }
                        checkDataLoaded(); // 제약 데이터 로드 후 확인
                    } else {
                        Log.e("CartActivity", "Error fetching restricted products: " + task.getException());
                    }
                });
    }



    private void checkDataLoaded() {
        if (!productList.isEmpty() && !restrictedProducts.isEmpty()) {
            dataLoaded = true;
            Log.d("CartActivity", "Data fully loaded.");
        }
    }


    private void setupFirestoreListener() {
        listenerRegistration = db.collection("kartrider")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.w("CartActivity", "Listen failed.", e);
                        return;
                    }
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        for (DocumentChange dc : queryDocumentSnapshots.getDocumentChanges()) {
                            Kartrider updatedProduct = dc.getDocument().toObject(Kartrider.class);
                            updatedProduct.setId(dc.getDocument().getId());
                            switch (dc.getType()) {
                                case ADDED:
                                    addProductToList(updatedProduct);
                                    break;
                                case MODIFIED:
                                    updateProductInList(updatedProduct);
                                    break;
                                case REMOVED:
                                    removeProductFromList(updatedProduct.getId());
                                    break;
                            }
                        }
                        updateTotalPrice();
                        checkDataLoaded(); // 데이터를 불러온 후 상태를 확인
                    } else {
                        Log.d("CartActivity", "No documents found in snapshot.");
                    }
                });
    }




    private void addProductToList(Kartrider product) {
        if (product != null && product.getId() != null) {
            if (findProductIndexById(product.getId()) == -1) {
                productList.add(product);
                productAdapter.notifyItemInserted(productList.size() - 1);
                Log.d("CartActivity", "Added product to list: " + product.toString());
            }
        } else {
            Log.e("CartActivity", "Product or Product ID is null. Cannot add to list.");
        }
    }


    private void updateProductInList(Kartrider product) {
        if (product != null && product.getId() != null) {
            int index = findProductIndexById(product.getId());
            if (index != -1) {
                productList.set(index, product);
                productAdapter.notifyItemChanged(index);
                Log.d("CartActivity", "Updated product in list: " + product.toString());
            }
        } else {
            Log.e("CartActivity", "Product or Product ID is null. Cannot update list.");
        }
    }

    private void removeProductFromList(String productId) {
        if (productId != null) {
            int index = findProductIndexById(productId);
            if (index != -1) {
                Kartrider removedProduct = productList.remove(index);
                productAdapter.notifyItemRemoved(index);
                Log.d("CartActivity", "Removed product from list: " + removedProduct.toString());
            }
        } else {
            Log.e("CartActivity", "Product ID is null. Cannot remove from list.");
        }
    }

    private int findProductIndexById(String id) {
        if (id != null) {
            for (int i = 0; i < productList.size(); i++) {
                if (id.equals(productList.get(i).getId())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void updateTotalPrice() {
        int totalPrice = 0;
        for (Kartrider product : productList) {
            if (product != null) {
                // 수량을 고려하여 총 금액 계산
                totalPrice += product.getPrice() * product.getQuantity(); // quantity 필드가 필요합니다.
            }
        }
        totalPriceTextView.setText("총 결제금액: " + totalPrice + "원");
    }

    @Override
    public void onProductDeleteClick(int position) {
        if (position >= 0 && position < productList.size()) {
            Kartrider productToRemove = productList.get(position);
            String productId = productToRemove.getId();

            // Firestore에서 삭제
            db.collection("kartrider").document(productId).delete()
                    .addOnSuccessListener(aVoid -> {
                        // 삭제 성공 후 리스트에서 상품 제거
                        productList.remove(position);
                        productAdapter.notifyItemRemoved(position); // 어댑터에 삭제 알림

                        // 총 결제 금액 업데이트
                        updateTotalPrice();

                        // 장바구니가 비어 있는지 확인
                        if (productList.isEmpty()) {
                            // UI 업데이트 (예: 비어있음 메시지 표시)
                            Toast.makeText(this, "장바구니가 비어 있습니다.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "상품 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Log.e("CartActivity", "Invalid position for deletion: " + position);
        }
    }
    private void loadProductList() {
        db.collection("kartrider")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        productList.clear(); // 기존 목록 비우기
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Kartrider product = document.toObject(Kartrider.class);
                            productList.add(product);
                        }
                        productAdapter.notifyDataSetChanged(); // 어댑터에 데이터 변경 알림
                    } else {
                        Log.e("CartActivity", "Error getting documents: ", task.getException());
                    }
                });
    }

    @Override
    public void onProductQuantityChanged() {
        updateTotalPrice(); // 총 가격 업데이트 호출 추가
    }

    private void handlePayment() {
        if (!dataLoaded) {
            Toast.makeText(context, "데이터가 아직 로드되지 않았습니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            Log.d("CartActivity", "Data not loaded yet. dataLoaded = " + dataLoaded);
            return;
        }

        // SharedPreferences에서 성인 인증 상태 로드
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isAdult = prefs.getBoolean(KEY_IS_ADULT, false);

        Log.d("CartActivity", "containsRestrictedProducts: " + containsRestrictedProducts());
        Log.d("CartActivity", "isAdult: " + isAdult);

        if (containsRestrictedProducts()) {
            if (!isAdult) {
                // 미성년자 구매 불가 품목이 있는 경우 성인 인증을 요구
                showAgeVerificationDialog();
            } else {
                // 성인 인증이 완료된 경우 결제 처리
                navigateToOrderSummary();
                resetAdultVerification(); // 결제 후 성인 인증 정보 초기화
            }
        } else {
            // 미성년자 구매 불가 품목이 없는 경우 결제 처리
            navigateToOrderSummary();
            resetAdultVerification(); // 결제 후 성인 인증 정보 초기화
        }
    }


    private void showAgeVerificationDialog() {
        isDialogShowing = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_age_verification, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();

        Button confirmButton = dialogView.findViewById(R.id.confirm_button);
        confirmButton.setOnClickListener(v -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                StorageReference faceImageRef = FirebaseStorage.getInstance()
                        .getReference()
                        .child("faces/" + currentUser.getUid() + "_face.jpg");

                faceImageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    // 얼굴 이미지가 존재하는 경우 FaceVerificationActivity로 이동
                    Intent intent = new Intent(CartActivity.this, FaceVerificationActivity.class);
                    startActivityForResult(intent, REQUEST_CODE_OCR);
                    dialog.dismiss();
                }).addOnFailureListener(e -> {
                    // 얼굴 이미지가 존재하지 않으면 등록 요청 메시지를 표시하고 메인으로 이동
                    Toast.makeText(this, "회원 정보 관리에서 성인 인증을 해주세요.", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(CartActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                    dialog.dismiss();
                });
            } else {
                Toast.makeText(this, "사용자가 로그인되어 있지 않습니다.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
    }




    private void resetAdultVerification() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_ADULT, false);
        editor.apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OCR) {
            if (resultCode == RESULT_OK) {
                boolean isAdult = data.getBooleanExtra("IS_ADULT", false);
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(KEY_IS_ADULT, isAdult);
                editor.apply();

                if (isAdult) {
                    // 성인 인증이 완료되었으면 인증 완료 메시지 표시
                    Toast.makeText(this, "성인 인증이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "성인 인증에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }
            // 다이얼로그가 이미 닫혔으므로 상태를 초기화합니다.
            isDialogShowing = false;
        }
    }

    private void navigateToOrderSummary() {
        Intent intent = new Intent(CartActivity.this, OrderSummaryActivity.class);
        intent.putParcelableArrayListExtra("PRODUCT_LIST", new ArrayList<>(productList));
        int totalPrice = 0;
        for (Kartrider product : productList) {
            totalPrice += product.getPrice(); // Assuming Kartrider has a getPrice() method
        }
        intent.putExtra("TOTAL_PRICE", totalPrice);
        intent.putExtra("TOTAL_QUANTITY", productList.size());
        startActivity(intent);
        finish(); // 현재 Activity 종료
    }

}
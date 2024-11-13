package com.example.myapplication2222;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RecommendationActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private TextView recommendationText;
    private Button closeButton;
    private List<Map<String, Object>> items = new ArrayList<>();
    private static final String INVENTORY_COLLECTION = "inventory";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommendation);

        recommendationText = findViewById(R.id.recommendation_text);
        closeButton = findViewById(R.id.close_button);
        db = FirebaseFirestore.getInstance();

        // 비콘 ID에 따른 추천 카테고리 설정
        int beaconId = getIntent().getIntExtra("beaconId", -1);
        String category = getCategoryFromBeaconId(beaconId);

        if (category != null) {
            loadItemsFromFirebase(category);
        } else {
            recommendationText.setText("추천할 항목이 없습니다.");
        }

        // 닫기 버튼 클릭 리스너 설정
        closeButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putExtra("beaconId", getIntent().getIntExtra("beaconId", -1));  // 현재 코너 beaconId 전달
            setResult(RESULT_OK, intent);  // 결과 전달
            finish();  // 액티비티 종료
        });

    }

    private String getCategoryFromBeaconId(int beaconId) {
        switch (beaconId) {
            case 1:
                return "과자";
            case 2:
                return "라면";
            case 3:
                return "음료";
            default:
                return null;
        }
    }

    private void loadItemsFromFirebase(String category) {
        db.collection(INVENTORY_COLLECTION)
                .whereEqualTo("category", category) // 카테고리를 통해 문서 필터링
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            for (DocumentSnapshot document : querySnapshot) {
                                items.add(document.getData());
                            }
                            showRandomRecommendation();
                        } else {
                            recommendationText.setText("해당 카테고리의 추천할 항목이 없습니다.");
                        }
                    } else {
                        Toast.makeText(this, "데이터 로드 실패: " + task.getException(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showRandomRecommendation() {
        if (!items.isEmpty()) {
            Random random = new Random();
            Map<String, Object> randomItem = items.get(random.nextInt(items.size()));

            String name = (String) randomItem.get("name");
            Long price = (Long) randomItem.get("price");
            Long stock = (Long) randomItem.get("stock");

            TextView nameText = findViewById(R.id.recommendation_name);
            TextView priceText = findViewById(R.id.recommendation_price);
            TextView stockText = findViewById(R.id.recommendation_stock);

            nameText.setText(name != null ? name : "정보 없음");
            priceText.setText(price != null ? price + "원" : "정보 없음");
            stockText.setText(stock != null ? stock + "개 남음" : "재고 없음");
        } else {
            recommendationText.setText("추천할 항목이 없습니다.");
        }
    }

}

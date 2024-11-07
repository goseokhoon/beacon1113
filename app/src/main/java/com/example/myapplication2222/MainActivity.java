package com.example.myapplication2222;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private Button loginButton;
    private Button mapButton;
    private Button cartButton;
    private Button stockButton;
    private Button manageProfileButton; // 회원정보 관리 버튼 추가
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_APP_WAS_CLOSED = "app_was_closed";
    private static final String KEY_IS_ADULT = "is_adult";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // FirebaseAuth 인스턴스 초기화
        mAuth = FirebaseAuth.getInstance();

        FirebaseApp.initializeApp(this);

        // 앱이 완전히 종료되었다면 로그인 정보와 성인 인증 상태 초기화
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean wasClosed = prefs.getBoolean(KEY_APP_WAS_CLOSED, true);
        if (wasClosed) {
            clearUserSession();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_APP_WAS_CLOSED, false);
            editor.apply();
        }

        // 버튼 인스턴스를 가져옵니다.
        mapButton = findViewById(R.id.map_button);
        cartButton = findViewById(R.id.cart_button);
        stockButton = findViewById(R.id.stock_button);
        loginButton = findViewById(R.id.login_button); // 로그인 버튼 추가
        manageProfileButton = findViewById(R.id.manage_profile_button); // 회원정보 관리 버튼 추가

        // 로그인 버튼 클릭 리스너 설정
        loginButton.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() != null) {
                logoutUser(); // 로그아웃 시도
            } else {
                // LoginActivity로 이동하는 Intent를 생성합니다.
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent); // 액티비티 전환
            }
        });

        // 지도 버튼에 클릭 리스너를 설정합니다.
        mapButton.setOnClickListener(v -> {
            // MapActivity로 이동하는 Intent를 생성합니다.
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent); // 액티비티 전환
        });

        // 장바구니 버튼에 클릭 리스너를 설정합니다.
        cartButton.setOnClickListener(v -> {
            // 장바구니 화면으로 이동하기 전에 로그인 여부 확인
            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(MainActivity.this, "로그인 후 이용해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            // 장바구니 화면으로 이동하는 Intent를 생성합니다.
            Intent intent = new Intent(MainActivity.this, CartActivity.class);
            startActivity(intent); // 액티비티 전환
        });

        // "재고 조회" 버튼 클릭 리스너 설정
        stockButton.setOnClickListener(v -> {
            // InventoryActivity로 이동하는 Intent를 생성합니다.
            Intent intent = new Intent(MainActivity.this, InventoryActivity.class);
            startActivity(intent);
        });

        // "회원정보 관리" 버튼 클릭 리스너 설정
        manageProfileButton.setOnClickListener(v -> {
            // ProfileManagementActivity로 이동하는 Intent를 생성합니다.
            Intent intent = new Intent(MainActivity.this, ProfileManagementActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 현재 사용자의 로그인 상태를 확인하고 UI 업데이트
        updateUI(mAuth.getCurrentUser() != null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 앱이 완전히 종료되었다고 표시하기 위한 상태 변경
        if (isFinishing()) {
            markAppAsClosed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 앱이 완전히 종료될 때 상태 변경
        markAppAsClosed();
    }

    private void markAppAsClosed() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_APP_WAS_CLOSED, true);
        editor.apply();
    }

    private void updateUI(boolean isLoggedIn) {
        if (isLoggedIn) {
            loginButton.setText("로그아웃"); // 로그인 버튼 텍스트를 로그아웃으로 변경
        } else {
            loginButton.setText("로그인"); // 로그인 버튼 텍스트를 로그인으로 변경
        }
    }

    private void logoutUser() {
        clearUserSession(); // 사용자 세션 초기화
        Toast.makeText(MainActivity.this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();
        updateUI(false); // 로그아웃 후 UI 업데이트
    }

    private void clearUserSession() {
        if (mAuth.getCurrentUser() != null) {
            mAuth.signOut(); // 사용자 로그아웃
        }

        // 성인 인증 상태 초기화
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_ADULT, false);
        editor.apply();
    }
}

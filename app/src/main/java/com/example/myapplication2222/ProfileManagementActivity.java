package com.example.myapplication2222;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileManagementActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText editTextName;
    private EditText editTextCurrentPassword;
    private EditText editTextNewPassword;
    private EditText editTextConfirmNewPassword;
    private EditText editTextIssueDate;
    private TextView textViewSSN;
    private TextView textViewCurrentIssueDate;
    private Button saveButton;
    private Button deleteAccountButton;
    private Button adultVerificationButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_management);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 로그인한 사용자 확인
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인 후 접근할 수 있습니다.", Toast.LENGTH_SHORT).show();
            finish(); // 현재 Activity 종료
            return;
        }

        editTextName = findViewById(R.id.editTextName);
        editTextCurrentPassword = findViewById(R.id.editTextCurrentPassword);
        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmNewPassword = findViewById(R.id.editTextConfirmNewPassword);
        editTextIssueDate = findViewById(R.id.editTextIssueDate);
        textViewSSN = findViewById(R.id.textViewSSN);
        textViewCurrentIssueDate = findViewById(R.id.textViewCurrentIssueDate);
        saveButton = findViewById(R.id.save_button);
        deleteAccountButton = findViewById(R.id.delete_account_button);
        adultVerificationButton = findViewById(R.id.adultVerificationButton);

        editTextIssueDate.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;

                isFormatting = true;
                String input = s.toString().replaceAll("[^\\d]", ""); // 숫자만 남기기
                String formatted = formatAsDate(input);
                s.replace(0, s.length(), formatted);
                isFormatting = false;
            }

            private String formatAsDate(String input) {
                if (input.length() >= 5 && input.length() <= 6) {
                    return input.substring(0, 4) + "." + input.substring(4);
                } else if (input.length() > 6) {
                    return input.substring(0, 4) + "." + input.substring(4, 6) + "." + input.substring(6);
                }
                return input;
            }
        });

        loadUserProfile(currentUser); // 현재 로그인한 사용자 프로필 로드
        saveButton.setOnClickListener(v -> saveUserProfile());
        deleteAccountButton.setOnClickListener(v -> deleteUserAccount());

        adultVerificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileManagementActivity.this, OcrActivity.class);
            startActivity(intent);
        });
    }

    private void loadUserProfile(FirebaseUser currentUser) {
        SharedPreferences preferences = getSharedPreferences("UserProfile", MODE_PRIVATE);
        String name = preferences.getString("name", null);
        String ssn = preferences.getString("ssn", null);
        String issueDate = preferences.getString("issueDate", null);

        if (name == null) {
            // Firestore에서 데이터 가져오기
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // 로컬 저장소에 저장
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("name", documentSnapshot.getString("name"));
                            editor.putString("ssn", documentSnapshot.getString("ssn"));
                            editor.putString("issueDate", documentSnapshot.getString("issueDate"));
                            editor.apply();

                            // UI 업데이트
                            editTextName.setText(documentSnapshot.getString("name"));
                            textViewSSN.setText("주민등록번호: " + documentSnapshot.getString("ssn"));
                            String fetchedIssueDate = documentSnapshot.getString("issueDate");
                            textViewCurrentIssueDate.setText("현재 발급일자: " + fetchedIssueDate);
                            editTextIssueDate.setText(fetchedIssueDate);
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "프로필 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show());
        } else {
            // 로컬 저장소에서 데이터 설정
            editTextName.setText(name);
            textViewSSN.setText("주민등록번호: " + ssn);
            textViewCurrentIssueDate.setText("현재 발급일자: " + issueDate);
            editTextIssueDate.setText(issueDate);
        }
    }

    private void saveUserProfile() {
        final String name = editTextName.getText().toString().trim();
        final String currentPassword = editTextCurrentPassword.getText().toString().trim();
        final String newPassword = editTextNewPassword.getText().toString().trim();
        final String confirmNewPassword = editTextConfirmNewPassword.getText().toString().trim();
        String issueDateInput = editTextIssueDate.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!TextUtils.isEmpty(newPassword) && !newPassword.equals(confirmNewPassword)) {
            Toast.makeText(this, "새 비밀번호가 서로 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
        final String formattedIssueDate;
        try {
            Date date = dateFormat.parse(issueDateInput);
            formattedIssueDate = dateFormat.format(date);
        } catch (ParseException e) {
            Toast.makeText(this, "발급일자를 yyyy.MM.dd 형식으로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            if (!TextUtils.isEmpty(currentPassword) && !TextUtils.isEmpty(newPassword)) {
                AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);
                currentUser.reauthenticate(credential).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updatePasswordAndProfile(currentUser, newPassword, name, formattedIssueDate);
                    } else {
                        Toast.makeText(this, "현재 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                updateProfileWithoutPassword(currentUser, name, formattedIssueDate);
            }
        }
    }

    private void updatePasswordAndProfile(FirebaseUser currentUser, String newPassword, String name, String issueDate) {
        currentUser.updatePassword(newPassword).addOnCompleteListener(passwordTask -> {
            if (passwordTask.isSuccessful()) {
                Map<String, Object> userUpdates = new HashMap<>();
                userUpdates.put("name", name);
                userUpdates.put("issueDate", issueDate);

                db.collection("users").document(currentUser.getUid())
                        .update(userUpdates)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "프로필이 업데이트되었습니다.", Toast.LENGTH_SHORT).show();
                            navigateToMainActivity();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "프로필 업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show());
            } else {
                Toast.makeText(this, "비밀번호 변경에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateProfileWithoutPassword(FirebaseUser currentUser, String name, String issueDate) {
        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("name", name);
        userUpdates.put("issueDate", issueDate);

        db.collection("users").document(currentUser.getUid())
                .update(userUpdates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "프로필이 업데이트되었습니다.", Toast.LENGTH_SHORT).show();
                    navigateToMainActivity();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "프로필 업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(ProfileManagementActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void deleteUserAccount() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).delete()
                    .addOnSuccessListener(aVoid -> {
                        currentUser.delete().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, "계정이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(this, "계정 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "사용자 데이터 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show());
        }
    }
}

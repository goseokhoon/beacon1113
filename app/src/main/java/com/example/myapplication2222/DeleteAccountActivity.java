package com.example.myapplication2222;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class DeleteAccountActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText editTextPassword;
    private Button confirmDeleteButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_account);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editTextPassword = findViewById(R.id.editTextDeletePassword);
        confirmDeleteButton = findViewById(R.id.confirmDeleteButton);

        confirmDeleteButton.setOnClickListener(v -> deleteUserAccount());
    }

    private void deleteUserAccount() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            Toast.makeText(this, "사용자가 인증되지 않았습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentPassword = editTextPassword.getText().toString().trim();
        if (TextUtils.isEmpty(currentPassword)) {
            Toast.makeText(this, "비밀번호를 입력해야 계정 삭제가 가능합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);

        currentUser.reauthenticate(credential)
                .addOnCompleteListener(reAuthTask -> {
                    if (reAuthTask.isSuccessful()) {
                        db.collection("users").document(currentUser.getUid()).delete()
                                .addOnSuccessListener(aVoid -> {
                                    currentUser.delete().addOnCompleteListener(deleteTask -> {
                                        if (deleteTask.isSuccessful()) {
                                            Toast.makeText(this, "계정이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                                            navigateToLoginScreen();
                                        } else {
                                            Toast.makeText(this, "계정 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "사용자 데이터 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show());
                    } else {
                        Toast.makeText(this, "비밀번호가 올바르지 않습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToLoginScreen() {
        Intent intent = new Intent(DeleteAccountActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

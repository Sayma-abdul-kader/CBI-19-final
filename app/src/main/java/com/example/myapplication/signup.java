package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class signup extends AppCompatActivity {

    private EditText usernameInput, emailInput, contactInput, passwordInput;
    private Button signupButton, googleButton, loginButton;
    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;
    private GoogleSignInClient googleSignInClient;
    private static final int RC_SIGN_IN = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize Firebase Auth and Database
        firebaseAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");

        // Initialize UI elements
        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        contactInput = findViewById(R.id.contactInput);
        passwordInput = findViewById(R.id.passwordInput);
        signupButton = findViewById(R.id.signupButton);
        googleButton = findViewById(R.id.googleButton);
        loginButton = findViewById(R.id.loginButton);

        // Sign-up button click listener
        signupButton.setOnClickListener(v -> registerUser());

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // Replace with actual client ID
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Google Sign-In button click listener
        googleButton.setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

        // Login button click listener
        loginButton.setOnClickListener(v -> {
            Intent intent = new Intent(signup.this, login.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            try {
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account);
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        Toast.makeText(signup.this, "Welcome, " + (user != null ? user.getDisplayName() : "User"), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(signup.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(signup.this, "Google Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void registerUser() {
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String contact = contactInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (validateInputs(username, email, contact, password)) {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                String userId = firebaseUser.getUid();
                                User user = new User(userId, username, email, contact);

                                databaseReference.child(userId).setValue(user).addOnCompleteListener(dbTask -> {
                                    if (dbTask.isSuccessful()) {
                                        Toast.makeText(signup.this, "Sign-up successful!", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(signup.this, MainActivity.class));
                                        finish();
                                    } else {
                                        Toast.makeText(signup.this, "Failed to save user details.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } else {
                            String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(signup.this, mapFirebaseError(errorMessage), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private boolean validateInputs(String username, String email, String contact, String password) {
        if (username.isEmpty() || email.isEmpty() || contact.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!contact.matches("\\d{10,15}")) {
            Toast.makeText(this, "Contact must be numeric and 10-15 digits long.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters long.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String mapFirebaseError(String errorMessage) {
        if (errorMessage.contains("email")) {
            return "The email address is already in use.";
        } else if (errorMessage.contains("password")) {
            return "The password is too weak.";
        } else {
            return "Sign-up failed. Please try again.";
        }
    }

    public static class User {
        public String id;
        public String username;
        public String email;
        public String contact;

        public User() {
            // Default constructor required for Firebase
        }

        public User(String id, String username, String email, String contact) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.contact = contact;
        }
    }
}

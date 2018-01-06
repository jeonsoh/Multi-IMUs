package nisargpatel.deadreckoning.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import nisargpatel.deadreckoning.R;

public class AuthActivity extends Activity implements GoogleApiClient.OnConnectionFailedListener {//파이어베이스 연동을 위한 구글계정 로그인 액비티비티
    private SignInButton mSignButton;
    private GoogleApiClient mGoogleApiClient;
    private FirebaseAuth mFirebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activty_auth);
        mSignButton = (SignInButton) findViewById(R.id.sign_btn);
        mSignButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {//해당 버튼 클릭시 구글 로그인창 연동
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                startActivityForResult(signInIntent, 100);
            }
        });

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                //.enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        // [START initialize_auth]
        mFirebaseAuth = FirebaseAuth.getInstance();// Auth를 통해 자동로그인 구현도 가능

    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {//구글로 연동된 페이지로부터 받은 응답코드로 판단
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == 100) {//100 이라는 신호를 받는다면
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {//로그인 성공시
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {//로그인 실패시
                Toast.makeText(AuthActivity.this," 구글로그인 실패입니다. ", Toast.LENGTH_SHORT).show();
                // Google Sign In failed, update UI appropriately
                // ...
            }
        }
    }
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {//로그인 성공시 호출되는 메소드
        //Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());
        // [START_EXCLUDE silent]
        //showProgressDialog();
        // [END_EXCLUDE]

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);//자격 확인.
        Task<AuthResult> authResultTask = mFirebaseAuth.signInWithCredential(credential);
        authResultTask.addOnSuccessListener(new OnSuccessListener<AuthResult>() {
            @Override
            public void onSuccess(AuthResult authResult) {//유저 자격 확인이 성공되었다면, UserActivity로 이동
                FirebaseUser firebaseUser = authResult.getUser();
                //Toast.makeText(AuthActivity.this,"Email: "+firebaseUser.getEmail(),Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(AuthActivity.this,UserListActivity.class);
                startActivity(intent);
                finish();
            }
        });
        authResultTask.addOnFailureListener(new OnFailureListener() {

            @Override
            public void onFailure(@NonNull Exception e) {//유저 자격 확인이 실패되었다면,
                Toast.makeText(AuthActivity.this," firebase 허용 안됩니다. ", Toast.LENGTH_SHORT).show();
            }
        });
        /*mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            //Log.d(TAG, "signInWithCredential:success");
                            Toast.makeText(AuthActivity.this, "Authentication successed.",
                                    Toast.LENGTH_SHORT).show();
                            FirebaseUser user = mAuth.getCurrentUser();
                            //updateUI(user);
                        } else {
                            // If sign in fails, display a message to the user.
                            //Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(AuthActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                            //updateUI(null);
                        }

                        // [START_EXCLUDE]
                        //hideProgressDialog();
                        // [END_EXCLUDE]
                    }
                });*/
    }
}

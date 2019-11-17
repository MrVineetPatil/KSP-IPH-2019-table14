package com.project.coders.ksp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executor;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private String mToBeSignedMessage;
    private static final String TAG = MapsActivity.class.getSimpleName();
    private HashMap<String, Marker> mMarkers = new HashMap<>();
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String KEY_NAME = UUID.randomUUID().toString();
    private LocationManager lm;
    private int flagvalue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        flagvalue = 0;
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        Button btn = findViewById(R.id.submit);
       // Button btns = findViewById(R.id.startstop);
       /* btns.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Button bt = findViewById(R.id.startstop);
                String strst1 = bt.getText().toString();
                if(strst1.equalsIgnoreCase("Start")){
                    startService(new Intent(getApplicationContext(), TrackerService.class));
                    bt.setBackgroundColor(Color.BLUE);
                    bt.setText("STOP");
                }
                if(strst1.equalsIgnoreCase("Stop")){
                    stopService(new Intent(getApplicationContext(), TrackerService.class));
                    bt.setBackgroundColor(Color.RED);
                    bt.setText("START");
                }}});*/
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (canAuthenticateWithBiometrics()) {  // Check whether this device can authenticate with biometrics
                    Log.i("TAG", "Try registration");
                    // Generate keypair and init signature
                    Signature signature;
                    try {
                        KeyPair keyPair = generateKeyPair(KEY_NAME, true);
                        // Send public key part of key pair to the server, this public key will be used for authentication
                        mToBeSignedMessage = Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.URL_SAFE) +
                                ":" +
                                KEY_NAME +
                                ":" +
                                // Generated by the server to protect against replay attack
                                "12345";

                        signature = initSignature(KEY_NAME);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    // Create biometricPrompt
                    showBiometricPrompt(signature);
                } else {
                    // Cannot use biometric prompt
                    Toast.makeText(getApplicationContext(), "Cannot use biometric", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    private void showBiometricPrompt(Signature signature) {
        BiometricPrompt.AuthenticationCallback authenticationCallback = getAuthenticationCallback();
        BiometricPrompt mBiometricPrompt = new BiometricPrompt(this, getMainThreadExecutor(), authenticationCallback);

        // Set prompt info
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setDescription("Please scan your fingerprint")
                .setTitle("Verify Beat")
                .setSubtitle("")
                .setNegativeButtonText("Cancel")
                .build();

        // Show biometric prompt
        if (signature != null) {
            Log.i(TAG, "Show biometric prompt");
            mBiometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(signature));
        }
    }

    private BiometricPrompt.AuthenticationCallback getAuthenticationCallback() {
        // Callback for biometric authentication result
        return new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                Log.i(TAG, "onAuthenticationSucceeded");
                super.onAuthenticationSucceeded(result);
                if (result.getCryptoObject() != null &&
                        result.getCryptoObject().getSignature() != null) {
                    try {
                        Signature signature = result.getCryptoObject().getSignature();
                        signature.update(mToBeSignedMessage.getBytes());
                        String signatureString = Base64.encodeToString(signature.sign(), Base64.URL_SAFE);
                        int permission = ContextCompat.checkSelfPermission(getApplicationContext(),
                                Manifest.permission.ACCESS_FINE_LOCATION);
                        if (permission == PackageManager.PERMISSION_GRANTED) {
                            //Check if there is any live assignment if yes then start TrackerService
                            startService(new Intent(getApplicationContext(), TrackerService.class));
                        }
                        // Normally, ToBeSignedMessage and Signature are sent to the server and then verified
                        Log.i(TAG, "Message: " + mToBeSignedMessage);
                        Log.i(TAG, "Signature (Base64 EncodeD): " + signatureString);
                        Toast.makeText(getApplicationContext(), mToBeSignedMessage + ":" + signatureString, Toast.LENGTH_SHORT).show();
                        Log.i("FLAG","    "+flagvalue);
                        Intent in = new Intent(MapsActivity.this,Starttrack.class);
                        startActivity(in);
                       /* else if(flagvalue==1)
                        {
                            stopService(new Intent(getApplicationContext(), TrackerService.class));
                            flagvalue = 0;
                        }*/
                    } catch (SignatureException e) {
                        throw new RuntimeException();
                    }
                } else {
                    // Error
                    Toast.makeText(getApplicationContext(), "Something wrong", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        };
    }

    private KeyPair generateKeyPair(String keyName, boolean invalidatedByBiometricEnrollment) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keyName,
                KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512)
                // Require the user to authenticate with a biometric to authorize every use of the key
                .setUserAuthenticationRequired(true);

        // Generated keys will be invalidated if the biometric templates are added more to user device
        if (Build.VERSION.SDK_INT >= 24) {
            builder.setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment);
        }

        keyPairGenerator.initialize(builder.build());

        return keyPairGenerator.generateKeyPair();
    }

    @Nullable
    private KeyPair getKeyPair(String keyName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(keyName)) {
            // Get public key
            PublicKey publicKey = keyStore.getCertificate(keyName).getPublicKey();
            // Get private key
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyName, null);
            // Return a key pair
            return new KeyPair(publicKey, privateKey);
        }
        return null;
    }

    @Nullable
    private Signature initSignature(String keyName) throws Exception {
        KeyPair keyPair = getKeyPair(keyName);

        if (keyPair != null) {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keyPair.getPrivate());
            return signature;
        }
        return null;
    }

    private Executor getMainThreadExecutor() {
        return new MapsActivity.MainThreadExecutor();
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable r) {
            handler.post(r);
        }
    }

    private boolean canAuthenticateWithBiometrics() {
        // Check whether the fingerprint can be used for authentication (Android M to P)
        if (Build.VERSION.SDK_INT < 29) {
            FingerprintManagerCompat fingerprintManagerCompat = FingerprintManagerCompat.from(this);
            return fingerprintManagerCompat.hasEnrolledFingerprints() && fingerprintManagerCompat.isHardwareDetected();
        } else {    // Check biometric manager (from Android Q)
            BiometricManager biometricManager = this.getSystemService(BiometricManager.class);
            if (biometricManager != null) {
                return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
            }
            return false;
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMaxZoomPreference(26);
        LatLng rvce = new LatLng(12.922612, 77.504118);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(rvce));
        MarkBeatPoints();
        //subscribeToUpdates();
    }

    private void MarkBeatPoints() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Beat");
        ref.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                setMarkerBP(dataSnapshot);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                setMarkerBP(dataSnapshot);
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.d(TAG, "Failed to read value.", error.toException());
            }
        });
    }
    private void setMarkerBP(DataSnapshot dataSnapshot) {
        String key = dataSnapshot.getKey();
        HashMap<String, Object> value = (HashMap<String, Object>) dataSnapshot.getValue();
        double lat = Double.parseDouble(value.get("latitude").toString());
        double lng = Double.parseDouble(value.get("longitude").toString());
        LatLng location = new LatLng(lat, lng);
        if (!mMarkers.containsKey(key)) {
            mMarkers.put(key, mMap.addMarker(new MarkerOptions().title(key).position(location)));
        } else {
            mMarkers.get(key).setPosition(location);
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Marker marker : mMarkers.values()) {
            builder.include(marker.getPosition());
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 300));
    }
}

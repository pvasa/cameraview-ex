package com.priyankvasa.android.cameraviewexSample.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.priyankvasa.android.cameraviewexSample.R;
import com.priyankvasa.android.cameraviewexSample.extensions.ActivityExtensionsKt;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class CameraActivity extends AppCompatActivity {

    private String[] permissions = {Manifest.permission.CAMERA};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            getSupportFragmentManager().beginTransaction().replace(R.id.mainContainer, new CameraFragment()).commit();
        else ActivityCompat.requestPermissions(this, permissions, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            getSupportFragmentManager().beginTransaction().replace(R.id.mainContainer, new CameraFragment()).commit();
        else ActivityCompat.requestPermissions(this, permissions, 1);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        ActivityExtensionsKt.hideSystemUI(this);
    }
}
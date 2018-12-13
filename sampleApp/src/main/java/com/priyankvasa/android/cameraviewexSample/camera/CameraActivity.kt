package com.priyankvasa.android.cameraviewexSample.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import com.priyankvasa.android.cameraviewexSample.R
import com.priyankvasa.android.cameraviewexSample.extensions.hideSystemUI

class CameraActivity : AppCompatActivity() {

    private val permissions: Array<String> = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        permissions.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
                .toTypedArray()
                .also {
                    if (it.isNotEmpty()) {
                        ActivityCompat.requestPermissions(this, it, 1)
                        return
                    }
                }

        launchCameraFragment()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            permissions.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
                    .toTypedArray()
                    .also {
                        if (it.isNotEmpty()) {
                            ActivityCompat.requestPermissions(this, it, 1)
                            return
                        }
                    }
        } else launchCameraFragment()
    }

    private fun launchCameraFragment() {
        supportFragmentManager.beginTransaction().replace(R.id.mainContainer, CameraFragment()).commit()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hideSystemUI()
    }
}
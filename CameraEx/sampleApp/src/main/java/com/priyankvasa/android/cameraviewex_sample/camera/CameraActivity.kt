package com.priyankvasa.android.cameraviewex_sample.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.priyankvasa.android.cameraviewex_sample.R
import com.priyankvasa.android.cameraviewex_sample.extensions.hideSystemUi

class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        if (savedInstanceState == null) openCameraFragment()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun openCameraFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flMainContainer, CameraFragment.newInstance(), CameraFragment.TAG)
            .commit()
    }
}
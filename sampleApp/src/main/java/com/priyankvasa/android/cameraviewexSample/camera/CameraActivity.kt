package com.priyankvasa.android.cameraviewexSample.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.priyankvasa.android.cameraviewexSample.R
import com.priyankvasa.android.cameraviewexSample.extensions.hideSystemUI

class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        launchCameraFragment()
    }

    private fun launchCameraFragment() {
        supportFragmentManager.beginTransaction().replace(R.id.mainContainer, CameraFragment.newInstance).commit()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hideSystemUI()
    }
}
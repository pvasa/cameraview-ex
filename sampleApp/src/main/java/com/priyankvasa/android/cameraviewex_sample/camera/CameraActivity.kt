package com.priyankvasa.android.cameraviewex_sample.camera

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.priyankvasa.android.cameraviewex_sample.R

class CameraActivity : AppCompatActivity(), CameraInitFragment.Navigator {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        openCameraInitFragment()
    }

    private fun openCameraInitFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flMainContainer, CameraInitFragment.newInstance)
            .commit()
    }

    override fun openCameraFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.flMainContainer, CameraFragment.newInstance)
            .addToBackStack(CameraFragment::class.java.canonicalName)
            .commit()
    }
}
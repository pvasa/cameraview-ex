package com.priyankvasa.android.cameraviewex_sample.camera

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import com.priyankvasa.android.cameraviewex_sample.R

class CameraActivity : AppCompatActivity(), CameraInitFragment.Navigator {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        openCameraInitFragment()
        if (savedInstanceState != null) {
            val cameraFrag: Fragment = supportFragmentManager
                .getFragment(savedInstanceState, KEY_CAMERA_FRAGMENT)
                ?: return
            supportFragmentManager.beginTransaction()
                .add(R.id.flMainContainer, cameraFrag, KEY_CAMERA_FRAGMENT)
                .commit()
        }
    }

    private fun openCameraInitFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flMainContainer, CameraInitFragment.newInstance())
            .commit()
    }

    override fun openCameraFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.flMainContainer, CameraFragment.newInstance(), KEY_CAMERA_FRAGMENT)
            .addToBackStack(KEY_CAMERA_FRAGMENT)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        val state: Bundle = outState ?: Bundle()
        val cameraFrag: Fragment? =
            supportFragmentManager.findFragmentByTag(KEY_CAMERA_FRAGMENT)
        cameraFrag?.let { supportFragmentManager.putFragment(state, KEY_CAMERA_FRAGMENT, it) }
    }

    companion object {

        private val KEY_CAMERA_FRAGMENT: String =
            CameraFragment::class.java.run { canonicalName ?: name }
    }
}
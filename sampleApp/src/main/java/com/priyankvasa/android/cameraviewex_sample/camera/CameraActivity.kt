package com.priyankvasa.android.cameraviewex_sample.camera

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.priyankvasa.android.cameraviewex_sample.R

class CameraActivity : AppCompatActivity(), CameraNavigator {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        openCameraFragment()
        /*if (savedInstanceState != null) {
            val cameraFrag: Fragment = supportFragmentManager
                .getFragment(savedInstanceState, CameraFragment.TAG)
                ?: return
            supportFragmentManager.beginTransaction()
                .add(R.id.flMainContainer, cameraFrag, CameraFragment.TAG)
                .commit()
        }*/
    }

    override fun openCameraInitFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flMainContainer, CameraInitFragment.newInstance(), CameraInitFragment.TAG)
            .commit()
    }

    override fun openCameraFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flMainContainer, CameraFragment.newInstance(), CameraFragment.TAG)
//            .addToBackStack(CameraFragment.TAG)
            .commit()
    }

    /*override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        val state: Bundle = outState ?: Bundle()
        val cameraFrag: Fragment? =
            supportFragmentManager.findFragmentByTag(CameraFragment.TAG)
        cameraFrag?.let { supportFragmentManager.putFragment(state, CameraFragment.TAG, it) }
    }*/
}
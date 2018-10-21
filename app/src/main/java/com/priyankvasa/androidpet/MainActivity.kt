package com.priyankvasa.androidpet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.cameraview.Constants
import kotlinx.android.synthetic.main.fragment_camera.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            supportFragmentManager.beginTransaction().replace(R.id.mainContainer, CameraFragment()).commit()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            supportFragmentManager.beginTransaction().replace(R.id.mainContainer, CameraFragment()).commit()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
    }

    class CameraFragment : Fragment() {

        override fun onCreateView(
                inflater: LayoutInflater,
                container: ViewGroup?,
                savedInstanceState: Bundle?
        ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            camera.setOnClickListener {
                camera.facing = if (camera.facing == Constants.FACING_BACK) Constants.FACING_FRONT else Constants.FACING_BACK
            }
        }

        override fun onResume() {
            super.onResume()
            camera.start()
        }

        override fun onPause() {
            camera.run { if (isCameraOpened) stop() }
            super.onPause()
        }
    }
}

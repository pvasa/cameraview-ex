package com.priyankvasa.cameraviewexsample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.priyankvasa.android.cameraviewex.CameraView
import com.priyankvasa.android.cameraviewex.Modes
import kotlinx.android.synthetic.main.fragment_camera.buttonCapture
import kotlinx.android.synthetic.main.fragment_camera.camera
import kotlinx.android.synthetic.main.fragment_camera.ivPhoto

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
                camera.facing = if (camera.facing == Modes.FACING_BACK) Modes.FACING_FRONT else Modes.FACING_BACK
            }
            ivPhoto.setOnClickListener { ivPhoto.visibility = View.GONE }
            camera.addCallback(object : CameraView.Callback() {
                override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
                    super.onPictureTaken(cameraView, data)
                    ivPhoto.visibility = View.VISIBLE
                    Glide.with(this@CameraFragment)
                            .asBitmap()
                            .load(data)
                            .into(ivPhoto)
                }
            })

            buttonCapture.setOnClickListener { camera.capture() }
        }

        override fun onResume() {
            super.onResume()
            camera.run { if (!isCameraOpened) start() }
        }

        override fun onPause() {
            camera.run { if (isCameraOpened) stop() }
            super.onPause()
        }
    }
}

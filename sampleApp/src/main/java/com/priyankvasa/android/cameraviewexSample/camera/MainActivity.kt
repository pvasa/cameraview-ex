package com.priyankvasa.android.cameraviewexSample.camera

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.priyankvasa.android.cameraviewexSample.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        launch.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }
}
package com.priyankvasa.android.cameraviewex_sample.camera

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.priyankvasa.android.cameraviewex_sample.R
import kotlinx.android.synthetic.main.fragment_camera_init.*

class CameraInitFragment : Fragment() {

    interface Navigator {
        fun openCameraFragment()
    }

    private val navigator: Navigator
        get() = context as? Navigator
            ?: throw ClassCastException("Either not attached to activity or parent activity" +
                " has not implemented ${Navigator::class.java.canonicalName}")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_init, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buttonOpenCamera.setOnClickListener { navigator.openCameraFragment() }
    }

    companion object {
        val newInstance: CameraInitFragment get() = CameraInitFragment()
    }
}
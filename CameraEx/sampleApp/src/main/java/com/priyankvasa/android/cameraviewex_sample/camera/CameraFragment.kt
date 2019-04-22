package com.priyankvasa.android.cameraviewex_sample.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.priyankvasa.android.cameraviewex.AspectRatio
import com.priyankvasa.android.cameraviewex.ErrorLevel
import com.priyankvasa.android.cameraviewex.Image
import com.priyankvasa.android.cameraviewex.Modes
import com.priyankvasa.android.cameraviewex.VideoSize
import com.priyankvasa.android.cameraviewex_sample.R
import com.priyankvasa.android.cameraviewex_sample.extensions.hide
import com.priyankvasa.android.cameraviewex_sample.extensions.show
import com.priyankvasa.android.cameraviewex_sample.extensions.toast
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import kotlin.coroutines.CoroutineContext

open class CameraFragment : Fragment(), SettingsDialogFragment.ConfigListener, AspectRatiosRepository, CoroutineScope {

    private val job: Job = SupervisorJob()

    override val coroutineContext: CoroutineContext get() = job + Dispatchers.Main

    private val parentDir: String
        by lazy { "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx" }

    private val imageOutputDirectory: String by lazy { "$parentDir/images".also { File(it).mkdirs() } }

    private val videoOutputDirectory: String by lazy { "$parentDir/videos".also { File(it).mkdirs() } }

    private lateinit var videoFile: File

    private val nextImageFile: File
        get() = File(imageOutputDirectory, "image_${System.currentTimeMillis()}.jpg")

    private val nextVideoFile: File
        get() = File(videoOutputDirectory, "video_${System.currentTimeMillis()}.mp4")

    @SuppressLint("MissingPermission")
    private val imageCaptureListener: View.OnClickListener = View.OnClickListener { camera.capture() }

    @SuppressLint("MissingPermission")
    private val videoCaptureListener: View.OnClickListener = View.OnClickListener {
        if (camera.isVideoRecording) {
            camera.stopVideoRecording()
        } else {
            videoFile = nextVideoFile
            camera.startVideoRecording(videoFile) {
                videoFrameRate = 30
                // maxDuration = 4000
                videoStabilization = true
                videoSize = VideoSize.Max
            }
        }
    }

    private val textBarcodes: String by lazy { getString(R.string.text_barcodes) }

    private val decodeSuccessListener: (MutableList<FirebaseVisionBarcode>) -> Unit =
        listener@{ barcodes: MutableList<FirebaseVisionBarcode> ->
            if (barcodes.isEmpty()) {
                tvBarcodes?.text = textBarcodes
                return@listener
            }
            val barcodesStr = "$textBarcodes\n${barcodes.joinToString(
                separator = "\n",
                transform = { it.rawValue as? CharSequence ?: "" }
            )}"
            launch { tvBarcodes?.text = barcodesStr }
        }

    private val glideManager: RequestManager by lazy { Glide.with(this) }

    private val previewFrameInflater: (Bitmap) -> Unit = { bm: Bitmap ->
        launch {
            glideManager.load(bm)
                .apply(RequestOptions().apply { override(bm.width / 3, bm.height / 3) })
                .into(ivOutputPreview)
        }
    }

    private val cameraPreviewFrameHandler: CameraPreviewFrameHandler
        by lazy { CameraPreviewFrameHandler(decodeSuccessListener, previewFrameInflater) }

    override val supportedAspectRatios: Set<AspectRatio>
        get() = camera?.supportedAspectRatios ?: setOf()

    override val currentAspectRatio: AspectRatio get() = camera?.aspectRatio ?: AspectRatio.Invalid

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCamera()
        setupView()
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        updateViewState()
        checkPermissions().let { if (it.isEmpty()) camera.start() else requestPermissions(it, 1) }
    }

    private fun checkPermissions(): Array<String> {

        val context: Context = context ?: return permissions

        return permissions
            .filter { ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
    }

    override fun onPause() {
        super.onPause()
        camera.stop()
    }

    override fun onDestroyView() {
        cameraPreviewFrameHandler.release()
        camera.destroy()
        job.cancel()
        super.onDestroyView()
    }

    private fun setupCamera() {

        with(camera) {

            // Callback on main (UI) thread
            addCameraOpenedListener { Timber.i("Camera opened.") }

            // Callback on background thread
            setContinuousFrameListener(cameraPreviewFrameHandler.frameRate, cameraPreviewFrameHandler.listener)

            // Callback on main (UI) thread
            addPictureTakenListener { image: Image -> launch { saveDataToFile(image) } }

            // Callback on main (UI) thread
            addCameraErrorListener { t, errorLevel ->
                when (errorLevel) {
                    ErrorLevel.Error -> Timber.e(t)
                    ErrorLevel.Warning -> Timber.w(t)
                }
            }

            // Callback on main (UI) thread
            addVideoRecordStartedListener {
                Timber.i("Video recording started.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ivPlayPause.show()
                    ivPlayPause.isActivated = true
                }
                ivVideoCaptureButton.isActivated = true
            }

            // Callback on main (UI) thread
            addVideoRecordStoppedListener { isSuccess: Boolean ->
                ivPlayPause.hide()
                ivPlayPause.isActivated = false
                ivVideoCaptureButton.isActivated = false
                if (isSuccess) context?.toast("Video saved to ${videoFile.absolutePath}")
                else context?.toast("Failed to save video!")
            }

            // Callback on main (UI) thread
            addCameraClosedListener { Timber.i("Camera closed.") }
        }
    }

    private suspend fun saveDataToFile(image: Image): File {

        val output: File = nextImageFile.apply { createNewFile() }

        runCatching {
            withContext(Dispatchers.IO) {
                BufferedOutputStream(output.outputStream()).use { it.write(image.data) }
            }
        }
            .onFailure {
                context?.toast("Unable to save image to file.")
                Timber.e(it)
            }
            .onSuccess { context?.toast("Saved image to file ${output.absolutePath}") }

        return output
    }

    private fun setupView() {

        val context: Context = context ?: return

        ivSettings.setOnClickListener {
            val fm: FragmentManager = fragmentManager ?: return@setOnClickListener
            SettingsDialogFragment.newInstance().apply {
                setTargetFragment(this@CameraFragment, 0)
                show(fm, SettingsDialogFragment.TAG)
            }
        }

        ivFlashSwitch.setOnClickListener {

            @DrawableRes val flashDrawableId: Int

            camera.flash = when (camera.flash) {
                Modes.Flash.FLASH_OFF -> {
                    flashDrawableId = R.drawable.ic_flash_auto
                    Modes.Flash.FLASH_AUTO
                }
                Modes.Flash.FLASH_AUTO -> {
                    flashDrawableId = R.drawable.ic_flash_on
                    Modes.Flash.FLASH_ON
                }
                Modes.Flash.FLASH_ON -> {
                    flashDrawableId = R.drawable.ic_flash_off
                    Modes.Flash.FLASH_OFF
                }
                else -> return@setOnClickListener
            }

            ivFlashSwitch.setImageDrawable(ActivityCompat.getDrawable(context, flashDrawableId))
        }

        tbSingleCapture.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) camera.enableCameraMode(Modes.CameraMode.SINGLE_CAPTURE)
            else camera.disableCameraMode(Modes.CameraMode.SINGLE_CAPTURE)
            updateViewState()
        }

        tbVideoCapture.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) camera.enableCameraMode(Modes.CameraMode.VIDEO_CAPTURE)
            else camera.disableCameraMode(Modes.CameraMode.VIDEO_CAPTURE)
            updateViewState()
        }

        tbContinuousFrame.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) camera.enableCameraMode(Modes.CameraMode.CONTINUOUS_FRAME)
            else camera.disableCameraMode(Modes.CameraMode.CONTINUOUS_FRAME)
            cameraPreviewFrameHandler.resetStats()
            updateViewState()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ivPlayPause.setOnClickListener {
                if (camera.isVideoRecording) {
                    camera.pauseVideoRecording()
                    ivPlayPause.isActivated = false
                } else {
                    camera.resumeVideoRecording()
                    ivPlayPause.isActivated = true
                }
            }
        }

        ivCameraSwitch.setOnClickListener {
            camera.facing = when (camera.facing) {
                Modes.Facing.FACING_BACK -> Modes.Facing.FACING_FRONT
                else -> Modes.Facing.FACING_BACK
            }
            updateViewState()
        }
    }

    private fun updateViewState() {

        if (camera.isSingleCaptureModeEnabled) {
            ivCaptureButton.show()
            ivCaptureButton.setOnClickListener(imageCaptureListener)
        } else {
            ivCaptureButton.setOnClickListener(null)
            ivCaptureButton.hide()
        }

        if (camera.isContinuousFrameModeEnabled) {
            tvBarcodes.show()
            ivOutputPreview.show()
        } else {
            tvBarcodes.hide()
            ivOutputPreview.hide()
        }

        if (camera.isVideoCaptureModeEnabled) {
            ivVideoCaptureButton.show()
            ivVideoCaptureButton.setOnClickListener(videoCaptureListener)
        } else {
            ivVideoCaptureButton.setOnClickListener(null)
            ivPlayPause.hide()
            ivVideoCaptureButton.hide()
        }

        ivCameraSwitch.isActivated = camera.facing != Modes.Facing.FACING_BACK
    }

    override fun onNewAspectRatio(aspectRatio: AspectRatio) {
        camera.aspectRatio = aspectRatio
    }

    companion object {

        val TAG: String = CameraFragment::class.java.run { canonicalName ?: name }

        private val permissions: Array<String> = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun newInstance(): CameraFragment = CameraFragment()
    }
}
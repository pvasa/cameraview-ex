/*
 * Copyright 2019 Priyank Vasa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.priyankvasa.android.cameraviewex

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.support.annotation.RequiresApi
import android.view.Surface
import com.priyankvasa.android.cameraviewex.extension.calculateVideoBitRate
import com.priyankvasa.android.cameraviewex.extension.isVideoStabilizationSupported
import java.io.File
import java.io.IOException
import java.util.SortedSet

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class VideoManager(private val warn: (message: String) -> Unit) {

    private var camera: CameraDevice? = null

    private lateinit var cameraCharacteristics: CameraCharacteristics

    private var mediaRecorder: MediaRecorder? = null

    private lateinit var videoRequestBuilder: CaptureRequest.Builder

    private val videoSizes = SizeMap()

    val isSize1080pSupported: Boolean get() = videoSizes.sizes(AspectRatio.Ratio16x9).contains(Size.P1080)

    val isSize720pSupported: Boolean get() = videoSizes.sizes(AspectRatio.Ratio16x9).contains(Size.P720)

    var isVideoRecording: Boolean = false
        private set

    fun setCameraDevice(camera: CameraDevice) {
        this.camera = camera
    }

    fun setCameraCharacteristics(characteristics: CameraCharacteristics) {
        cameraCharacteristics = characteristics
    }

    @Throws(CameraViewException::class)
    fun setupVideoRequestBuilder(
        baseRequestBuilder: CaptureRequest.Builder,
        config: CameraConfiguration,
        videoConfig: VideoConfiguration
    ) {

        videoRequestBuilder = camera?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {

            set(
                CaptureRequest.SCALER_CROP_REGION,
                baseRequestBuilder[CaptureRequest.SCALER_CROP_REGION]
            )

            val afMode = when (baseRequestBuilder[CaptureRequest.CONTROL_AF_MODE]) {
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ->
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                else -> baseRequestBuilder[CaptureRequest.CONTROL_AF_MODE]
            }

            set(CaptureRequest.CONTROL_AF_MODE, afMode)
            set(CaptureRequest.CONTROL_AWB_MODE, baseRequestBuilder[CaptureRequest.CONTROL_AWB_MODE])

            if (config.opticalStabilization.value) {
                set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    baseRequestBuilder[CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE]
                )
            } else if (videoConfig.videoStabilization) {
                if (cameraCharacteristics.isVideoStabilizationSupported())
                    set(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                else warn("Video stabilization not supported by selected camera ${camera?.id}.")
            }

            set(
                CaptureRequest.NOISE_REDUCTION_MODE,
                baseRequestBuilder[CaptureRequest.NOISE_REDUCTION_MODE]
            )
            set(
                CaptureRequest.CONTROL_AE_MODE,
                baseRequestBuilder[CaptureRequest.CONTROL_AE_MODE]
            )
            set(
                CaptureRequest.FLASH_MODE,
                baseRequestBuilder[CaptureRequest.FLASH_MODE]
            )
        } ?: throw IllegalStateException("Camera not initialized or already stopped")
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    fun setupMediaRecorder(
        outputFile: File,
        videoConfig: VideoConfiguration,
        videoSize: Size,
        outputOrientation: Int
    ) {

        mediaRecorder = (mediaRecorder?.apply { reset() } ?: MediaRecorder()).apply {
            setOrientationHint(outputOrientation)
            setAudioSource(videoConfig.audioSource.value)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(videoConfig.outputFormat.value)
            setOutputFile(outputFile.absolutePath)

            val bitRate =
                if (videoConfig.videoEncodingBitRate != VideoConfiguration.BIT_RATE_1080P) {
                    videoConfig.videoEncodingBitRate
                } else videoSize.calculateVideoBitRate()

            setVideoEncodingBitRate(bitRate)
            setVideoFrameRate(videoConfig.videoFrameRate)
            setVideoSize(videoSize.width, videoSize.height)
            setVideoEncoder(videoConfig.videoEncoder.value)
            setAudioEncoder(videoConfig.audioEncoder.value)

            setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> stopVideoRecording()
                }
            }

            // Let's not have videos less than one second
            when {
                videoConfig.maxDuration >= VideoConfiguration.DEFAULT_MIN_DURATION -> setMaxDuration(videoConfig.maxDuration)
                else -> {
                    warn("${videoConfig.maxDuration} is not a valid max duration value for video recording. Using minimum default ${VideoConfiguration.DEFAULT_MIN_DURATION}.")
                    setMaxDuration(VideoConfiguration.DEFAULT_MIN_DURATION)
                }
            }

            prepare()
        }
    }

    @Throws(IllegalStateException::class)
    fun getRecorderSurface(): Surface = mediaRecorder?.surface
        ?: throw IllegalStateException("Media recorder surface is not available. Cannot start video recording.")

    fun getCandidatesForOptimalSize(aspectRatio: AspectRatio): SortedSet<Size> = videoSizes.sizes(aspectRatio)

    fun updateVideoSizes(map: StreamConfigurationMap) {
        videoSizes.clear()
        map.getOutputSizes(MediaRecorder::class.java).forEach { videoSizes.add(Size(it.width, it.height)) }
    }

    @Throws(IllegalStateException::class)
    fun startMediaRecorder() = mediaRecorder?.start()?.also { isVideoRecording = true }
        ?: throw IllegalStateException("Media recorder surface is not available. Cannot start video recording.")

    fun getRequestBuilder(): CaptureRequest.Builder = videoRequestBuilder

    fun buildRequest(): CaptureRequest = videoRequestBuilder.build()

    @RequiresApi(Build.VERSION_CODES.N)
    fun pause() = mediaRecorder?.pause()

    @RequiresApi(Build.VERSION_CODES.N)
    fun resume() = mediaRecorder?.resume()

    @Throws(IllegalStateException::class)
    fun stopVideoRecording(): Boolean {
        mediaRecorder?.stop()
        mediaRecorder?.reset()
        isVideoRecording = false
        return true
    }

    fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
        camera = null
    }
}
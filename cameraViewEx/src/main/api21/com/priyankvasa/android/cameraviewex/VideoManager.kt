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

@file:Suppress("DEPRECATION")

package com.priyankvasa.android.cameraviewex

import android.annotation.SuppressLint
import android.hardware.Camera
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.math.MathUtils
import android.view.Surface
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

internal class VideoManager(private val warn: (message: String) -> Unit) {

    private var mediaRecorder: MediaRecorder? = null

    private val videoSizes: SizeMap = SizeMap()

    var isVideoRecording: Boolean = false
        private set

    @Throws(IllegalStateException::class)
    fun startMediaRecorder(): Unit = mediaRecorder?.start()?.also { isVideoRecording = true }
        ?: throw IllegalStateException("Media recorder surface is not available")

    @RequiresApi(Build.VERSION_CODES.N)
    @Throws(IllegalStateException::class)
    fun pause(): Unit? = mediaRecorder?.pause()

    @RequiresApi(Build.VERSION_CODES.N)
    @Throws(IllegalStateException::class)
    fun resume(): Unit? = mediaRecorder?.resume()

    @Throws(IllegalStateException::class)
    fun stopVideoRecording() {

        val t: Throwable? = runCatching { mediaRecorder?.stop() }.exceptionOrNull()

        mediaRecorder?.reset()
        isVideoRecording = false

        t?.let { throw it }
    }

    fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    fun addVideoSizes(sizes: Sequence<Size>) {
        videoSizes.clear()
        sizes.forEach { videoSizes.add(it) }
    }

    /** Returns the [mediaRecorder] surface */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(IllegalStateException::class)
    fun getRecorderSurface(): Surface = mediaRecorder?.surface
        ?: throw IllegalStateException("Media recorder surface is null")

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun createVideoRequestBuilder(
        camera: CameraDevice,
        baseRequestBuilder: CaptureRequest.Builder,
        config: CameraConfiguration,
        videoConfig: VideoConfiguration,
        isVideoStabilizationSupported: () -> Boolean
    ): CaptureRequest.Builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {

        baseRequestBuilder[CaptureRequest.SCALER_CROP_REGION]
            ?.let { set(CaptureRequest.SCALER_CROP_REGION, it) }

        val afMode: Int = when (baseRequestBuilder[CaptureRequest.CONTROL_AF_MODE]) {
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ->
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            else -> baseRequestBuilder[CaptureRequest.CONTROL_AF_MODE]
                ?: CaptureRequest.CONTROL_AF_MODE_AUTO
        }

        set(CaptureRequest.CONTROL_AF_MODE, afMode)

        baseRequestBuilder[CaptureRequest.CONTROL_AWB_MODE]
            ?.let { set(CaptureRequest.CONTROL_AWB_MODE, it) }

        if (config.opticalStabilization.value) {
            baseRequestBuilder[CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE]
                ?.let { set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, it) }
        } else if (videoConfig.videoStabilization) {
            if (isVideoStabilizationSupported())
                set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            else warn("Video stabilization not supported by selected camera (id ${camera.id}).")
        }

        baseRequestBuilder[CaptureRequest.NOISE_REDUCTION_MODE]
            ?.let { set(CaptureRequest.NOISE_REDUCTION_MODE, it) }

        baseRequestBuilder[CaptureRequest.CONTROL_AE_MODE]
            ?.let { set(CaptureRequest.CONTROL_AE_MODE, it) }

        baseRequestBuilder[CaptureRequest.FLASH_MODE]
            ?.let { set(CaptureRequest.FLASH_MODE, it) }
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    fun setupMediaRecorder(
        camera: Camera,
        cameraId: Int,
        previewSurface: Surface?,
        outputFile: File,
        videoConfig: VideoConfiguration,
        outputOrientation: Int,
        maxSize: Size,
        maxDurationAction: () -> Any
    ) {
        mediaRecorder = (mediaRecorder?.apply { reset() } ?: MediaRecorder()).apply {
            setCamera(camera)
            setVideoSource(MediaRecorder.VideoSource.CAMERA)
            setPreviewDisplay(previewSurface)
            setupInternal(
                cameraId,
                outputFile,
                videoConfig,
                outputOrientation,
                maxSize,
                maxDurationAction
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    fun setupMediaRecorder(
        cameraId: Int?,
        outputFile: File,
        videoConfig: VideoConfiguration,
        outputOrientation: Int,
        maxSize: Size,
        maxDurationAction: () -> Any
    ) {
        mediaRecorder = (mediaRecorder?.apply { reset() } ?: MediaRecorder()).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setupInternal(
                cameraId,
                outputFile,
                videoConfig,
                outputOrientation,
                maxSize,
                maxDurationAction
            )
        }
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    private fun MediaRecorder.setupInternal(
        cameraId: Int?,
        outputFile: File,
        videoConfig: VideoConfiguration,
        outputOrientation: Int,
        maxSize: Size,
        maxDurationAction: () -> Any
    ) {

        setOrientationHint(outputOrientation)

        setAudioSource(videoConfig.audioSource.value)

        if (!setCamcorderProfile(cameraId, videoConfig.videoSize)) {
            manualProfileSetup(videoConfig, maxSize)
        }

        setOutputFile(outputFile.absolutePath)

        setOnInfoListener { _, what, _ ->
            when (what) {
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> maxDurationAction()
            }
        }

        // Let's not have videos less than one second
        when {
            videoConfig.maxDuration >= VideoConfiguration.DEFAULT_MIN_DURATION -> setMaxDuration(videoConfig.maxDuration)
            else -> {
                warn("${videoConfig.maxDuration} is not a valid max duration value for video recording." +
                    " Using minimum default ${VideoConfiguration.DEFAULT_MIN_DURATION}.")
                setMaxDuration(VideoConfiguration.DEFAULT_MIN_DURATION)
            }
        }

        prepare()
    }

    private fun MediaRecorder.setCamcorderProfile(cameraId: Int?, videoSize: VideoSize): Boolean {

        @SuppressLint("InlinedApi")
        val profile: Int = when (videoSize) {

            VideoSize.P2160 -> CamcorderProfile.QUALITY_2160P
            VideoSize.P1080 -> CamcorderProfile.QUALITY_1080P
            VideoSize.P720 -> CamcorderProfile.QUALITY_720P
            VideoSize.P480 -> CamcorderProfile.QUALITY_480P
            VideoSize.CIF -> CamcorderProfile.QUALITY_CIF
            VideoSize.QVGA -> CamcorderProfile.QUALITY_QVGA
            VideoSize.QCIF -> CamcorderProfile.QUALITY_QCIF

            else -> return false
        }

        if (cameraId == null || !CamcorderProfile.hasProfile(cameraId, profile)) return false

        setProfile(CamcorderProfile.get(cameraId, profile))

        return true
    }

    private fun MediaRecorder.manualProfileSetup(videoConfig: VideoConfiguration, maxSize: Size) {
        setOutputFormat(videoConfig.outputFormat.value)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        videoConfig.videoSize.parseSize(maxSize)
            .run {
                setVideoSize(width, height)
                setVideoEncodingBitRate(calculateVideoBitRate())
            }
        setVideoFrameRate(videoConfig.videoFrameRate)
    }

    /**
     * Parse the video size from popular [VideoSize] choices. If the [VideoSize]
     * is not supported then an optimal size sill be chosen.
     */
    private fun VideoSize.parseSize(maxSize: Size): Size = when (this) {

        VideoSize.Min -> chooseOptimalVideoSize(this, maxSize, chooseSmallest = true)

        VideoSize.Min16x9,
        VideoSize.Min11x9,
        VideoSize.Min4x3,
        VideoSize.Min3x2,
        VideoSize.Min1x1 -> chooseOptimalVideoSize(this, maxSize, chooseSmallest = true)

        VideoSize.Max -> chooseOptimalVideoSize(this, maxSize)

        VideoSize.Max16x9,
        VideoSize.Max11x9,
        VideoSize.Max4x3,
        VideoSize.Max3x2,
        VideoSize.Max1x1 -> chooseOptimalVideoSize(this, maxSize)

        VideoSize.P2160, VideoSize.P1440, VideoSize.P1080, VideoSize.P720, VideoSize.P480 -> {
            if (videoSizes.containsSize(size)) size
            else chooseOptimalVideoSize(this, maxSize)
        }

        VideoSize.CIF, VideoSize.QVGA, VideoSize.QCIF -> {
            if (videoSizes.containsSize(size)) size
            else chooseOptimalVideoSize(this, maxSize, chooseSmallest = true)
        }
    }

    /**
     * Calculate a video bit rate based on the size. The bit rate is scaled
     * based on ratio of video size to 1080p size.
     */
    private fun Size.calculateVideoBitRate(): Int {
        val scaleFactor: Float = (height * width) / 2073600f // 1920 * 1080
        // Clamp to the MIN, MAX range.
        return MathUtils.clamp(
            (VideoConfiguration.BIT_RATE_1080P * scaleFactor).roundToInt(),
            VideoConfiguration.BIT_RATE_MIN,
            VideoConfiguration.BIT_RATE_MAX
        )
    }

    private fun chooseOptimalVideoSize(
        requestedVideoSize: VideoSize,
        maxSize: Size,
        chooseSmallest: Boolean = false
    ): Size {

        val ar: AspectRatio =
            when (requestedVideoSize) {
                VideoSize.Min,
                VideoSize.Max -> AspectRatio.of(maxSize.longerEdge, maxSize.shorterEdge)
                else -> requestedVideoSize.aspectRatio
            }

        return videoSizes.sizes(ar).run {
            if (chooseSmallest) first()
            else lastOrNull { it.width <= maxSize.longerEdge || it.height <= maxSize.longerEdge }
                ?: last()
        }
    }
}

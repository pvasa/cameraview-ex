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

    private val ratio16x9: AspectRatio = AspectRatio.of(16, 9)
    private val ratio11x9: AspectRatio = AspectRatio.of(11, 9)
    private val ratio4x3: AspectRatio = AspectRatio.of(4, 3)
    private val ratio3x2: AspectRatio = AspectRatio.of(3, 2)
    private val ratio1x1: AspectRatio = AspectRatio.of(1, 1)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(CameraViewException::class)
    fun createVideoRequestBuilder(
        camera: CameraDevice,
        baseRequestBuilder: CaptureRequest.Builder,
        config: CameraConfiguration,
        videoConfig: VideoConfiguration,
        isVideoStabilizationSupported: () -> Boolean
    ): CaptureRequest.Builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {

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
            if (isVideoStabilizationSupported())
                set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            else warn("Video stabilization not supported by selected camera ${camera.id}.")
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
    }

    @Suppress("DEPRECATION")
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    fun setupMediaRecorder(
        camera: Camera,
        cameraId: Int,
        previewSurface: Surface?,
        outputFile: File,
        videoConfig: VideoConfiguration,
        previewAspectRatio: AspectRatio,
        outputOrientation: Int,
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
                previewAspectRatio,
                outputOrientation,
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
        previewAspectRatio: AspectRatio,
        outputOrientation: Int,
        maxDurationAction: () -> Any
    ) {
        mediaRecorder = (mediaRecorder?.apply { reset() } ?: MediaRecorder()).apply {
            setVideoSource(MediaRecorder.VideoSource.DEFAULT)
            setupInternal(
                cameraId,
                outputFile,
                videoConfig,
                previewAspectRatio,
                outputOrientation,
                maxDurationAction
            )
        }
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    private fun MediaRecorder.setupInternal(
        cameraId: Int?,
        outputFile: File,
        videoConfig: VideoConfiguration,
        previewAspectRatio: AspectRatio,
        outputOrientation: Int,
        maxDurationAction: () -> Any
    ) {

        setOrientationHint(outputOrientation)

        setAudioSource(videoConfig.audioSource.value)

        val manualSetup: (AspectRatio) -> Unit = {
            setOutputFormat(videoConfig.outputFormat.value)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            videoConfig.videoSize.parseSize(it)
                .run {
                    setVideoSize(width, height)
                    setVideoEncodingBitRate(calculateVideoBitRate())
                }
            setVideoFrameRate(videoConfig.videoFrameRate)
        }

        val profile: Int = parseCamcorderProfile(
            cameraId,
            videoConfig.videoSize,
            previewAspectRatio,
            manualSetup
        )
        if (profile > -1) cameraId?.let { setProfile(CamcorderProfile.get(it, profile)) }

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

    private fun parseCamcorderProfile(
        cameraId: Int?,
        videoSize: VideoSize,
        previewAspectRatio: AspectRatio,
        manualSetup: (AspectRatio) -> Unit
    ): Int {

        val manualSetupTrigger: () -> Unit = {
            when (videoSize) {
                VideoSize.Min, VideoSize.Max -> manualSetup(previewAspectRatio)
                VideoSize.Min16x9, VideoSize.Max16x9 -> manualSetup(ratio16x9)
                VideoSize.Min11x9 -> manualSetup(ratio11x9)
                VideoSize.Min4x3, VideoSize.Max4x3 -> manualSetup(ratio4x3)
                VideoSize.Min3x2, VideoSize.Max3x2 -> manualSetup(ratio3x2)
                VideoSize.Min1x1, VideoSize.Max1x1 -> manualSetup(ratio1x1)
            }
        }

        val profile: Int = when (videoSize) {

            VideoSize.P2160 -> CamcorderProfile.QUALITY_2160P
            VideoSize.P1080 -> CamcorderProfile.QUALITY_1080P
            VideoSize.P720 -> CamcorderProfile.QUALITY_720P
            VideoSize.P480 -> CamcorderProfile.QUALITY_480P
            VideoSize.CIF -> CamcorderProfile.QUALITY_CIF
            VideoSize.QVGA -> CamcorderProfile.QUALITY_QVGA
            VideoSize.QCIF -> CamcorderProfile.QUALITY_QCIF

            VideoSize.Min, VideoSize.Min16x9, VideoSize.Min11x9,
            VideoSize.Min4x3, VideoSize.Min3x2, VideoSize.Min1x1,
            VideoSize.Max, VideoSize.Max16x9, VideoSize.Max4x3,
            VideoSize.Max3x2, VideoSize.Max1x1 -> {
                manualSetupTrigger()
                return -1
            }
        }

        if (cameraId == null || !CamcorderProfile.hasProfile(cameraId, profile)) {
            manualSetupTrigger()
            return -1
        }

        return profile
    }

    /**
     * Parse the video size from popular [VideoSize] choices. If the [VideoSize]
     * is not supported then an optimal size sill be chosen.
     */
    private fun VideoSize.parseSize(aspectRatio: AspectRatio): Size = when (this) {

        VideoSize.Min, VideoSize.Min16x9, VideoSize.Min11x9,
        VideoSize.Min4x3, VideoSize.Min3x2, VideoSize.Min1x1 ->
            chooseOptimalVideoSize(aspectRatio, chooseSmallest = true)

        VideoSize.P2160, VideoSize.P1080, VideoSize.P720, VideoSize.P480,
        VideoSize.CIF, VideoSize.QVGA, VideoSize.QCIF -> {
            if (videoSizes.containsSize(size)) size
            else chooseOptimalVideoSize(aspectRatio)
        }

        else -> chooseOptimalVideoSize(aspectRatio)
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

    /** Returns the [mediaRecorder] surface */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(IllegalStateException::class)
    fun getRecorderSurface(): Surface = mediaRecorder?.surface
        ?: throw IllegalStateException("Media recorder surface is not available")

    fun addVideoSizes(sizes: List<Size>) {
        videoSizes.clear()
        sizes.forEach { videoSizes.add(it) }
    }

    private fun chooseOptimalVideoSize(aspectRatio: AspectRatio, chooseSmallest: Boolean = false): Size {

        return videoSizes.sizes(aspectRatio).run {

            if (chooseSmallest) firstOrNull { size ->

                val (surfaceLonger: Int, surfaceShorter: Int) =
                    if (size.width < size.height) size.height to size.width
                    else size.width to size.height

                surfaceLonger == surfaceShorter * aspectRatio.x / aspectRatio.y && surfaceLonger <= 2160
            } ?: first()
            // Pick the largest of those big enough
            // If no size is big enough, pick the largest one.
            else lastOrNull { size ->

                val (surfaceLonger: Int, surfaceShorter: Int) =
                    if (size.width < size.height) size.height to size.width
                    else size.width to size.height

                surfaceLonger == surfaceShorter * aspectRatio.x / aspectRatio.y && surfaceLonger <= 2160
            } ?: last()
        }
    }

    @Throws(IllegalStateException::class)
    fun startMediaRecorder() = mediaRecorder?.start()?.also { isVideoRecording = true }
        ?: throw IllegalStateException("Media recorder surface is not available")

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
    }
}
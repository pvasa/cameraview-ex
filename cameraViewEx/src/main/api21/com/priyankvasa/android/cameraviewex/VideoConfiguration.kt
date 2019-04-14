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

@file:Suppress("unused")

package com.priyankvasa.android.cameraviewex

import android.media.MediaRecorder
import android.os.Build
import android.support.annotation.RequiresApi

/** Configuration that abstracts [MediaRecorder] parameters for video recording */
class VideoConfiguration {

    /**
     * An audio source defines both a default physical source of audio signal, and a recording
     * configuration.
     */
    var audioSource: AudioSource = DEFAULT_AUDIO_SOURCE

    /**
     * Output format of the recorded video.
     *
     * This config is overridden for video sizes
     * [VideoSize.P2160], [VideoSize.P1080], [VideoSize.P720], [VideoSize.P480],
     * [VideoSize.CIF], [VideoSize.QVGA], and [VideoSize.QCIF],
     * unless they are not supported by selected camera in which case
     */
    var outputFormat: VideoOutputFormat = DEFAULT_OUTPUT_FORMAT

    /** Number of frames recorded per second. */
    var videoFrameRate: Int = DEFAULT_VIDEO_FRAME_RATE

    /** The video encoding bit rate in bits per second. */
    var videoEncodingBitRate: Int = BIT_RATE_1080P

    /** The encoding used for audio. */
    var audioEncoder: AudioEncoder = DEFAULT_AUDIO_ENCODER

    /** The encoding used for video. */
    var videoEncoder: VideoEncoder = DEFAULT_VIDEO_ENCODER

    /**
     * Enable or disable video stabilization.
     * Refer [android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE]
     */
    var videoStabilization: Boolean = DEFAULT_VIDEO_STABILIZATION

    /** Max length a video can be */
    var maxDuration: Int = DEFAULT_MAX_DURATION

    /** Optional video size to record in. Valid values are [VideoSize] */
    var videoSize: VideoSize = DEFAULT_VIDEO_SIZE

    companion object {
        val DEFAULT_AUDIO_SOURCE: AudioSource = AudioSource.Camcorder
        val DEFAULT_OUTPUT_FORMAT: VideoOutputFormat = VideoOutputFormat.Mpeg4
        const val DEFAULT_VIDEO_FRAME_RATE: Int = 30
        val DEFAULT_AUDIO_ENCODER: AudioEncoder = AudioEncoder.Aac
        val DEFAULT_VIDEO_ENCODER: VideoEncoder = VideoEncoder.H264
        const val DEFAULT_VIDEO_STABILIZATION: Boolean = true
        val DEFAULT_VIDEO_SIZE: VideoSize = VideoSize.Max

        const val BIT_RATE_1080P: Int = 16000000
        const val BIT_RATE_MIN: Int = 64000
        const val BIT_RATE_MAX: Int = 40000000

        const val DEFAULT_MAX_DURATION: Int = 3600000 // 1 hour
        const val DEFAULT_MIN_DURATION: Int = 1000 // 1 second
    }
}

sealed class AudioSource(val value: Int) {

    /**
     * Microphone audio source tuned for video recording, with the same orientation
     * as the camera if available.
     */
    object Camcorder : AudioSource(MediaRecorder.AudioSource.CAMCORDER)

    /** Device's default audio source */
    object Default : AudioSource(MediaRecorder.AudioSource.DEFAULT)

    /** Microphone audio source */
    object Mic : AudioSource(MediaRecorder.AudioSource.MIC)

    /**
     * Voice call uplink (Tx) audio source.
     *
     * Capturing from `VOICE_UPLINK` source requires the
     * [android.Manifest.permission.CAPTURE_AUDIO_OUTPUT] permission.
     * This permission is reserved for use by system components and is not available to
     * third-party applications.
     */
    object VoiceUplink : AudioSource(MediaRecorder.AudioSource.VOICE_UPLINK)

    /**
     * Voice call downlink (Rx) audio source.
     *
     * Capturing from `VOICE_DOWNLINK` source requires the
     * [android.Manifest.permission.CAPTURE_AUDIO_OUTPUT] permission.
     * This permission is reserved for use by system components and is not available to
     * third-party applications.
     */
    object VoiceDownLink : AudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK)

    /**
     * Voice call uplink + downlink audio source
     *
     * Capturing from `VOICE_CALL` source requires the
     * [android.Manifest.permission.CAPTURE_AUDIO_OUTPUT] permission.
     * This permission is reserved for use by system components and is not available to
     * third-party applications.
     */
    object VoiceCall : AudioSource(MediaRecorder.AudioSource.VOICE_CALL)

    /** Microphone audio source tuned for voice recognition. */
    object VoiceRecognition : AudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)

    /**
     * Microphone audio source tuned for voice communications such as VoIP. It
     * will for instance take advantage of echo cancellation or automatic gain control
     * if available.
     */
    object VoiceCommunication : AudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)

    /**
     * Microphone audio source tuned for unprocessed (raw) sound if available, behaves like
     * [.DEFAULT] otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    object Unprocessed : AudioSource(MediaRecorder.AudioSource.UNPROCESSED)
}

sealed class VideoOutputFormat(val value: Int) {

    object Default : VideoOutputFormat(MediaRecorder.OutputFormat.DEFAULT)

    /** 3GPP media file format */
    object ThreeGpp : VideoOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

    /** MPEG4 media file format */
    object Mpeg4 : VideoOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

    /** The following formats are audio only .aac or .amr formats */

    /** AMR NB file format  */
    object AmrNb : VideoOutputFormat(MediaRecorder.OutputFormat.AMR_NB)

    /** AMR WB file format  */
    object AmrWb : VideoOutputFormat(MediaRecorder.OutputFormat.AMR_WB)

    /** AAC ADTS file format  */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    object AccAdts : VideoOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)

    /** H.264/AAC data encapsulated in MPEG2/TS  */
    @RequiresApi(Build.VERSION_CODES.O)
    object Mpeg2Ts : VideoOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS)

    /** VP8/VORBIS data in a WEBM container  */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object Webm : VideoOutputFormat(MediaRecorder.OutputFormat.WEBM)
}

sealed class AudioEncoder(val value: Int) {

    object Default : AudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
    /** AMR (Narrowband) audio codec */
    object AmrNb : AudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

    /** AMR (Wideband) audio codec */
    object AmrWb : AudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)

    /** AAC Low Complexity (AAC-LC) audio codec */
    object Aac : AudioEncoder(MediaRecorder.AudioEncoder.AAC)

    /** High Efficiency AAC (HE-AAC) audio codec */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    object HeAac : AudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)

    /** Enhanced Low Delay AAC (AAC-ELD) audio codec */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    object AacEld : AudioEncoder(MediaRecorder.AudioEncoder.AAC_ELD)

    /** Ogg Vorbis audio codec */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object Vorbis : AudioEncoder(MediaRecorder.AudioEncoder.VORBIS)
}

sealed class VideoEncoder(val value: Int) {
    object Default : VideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
    object H263 : VideoEncoder(MediaRecorder.VideoEncoder.H263)
    object H264 : VideoEncoder(MediaRecorder.VideoEncoder.H264)
    object Mpeg4Sp : VideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object Vp8 : VideoEncoder(MediaRecorder.VideoEncoder.VP8)

    @RequiresApi(Build.VERSION_CODES.N)
    object Hevc : VideoEncoder(MediaRecorder.VideoEncoder.HEVC)
}

/** Common video resolutions */
sealed class VideoSize(
    internal open val size: Size,
    internal open val aspectRatio: AspectRatio = size.aspectRatio
) {

    /** Minimum possible video size at preview's aspect ratio */
    object Min : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio
            get() = throw IllegalAccessException("aspectRatio must not be accessed for $this")
    }

    /** Minimum possible video size at 16:9 aspect ratio */
    object Min16x9 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(16, 9)
    }

    /** Minimum possible video size at 11:9 aspect ratio */
    object Min11x9 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(11, 9)
    }

    /** Minimum possible video size at 4:3 aspect ratio */
    object Min4x3 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(4, 3)
    }

    /** Minimum possible video size at 3:2 aspect ratio */
    object Min3x2 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(3, 2)
    }

    /** Minimum possible video size at 1:1 aspect ratio */
    object Min1x1 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(1, 1)
    }

    /** Maximum possible video size at preview's aspect ratio */
    object Max : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio
            get() = throw IllegalAccessException("aspectRatio must not be accessed for $this")
    }

    /** Maximum possible video size at 16:9 aspect ratio */
    object Max16x9 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(16, 9)
    }

    /** Maximum possible video size at 11:9 aspect ratio */
    object Max11x9 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(11, 9)
    }

    /** Maximum possible video size at 4:3 aspect ratio */
    object Max4x3 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(4, 3)
    }

    /** Maximum possible video size at 3:2 aspect ratio */
    object Max3x2 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(3, 2)
    }

    /** Maximum possible video size at 1:1 aspect ratio */
    object Max1x1 : VideoSize(Size.Invalid) {
        override val size: Size
            get() = throw IllegalAccessException("size must not be accessed for $this")
        override val aspectRatio: AspectRatio = AspectRatio.of(1, 1)
    }

    /** Video size 3840 x 2160 */
    object P2160 : VideoSize(Size.P2160)

    /** Video size 2560 x 1440 */
    object P1440 : VideoSize(Size.P1440)

    /**
     * Video size 1920 x 1080
     * Note that the vertical resolution for 1080p can also be 1088,
     * instead of 1080 (used by some vendors to avoid cropping during
     * video playback).
     */
    object P1080 : VideoSize(Size.P1080)

    /** Video size 1280 x 720 */
    object P720 : VideoSize(Size.P720)

    /**
     * Video size 720 x 480
     * Note that the horizontal resolution for 480p can also be other
     * values, such as 640 or 704, instead of 720.
     */
    object P480 : VideoSize(Size.P480)

    /** Video size 352 x 288 */
    object CIF : VideoSize(Size.CIF)

    /** Video size 320 x 240 */
    object QVGA : VideoSize(Size.QVGA)

    /** Video size 176 x 144 */
    object QCIF : VideoSize(Size.QCIF)
}
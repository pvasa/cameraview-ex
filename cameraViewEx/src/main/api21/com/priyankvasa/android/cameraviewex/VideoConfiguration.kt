/*
 * Copyright 2018 Priyank Vasa
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

@file:Suppress("unused", "SpellCheckingInspection")

package com.priyankvasa.android.cameraviewex

import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Configuration that abstracts [MediaRecorder] parameters for video recording
 */
class VideoConfiguration {

    /**
     * An audio source defines both a default physical source of audio signal, and a recording
     * configuration.
     */
    var audioSource: AudioSource = DEFAULT_AUDIO_SOURCE

    /** Output format of the recorded video. */
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

    /** Optional video size to record in */
    var videoSize: Size? = null

    companion object {
        val DEFAULT_AUDIO_SOURCE: AudioSource = AudioSource.Camcorder
        val DEFAULT_OUTPUT_FORMAT: VideoOutputFormat = VideoOutputFormat.Mpeg4
        const val DEFAULT_VIDEO_FRAME_RATE: Int = 30
        val DEFAULT_AUDIO_ENCODER: AudioEncoder = AudioEncoder.Aac
        val DEFAULT_VIDEO_ENCODER: VideoEncoder = VideoEncoder.H264
        const val DEFAULT_VIDEO_STABILIZATION = true

        const val BIT_RATE_1080P = 16000000
        const val BIT_RATE_MIN = 64000
        const val BIT_RATE_MAX = 40000000

        const val DEFAULT_MAX_DURATION = 3600000
        const val DEFAULT_MIN_DURATION = 1000
    }
}

inline class AudioSource(val value: Int) {

    companion object {

        /**
         * Microphone audio source tuned for video recording, with the same orientation
         * as the camera if available.
         */
        val Camcorder get() = AudioSource(MediaRecorder.AudioSource.CAMCORDER)

        /** Device's default audio source */
        val Default get() = AudioSource(MediaRecorder.AudioSource.DEFAULT)

        /** Microphone audio source */
        val Mic get() = AudioSource(MediaRecorder.AudioSource.MIC)

        /**
         * Voice call uplink (Tx) audio source.
         *
         * Capturing from `VOICE_UPLINK` source requires the
         * [android.Manifest.permission.CAPTURE_AUDIO_OUTPUT] permission.
         * This permission is reserved for use by system components and is not available to
         * third-party applications.
         */
        val VoiceUplink get() = AudioSource(MediaRecorder.AudioSource.VOICE_UPLINK)

        /**
         * Voice call downlink (Rx) audio source.
         *
         * Capturing from `VOICE_DOWNLINK` source requires the
         * [android.Manifest.permission.CAPTURE_AUDIO_OUTPUT] permission.
         * This permission is reserved for use by system components and is not available to
         * third-party applications.
         */
        val VoiceDownLink get() = AudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK)

        /**
         * Voice call uplink + downlink audio source
         *
         * Capturing from `VOICE_CALL` source requires the
         * [android.Manifest.permission.CAPTURE_AUDIO_OUTPUT] permission.
         * This permission is reserved for use by system components and is not available to
         * third-party applications.
         */
        val VoiceCall get() = AudioSource(MediaRecorder.AudioSource.VOICE_CALL)

        /** Microphone audio source tuned for voice recognition. */
        val VoiceRecognition get() = AudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)

        /**
         * Microphone audio source tuned for voice communications such as VoIP. It
         * will for instance take advantage of echo cancellation or automatic gain control
         * if available.
         */
        val VoiceCommunication get() = AudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)

        /**
         * Microphone audio source tuned for unprocessed (raw) sound if available, behaves like
         * [.DEFAULT] otherwise.
         */
        val Unprocessed
            @RequiresApi(Build.VERSION_CODES.N)
            get() = AudioSource(MediaRecorder.AudioSource.UNPROCESSED)
    }
}

inline class VideoOutputFormat(val value: Int) {

    companion object {

        val Default = VideoOutputFormat(MediaRecorder.OutputFormat.DEFAULT)

        /** 3GPP media file format */
        val ThreeGpp = VideoOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

        /** MPEG4 media file format */
        val Mpeg4 = VideoOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        /** The following formats are audio only .aac or .amr formats */

        /** AMR NB file format  */
        val AmrNb = VideoOutputFormat(MediaRecorder.OutputFormat.AMR_NB)

        /** AMR WB file format  */
        val AmrWb = VideoOutputFormat(MediaRecorder.OutputFormat.AMR_WB)

        /** AAC ADTS file format  */
        @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
        val AccAdts = VideoOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)

        /** H.264/AAC data encapsulated in MPEG2/TS  */
        @RequiresApi(Build.VERSION_CODES.O)
        val Mpeg2Ts = VideoOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS)

        /** VP8/VORBIS data in a WEBM container  */
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        val Webm = VideoOutputFormat(MediaRecorder.OutputFormat.WEBM)
    }
}

inline class AudioEncoder(val value: Int) {

    companion object {
        val Default = AudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
        /** AMR (Narrowband) audio codec */
        val AmrNb = AudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        /** AMR (Wideband) audio codec */
        val AmrWb = AudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
        /** AAC Low Complexity (AAC-LC) audio codec */
        val Aac = AudioEncoder(MediaRecorder.AudioEncoder.AAC)
        /** High Efficiency AAC (HE-AAC) audio codec */
        @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
        val HeAac = AudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
        /** Enhanced Low Delay AAC (AAC-ELD) audio codec */
        @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
        val AacEld = AudioEncoder(MediaRecorder.AudioEncoder.AAC_ELD)
        /** Ogg Vorbis audio codec */
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        val Vorbis = AudioEncoder(MediaRecorder.AudioEncoder.VORBIS)
    }
}

inline class VideoEncoder(val value: Int) {

    companion object {
        val Default = VideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
        val H263 = VideoEncoder(MediaRecorder.VideoEncoder.H263)
        val H264 = VideoEncoder(MediaRecorder.VideoEncoder.H264)
        val Mpeg4Sp = VideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP)
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        val Vp8 = VideoEncoder(MediaRecorder.VideoEncoder.VP8)
        @RequiresApi(Build.VERSION_CODES.N)
        val Hevc = VideoEncoder(MediaRecorder.VideoEncoder.HEVC)
    }
}
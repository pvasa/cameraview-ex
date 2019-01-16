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

package com.priyankvasa.android.cameraviewex

import android.annotation.TargetApi
import android.content.Context
import android.os.Build

@TargetApi(Build.VERSION_CODES.N)
internal open class Camera2Api24(
        override val listener: CameraInterface.Listener,
        preview: PreviewImpl,
        config: CameraConfiguration,
        context: Context
) : Camera2Api23(listener, preview, config, context) {

    override fun pauseVideoRecording(): Boolean = runCatching {
        mediaRecorder?.pause()
        true
    }.getOrElse { t ->
        listener.onCameraError(t as Exception)
        false
    }

    override fun resumeVideoRecording(): Boolean = runCatching {
        mediaRecorder?.resume()
        true
    }.getOrElse { t ->
        listener.onCameraError(t as Exception)
        false
    }
}
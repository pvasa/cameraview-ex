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

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Job

@TargetApi(Build.VERSION_CODES.N)
internal open class Camera2Api24(
    listener: CameraInterface.Listener,
    preview: PreviewImpl,
    config: CameraConfiguration,
    job: Job,
    context: Context
) : Camera2Api23(listener, preview, config, job, context) {

    override fun pauseVideoRecording(): Boolean = runCatching {
        videoManager.pause()
        true
    }.getOrElse {
        listener.onCameraError(it as Exception)
        false
    }

    override fun resumeVideoRecording(): Boolean = runCatching {
        videoManager.resume()
        true
    }.getOrElse {
        listener.onCameraError(it as Exception)
        false
    }
}
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
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build

/** A [CameraCaptureSession.CaptureCallback] for capturing a still picture. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
internal abstract class PictureCaptureCallback : CameraCaptureSession.CaptureCallback() {

    private var state: Int = 0

    internal fun setState(state: Int) {
        this.state = state
    }

    override fun onCaptureProgressed(
        session: CameraCaptureSession,
        request: CaptureRequest,
        partialResult: CaptureResult
    ) = process(partialResult)

    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
    ) = process(result)

    private fun process(result: CaptureResult) {

        when (state) {

            STATE_LOCKING -> {
                when (result.get(CaptureResult.CONTROL_AF_STATE) ?: return) { // af state
                    CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> {

                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)

                        if ((aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED)
                            && (awbState == null || awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED)) {
                            setState(STATE_CAPTURING)
                            onReady()
                        } else {
                            setState(STATE_LOCKED)
                            onPreCaptureRequired()
                        }
                    }
                }
            }

            STATE_PRE_CAPTURE -> {
                when (result.get(CaptureResult.CONTROL_AE_STATE)) {
                    null,
                    CaptureResult.CONTROL_AE_STATE_PRECAPTURE,
                    CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED,
                    CaptureResult.CONTROL_AE_STATE_CONVERGED -> {
                        setState(STATE_WAITING)
                        return
                    }
                }
                when (result.get(CaptureResult.CONTROL_AWB_STATE)) {
                    null,
                    CaptureResult.CONTROL_AWB_STATE_SEARCHING,
                    CaptureResult.CONTROL_AWB_STATE_CONVERGED -> setState(STATE_WAITING)
                }
            }

            STATE_WAITING -> {
                val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                    setState(STATE_CAPTURING)
                    onReady()
                }
            }
        }
    }

    /** Called when it is ready to take a still picture. */
    abstract fun onReady()

    /** Called when it is necessary to run the precapture sequence. */
    abstract fun onPreCaptureRequired()

    companion object {
        internal const val STATE_PREVIEW = 0
        internal const val STATE_LOCKING = 1
        internal const val STATE_LOCKED = 2
        internal const val STATE_PRE_CAPTURE = 3
        internal const val STATE_WAITING = 4
        internal const val STATE_CAPTURING = 5
    }
}
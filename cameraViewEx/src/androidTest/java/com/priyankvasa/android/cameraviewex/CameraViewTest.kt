/*
 * Copyright 2019 Priyank Vasa
 *
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.priyankvasa.android.cameraviewex

import android.os.Build
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import com.priyankvasa.android.cameraviewex.CameraViewActions.setAspectRatio
import com.priyankvasa.android.cameraviewex.CameraViewMatchers.hasAspectRatio
import com.priyankvasa.android.cameraviewex.test.R
import kotlinx.android.synthetic.main.texture_view.view.*
import java.io.Closeable
import java.io.File

@RunWith(AndroidJUnit4::class)
class CameraViewTest : GrantPermissionsRule() {

    @get:Rule
    val rule: ActivityTestRule<CameraViewActivity> = ActivityTestRule(CameraViewActivity::class.java)

    private var cameraViewIdlingResource: CameraViewIdlingResource? = null

    private val videoOutputDirectory =
        "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx/videos/".also { File(it).mkdirs() }

    private fun waitFor(ms: Long): ViewAction = object : AnythingAction("wait") {

        override fun perform(uiController: UiController?, view: View) {
            uiController.loopMainThreadForAtLeast(ms)
        }
    }

    private fun showingPreview(): ViewAssertion = ViewAssertion { view, _ ->

        if (Build.VERSION.SDK_INT < 14) return@ViewAssertion

        val cameraView = view as CameraView
        val textureView = cameraView.textureView ?: return@ViewAssertion
        val bitmap = textureView.bitmap
        val topLeft = bitmap.getPixel(0, 0)
        val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val bottomRight = bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)
        assertThat(
            "Preview possibly blank: ${Integer.toHexString(topLeft)}",
            topLeft == center && center == bottomRight,
            `is`(false)
        )
    }

    @Before
    fun setUpIdlingResource() {
        cameraViewIdlingResource =
            CameraViewIdlingResource(rule.activity.findViewById(R.id.camera) as CameraView)
        IdlingRegistry.getInstance().register(cameraViewIdlingResource)
    }

    @After
    @Throws(Exception::class)
    fun tearDownIdlingResource() {
        IdlingRegistry.getInstance().unregister(cameraViewIdlingResource)
        cameraViewIdlingResource?.close()
    }

    @Test
    fun testSetup() {
        onView(withId(R.id.camera))
            .check(matches(isDisplayed()))
        try {
            onView(withId(R.id.textureView))
                .check(matches(isDisplayed()))
        } catch (e: NoMatchingViewException) {
            onView(withId(R.id.surfaceView))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    @FlakyTest
    fun preview_isShowing() {
        onView(withId(R.id.camera))
            .perform(waitFor(1000))
            .check(showingPreview())
    }

    @Test
    fun testAspectRatio() {
        val cameraView = rule.activity.findViewById<CameraView>(R.id.camera)
        val ratios = cameraView.supportedAspectRatios
        for (ratio in ratios) {
            onView(withId(R.id.camera))
                .perform(setAspectRatio(ratio))
                .check(matches(hasAspectRatio(ratio)))
        }
    }

    @Test
    fun testAdjustViewBounds() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = view as CameraView
                assertThat(cameraView.adjustViewBounds, `is`(true))
                cameraView.adjustViewBounds = false
                assertThat(cameraView.adjustViewBounds, `is`(false))
                cameraView.adjustViewBounds = true
                assertThat(cameraView.adjustViewBounds, `is`(true))
            }
            .perform(object : AnythingAction("layout") {
                override fun perform(uiController: UiController, view: View) {
                    val params = view.layoutParams
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    view.layoutParams = params
                }
            })
            .check { view, _ ->
                val cameraView = view as CameraView
                val cameraRatio = cameraView.aspectRatio
                val viewRatio = AspectRatio.of(view.getWidth(), view.getHeight())
                assertThat<AspectRatio>(
                    cameraRatio,
                    `is`(AspectRatioIsCloseTo.closeToOrInverse(viewRatio))
                )
            }
    }

    @Test
    fun testPreviewViewSize() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = view as CameraView
                val preview: View = view.textureView ?: view.surfaceView

                val cameraRatio = cameraView.aspectRatio
                val textureRatio = AspectRatio.of(
                    preview.width,
                    preview.height
                )
                assertThat<AspectRatio>(
                    textureRatio,
                    `is`(AspectRatioIsCloseTo.closeToOrInverse(cameraRatio))
                )
            }
    }

    @Test
    fun testAutoFocus() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                // This can fail on devices without auto-focus support
                assertThat(cameraView.autoFocus, `is`(Modes.AutoFocus.AF_CONTINUOUS_PICTURE))
                cameraView.autoFocus = Modes.DEFAULT_AUTO_FOCUS
                assertThat(cameraView.autoFocus, `is`(Modes.DEFAULT_AUTO_FOCUS))
                cameraView.autoFocus = Modes.AutoFocus.AF_AUTO
                assertThat(cameraView.autoFocus, `is`(Modes.AutoFocus.AF_AUTO))
            }
    }

    @Test
    fun testFacing() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = view as CameraView
                assertThat<Any>(cameraView.facing, `is`(Modes.Facing.FACING_BACK))
                cameraView.facing = Modes.Facing.FACING_FRONT
                assertThat<Any>(cameraView.facing, `is`(Modes.Facing.FACING_FRONT))
            }
            .perform(waitFor(1000))
            .check(showingPreview())
    }

    @Test
    fun testFlash() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = view as CameraView
                assertThat<Any>(cameraView.flash, `is`(Modes.Flash.FLASH_AUTO))
                cameraView.flash = Modes.Flash.FLASH_TORCH
                assertThat<Any>(cameraView.flash, `is`(Modes.Flash.FLASH_TORCH))
            }
    }

    @Test
    fun testOpticalStabilization() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                assertThat<Any>(cameraView.opticalStabilization, `is`(true))
                cameraView.opticalStabilization = false
                assertThat<Any>(cameraView.opticalStabilization, `is`(false))
                cameraView.opticalStabilization = true
                assertThat<Any>(cameraView.opticalStabilization, `is`(true))
            }
    }

    @Test
    fun testAwb() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                assertThat<Any>(cameraView.awb, `is`(Modes.AutoWhiteBalance.AWB_AUTO))
                cameraView.awb = Modes.AutoWhiteBalance.AWB_FLUORESCENT
                assertThat<Any>(cameraView.awb, `is`(Modes.AutoWhiteBalance.AWB_FLUORESCENT))
            }
    }

    @Test
    fun testNoiseReduction() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                assertThat<Any>(cameraView.noiseReduction, `is`(Modes.NoiseReduction.NOISE_REDUCTION_HIGH_QUALITY))
                cameraView.noiseReduction = Modes.NoiseReduction.NOISE_REDUCTION_FAST
                assertThat<Any>(cameraView.noiseReduction, `is`(Modes.NoiseReduction.NOISE_REDUCTION_FAST))
            }
    }

    @Test
    fun testZeroShutterLag() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                assertThat(cameraView.zsl, `is`(true))
                cameraView.zsl = false
                assertThat(cameraView.zsl, `is`(false))
            }
    }

    @Test
    fun testCameraMode() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                assertThat(cameraView.isSingleCaptureModeEnabled, `is`(true))
                cameraView.setCameraMode(Modes.CameraMode.VIDEO_CAPTURE)
                assertThat(cameraView.isVideoCaptureModeEnabled, `is`(true))
                assertThat(cameraView.isSingleCaptureModeEnabled, `is`(false))
            }
    }

    @Test
    fun testPinchToZoom() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                assertThat(cameraView.pinchToZoom, `is`(true))
                cameraView.pinchToZoom = false
                assertThat(cameraView.pinchToZoom, `is`(false))
            }
    }

    @Test
    fun testTouchToFocus() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                assertThat(cameraView.touchToFocus, `is`(true))
                cameraView.touchToFocus = false
                assertThat(cameraView.touchToFocus, `is`(false))
            }
    }

    @Test
    fun testDigitalZoom() {
        onView(withId(R.id.camera))
            .check { view, _ ->
                val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return@check }
                assertThat(cameraView.currentDigitalZoom, `is`(1f))
                cameraView.currentDigitalZoom = cameraView.maxDigitalZoom
                assertThat(cameraView.currentDigitalZoom, `is`(cameraView.maxDigitalZoom))
            }
    }

    @Test
    fun testTakePicture() {
        val resource = TakePictureIdlingResource(rule.activity.findViewById(R.id.camera) as CameraView)
        onView(withId(R.id.camera))
            .perform(object : AnythingAction("take picture") {
                override fun perform(uiController: UiController, view: View) {
                    (view as CameraView).capture()
                }
            })
        try {
            IdlingRegistry.getInstance().register(resource)
            onView(withId(R.id.camera))
                .perform(waitFor(1000))
                .check(showingPreview())
            assertThat("Didn't receive valid JPEG data.", resource.receivedValidJpeg(), `is`(true))
        } finally {
            IdlingRegistry.getInstance().unregister(resource)
            resource.close()
        }
    }

    @Test
    fun testRecordVideo() {
        val recordingFile = File("$videoOutputDirectory/test_video.mp4")
        var saved = false
        onView(withId(R.id.camera))
            .perform(object : AnythingAction("take picture") {
                override fun perform(uiController: UiController, view: View) {
                    val cameraView = (view as CameraView).also { if (!it.isUiTestCompatible) return }
                    cameraView.setCameraMode(Modes.CameraMode.VIDEO_CAPTURE)
                    uiController.loopMainThreadForAtLeast(1000)
                    cameraView.startVideoRecording(recordingFile)
                    uiController.loopMainThreadForAtLeast(4000)
                    saved = cameraView.stopVideoRecording()
                    cameraView.setCameraMode(Modes.CameraMode.SINGLE_CAPTURE)
                }
            })
            .check(showingPreview())
        assertThat("Didn't save video file.", saved, `is`(true))
        assertThat(
            "Didn't record valid video file.",
            recordingFile.exists() && recordingFile.isFile && recordingFile.length() > 0,
            `is`(true)
        )
        recordingFile.delete()
    }

    /** Wait for a camera to open. */
    private class CameraViewIdlingResource(
        private val cameraView: CameraView
    ) : IdlingResource, Closeable {

        private var resourceCallback: IdlingResource.ResourceCallback? = null
        private var isIdleNow: Boolean = false

        init {
            cameraView
                .addCameraOpenedListener {
                    if (!isIdleNow) {
                        isIdleNow = true
                        resourceCallback.onTransitionToIdle()
                    }
                }
                .addCameraClosedListener { isIdleNow = false }
            isIdleNow = cameraView.isActive && cameraView.isCameraOpened
        }

        override fun close() {
            cameraView.removeAllListeners()
        }

        override fun getName(): String = CameraViewIdlingResource::class.java.simpleName

        override fun isIdleNow(): Boolean = isIdleNow

        override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
            resourceCallback = callback
        }
    }

    private class TakePictureIdlingResource(
        private val cameraView: CameraView
    ) : IdlingResource, Closeable {

        private var resourceCallback: IdlingResource.ResourceCallback? = null
        private var isIdleNow: Boolean = false
        private var validJpeg: Boolean = false

        init {
            cameraView.addPictureTakenListener { image: Image ->
                if (!isIdleNow) {
                    isIdleNow = true
                    validJpeg = image.data.size > 2 &&
                        image.data[0] == 0xFF.toByte() &&
                        image.data[1] == 0xD8.toByte()
                    resourceCallback.onTransitionToIdle()
                }
            }
        }

        override fun close() {
            cameraView.removeAllListeners()
        }

        override fun getName(): String = TakePictureIdlingResource::class.java.simpleName

        override fun isIdleNow(): Boolean = isIdleNow

        override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
            resourceCallback = callback
        }

        fun receivedValidJpeg(): Boolean = validJpeg
    }

    private abstract class AnythingAction(private val description: String) : ViewAction {
        override fun getConstraints(): Matcher<View> = IsAnything()
        override fun getDescription(): String = description
    }
}
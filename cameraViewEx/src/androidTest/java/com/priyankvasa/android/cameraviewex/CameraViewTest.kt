/*
 * Copyright 2018 Priyank Vasa
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

import android.os.SystemClock
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.IdlingRegistry
import android.support.test.espresso.IdlingResource
import android.support.test.espresso.NoMatchingViewException
import android.support.test.espresso.UiController
import android.support.test.espresso.ViewAction
import android.support.test.espresso.ViewAssertion
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.assertThat
import android.support.test.espresso.matcher.ViewMatchers.isDisplayed
import android.support.test.espresso.matcher.ViewMatchers.withId
import android.support.test.filters.FlakyTest
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import com.priyankvasa.android.cameraviewex.CameraViewActions.setAspectRatio
import com.priyankvasa.android.cameraviewex.CameraViewMatchers.hasAspectRatio
import com.priyankvasa.android.cameraviewex.test.R
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Matcher
import org.hamcrest.core.IsAnything
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable

@RunWith(AndroidJUnit4::class)
class CameraViewTest : GrantPermissionsRule() {

    @get:Rule
    val rule: ActivityTestRule<CameraViewActivity> = ActivityTestRule(CameraViewActivity::class.java)

    private var cameraViewIdlingResource: CameraViewIdlingResource? = null

    private fun waitFor(ms: Long): ViewAction = object : AnythingAction("wait") {

        override fun perform(uiController: UiController, view: View) {
            SystemClock.sleep(ms)
        }
    }

    private fun showingPreview(): ViewAssertion = ViewAssertion { view, noViewFoundException ->

        if (android.os.Build.VERSION.SDK_INT < 14) return@ViewAssertion

        val cameraView = view as CameraView
        val textureView = cameraView.findViewById<TextureView>(R.id.textureView)
        val bitmap = textureView.bitmap
        val topLeft = bitmap.getPixel(0, 0)
        val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val bottomRight = bitmap.getPixel(
                bitmap.width - 1, bitmap.height - 1)
        assertFalse("Preview possibly blank: " + Integer.toHexString(topLeft),
                topLeft == center && center == bottomRight)
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
            onView(withId(R.id.surface_view))
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
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
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
                .check { view, noViewFoundException ->
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
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
                    var preview: View? = view.findViewById(R.id.textureView)
                    if (preview == null) {
                        preview = view.findViewById(R.id.surface_view)
                    }
                    val cameraRatio = cameraView.aspectRatio
                    val textureRatio = AspectRatio.of(
                            preview?.width ?: return@check,
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
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
                    // This can fail on devices without auto-focus support
                    assertThat(cameraView.autoFocus, `is`(true))
                    cameraView.autoFocus = false
                    assertThat(cameraView.autoFocus, `is`(false))
                    cameraView.autoFocus = true
                    assertThat(cameraView.autoFocus, `is`(true))
                }
    }

    @Test
    fun testFacing() {
        onView(withId(R.id.camera))
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
                    assertThat<Any>(cameraView.facing, `is`(Modes.FACING_BACK))
                    cameraView.facing = Modes.FACING_FRONT
                    assertThat<Any>(cameraView.facing, `is`(Modes.FACING_FRONT))
                }
                .perform(waitFor(1000))
                .check(showingPreview())
    }

    @Test
    fun testFlash() {
        onView(withId(R.id.camera))
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
                    assertThat<Any>(cameraView.flash, `is`(Modes.Flash.FLASH_AUTO))
                    cameraView.flash = Modes.Flash.FLASH_TORCH
                    assertThat<Any>(cameraView.flash, `is`(Modes.Flash.FLASH_TORCH))
                }
    }

    @Test
    fun testOpticalStabilization() {
        onView(withId(R.id.camera))
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
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
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
                    assertThat<Any>(cameraView.awb, `is`(Modes.AutoWhiteBalance.AWB_AUTO))
                    cameraView.awb = Modes.AutoWhiteBalance.AWB_FLUORESCENT
                    assertThat<Any>(cameraView.awb, `is`(Modes.AutoWhiteBalance.AWB_FLUORESCENT))
                }
    }

    @Test
    fun testNoiseReduction() {
        onView(withId(R.id.camera))
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
                    assertThat<Any>(cameraView.noiseReduction, `is`(Modes.NoiseReduction.NOISE_REDUCTION_HIGH_QUALITY))
                    cameraView.noiseReduction = Modes.NoiseReduction.NOISE_REDUCTION_FAST
                    assertThat<Any>(cameraView.noiseReduction, `is`(Modes.NoiseReduction.NOISE_REDUCTION_FAST))
                }
    }

    @Test
    @Throws(Exception::class)
    fun testTakePicture() {
        val resource = TakePictureIdlingResource(rule.activity.findViewById(R.id.camera) as CameraView)
        onView(withId(R.id.camera))
                .perform(object : AnythingAction("take picture") {
                    override fun perform(uiController: UiController, view: View) {
                        val cameraView = view as CameraView
                        cameraView.capture()
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

    /**
     * Wait for a camera to open.
     */
    private class CameraViewIdlingResource(
            private val cameraView: CameraView
    ) : IdlingResource, Closeable {

        private var resourceCallback: IdlingResource.ResourceCallback? = null
        private var isIdleNow: Boolean = false

        private val callback = object : CameraView.Callback() {

            override fun onCameraOpened(cameraView: CameraView) {
                if (!isIdleNow) {
                    isIdleNow = true
                    resourceCallback?.onTransitionToIdle()
                }
            }

            override fun onCameraClosed(cameraView: CameraView) {
                isIdleNow = false
            }
        }

        init {
            cameraView.addCallback(callback)
            isIdleNow = cameraView.isCameraOpened
        }

        override fun close() {
            cameraView.removeCallback(callback)
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

        private val callback = object : CameraView.Callback() {

            override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
                if (!isIdleNow) {
                    isIdleNow = true
                    validJpeg = data.size > 2 &&
                            data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()
                    resourceCallback?.onTransitionToIdle()
                }
            }
        }

        init {
            cameraView.addCallback(callback)
        }

        override fun close() {
            cameraView.removeCallback(callback)
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

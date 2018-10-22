/*
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

package com.google.android.cameraview

import android.os.SystemClock
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.Espresso.registerIdlingResources
import android.support.test.espresso.Espresso.unregisterIdlingResources
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
import com.google.android.cameraview.CameraViewActions.setAspectRatio
import com.google.android.cameraview.CameraViewMatchers.hasAspectRatio
import junit.framework.Assert.assertFalse
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Matcher
import org.hamcrest.core.IsAnything
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable

@RunWith(AndroidJUnit4::class)
class CameraViewTest {

    @Rule
    val rule: ActivityTestRule<CameraViewActivity> = ActivityTestRule(CameraViewActivity::class.java)

    private var cameraViewIdlingResource: CameraViewIdlingResource? = null

    private fun waitFor(ms: Long): ViewAction {
        return object : AnythingAction("wait") {
            override fun perform(uiController: UiController, view: View) {
                SystemClock.sleep(ms)
            }
        }
    }

    private fun showingPreview(): ViewAssertion {
        return ViewAssertion { view, noViewFoundException ->
            if (android.os.Build.VERSION.SDK_INT < 14) {
                return@ViewAssertion
            }
            val cameraView = view as CameraView
            val textureView = cameraView.findViewById<TextureView>(R.id.texture_view)
            val bitmap = textureView.bitmap
            val topLeft = bitmap.getPixel(0, 0)
            val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            val bottomRight = bitmap.getPixel(
                    bitmap.width - 1, bitmap.height - 1)
            assertFalse("Preview possibly blank: " + Integer.toHexString(topLeft),
                    topLeft == center && center == bottomRight)
        }
    }

    @Before
    fun setUpIdlingResource() {
        cameraViewIdlingResource =
                CameraViewIdlingResource(rule.activity.findViewById(R.id.camera) as CameraView)
        registerIdlingResources(cameraViewIdlingResource)
    }

    @After
    @Throws(Exception::class)
    fun tearDownIdlingResource() {
        unregisterIdlingResources(cameraViewIdlingResource)
        cameraViewIdlingResource?.close()
    }

    @Test
    fun testSetup() {
        onView(withId(R.id.camera))
                .check(matches(isDisplayed()))
        try {
            onView(withId(R.id.texture_view))
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
        val cameraView = rule.activity.findViewById(R.id.camera)
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
                    var preview: View? = view.findViewById(R.id.texture_view)
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
                    assertThat<Any>(cameraView.facing, `is`(CameraView.FACING_BACK))
                    cameraView.facing = CameraView.FACING_FRONT
                    assertThat<Any>(cameraView.facing, `is`(CameraView.FACING_FRONT))
                }
                .perform(waitFor(1000))
                .check(showingPreview())
    }

    @Test
    fun testFlash() {
        onView(withId(R.id.camera))
                .check { view, noViewFoundException ->
                    val cameraView = view as CameraView
                    assertThat<Any>(cameraView.flash, `is`(CameraView.FLASH_AUTO))
                    cameraView.flash = CameraView.FLASH_TORCH
                    assertThat<Any>(cameraView.flash, `is`(CameraView.FLASH_TORCH))
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
                        cameraView.takePicture()
                    }
                })
        try {
            registerIdlingResources(resource)
            onView(withId(R.id.camera))
                    .perform(waitFor(1000))
                    .check(showingPreview())
            assertThat("Didn't receive valid JPEG data.", resource.receivedValidJpeg(), `is`(true))
        } finally {
            unregisterIdlingResources(resource)
            resource.close()
        }
    }

    /**
     * Wait for a camera to open.
     */
    private class CameraViewIdlingResource(
            private val mCameraView: CameraView
    ) : IdlingResource, Closeable {

        private var mResourceCallback: IdlingResource.ResourceCallback? = null
        private var mIsIdleNow: Boolean = false

        private val mCallback = object : CameraView.Callback() {

            override fun onCameraOpened(cameraView: CameraView) {
                if (!mIsIdleNow) {
                    mIsIdleNow = true
                    mResourceCallback?.onTransitionToIdle()
                }
            }

            override fun onCameraClosed(cameraView: CameraView) {
                mIsIdleNow = false
            }
        }

        init {
            mCameraView.addCallback(mCallback)
            mIsIdleNow = mCameraView.isCameraOpened
        }

        override fun close() {
            mCameraView.removeCallback(mCallback)
        }

        override fun getName(): String = CameraViewIdlingResource::class.java.simpleName

        override fun isIdleNow(): Boolean = mIsIdleNow

        override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
            mResourceCallback = callback
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

    private abstract class AnythingAction(private val mDescription: String) : ViewAction {
        override fun getConstraints(): Matcher<View> = IsAnything()
        override fun getDescription(): String = mDescription
    }
}

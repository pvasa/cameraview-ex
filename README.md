[![Build Status](https://travis-ci.org/pvasa/cameraview-ex.svg?branch=master)](https://travis-ci.org/pvasa/cameraview-ex)
[![Download](https://api.bintray.com/packages/ryan652/android/cameraview-ex/images/download.svg)](https://bintray.com/ryan652/android/cameraview-ex/_latestVersion)
[![License](https://img.shields.io/github/license/pvasa/cameraview-ex.svg)](LICENSE)

# CameraViewEx

_This is an extended version of [Google's cameraview library](https://github.com/google/cameraview) with better stability and many more features._

CameraViewEx highly simplifies integration of camera implementation and various camera features into any Android project. It uses new camera2 api with advanced features on API level 21 and higher and smartly switches to camera1 on older devices (API < 21).

Minimum API 14 is required to use CameraViewEx.
<br><br>

| API Level | Camera API | Preview View |
|:---------:|------------|--------------|
| 14-20     | Camera1    | TextureView  |
| 21+       | Camera2    | TextureView  |

#### Why another camera library?
Every camera library out there has some issues. Some good ones uses only camera1 api which cannot give best performance possible with today's devices, some are not updated anymore, some does not have all the features while some has a lot of features but uses complex api. CameraViewEx tries to solve all these issues while providing simpler api and more features.

## Features
- High quality image capture
- Multiple camera modes like single capture, continuous frame, and video capture
- Ability to enable all or multiple modes simultaneously
- Preview frame listener
- Any size preview
- Customizable continuous frame and single capture output size (different from preview size and aspect ratio)
- Support multiple formats for output images like jpeg, yuv_420_888, rgba_8888
- Pinch to zoom
- Touch to focus
- Configurable auto white balance, auto focus, flash, noise reduction, and optical / video stabilization
- Highly configurable video recording with most of the options from MediaRecorder
- Support multiple aspect ratios
- Switch between front and back camera
- Adjustable output image quality
- Zero shutter lag mode
- Shutter animation for single capture

## Usage

#### Import dependency
In app build.gradle,
```gradle
dependencies {
    // ...
    implementation "com.priyankvasa.android:cameraview-ex:3.5.5-alpha"
}
```

#### In layout xml
```xml
<com.priyankvasa.android.cameraviewex.CameraView
    android:id="@+id/camera"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:adjustViewBounds="true"
    android:keepScreenOn="true"
    app:aspectRatio="4:3"
    app:autoFocus="continuous_picture"
    app:awb="auto"
    app:cameraMode="single_capture"
    app:continuousFrameSize="W1440,1080"
    app:facing="back"
    app:flash="auto"
    app:jpegQuality="high"
    app:noiseReduction="high_quality"
    app:opticalStabilization="true"
    app:outputFormat="jpeg"
    app:pinchToZoom="true"
    app:shutter="short_time"
    app:singleCaptureSize="1920,H1080"
    app:touchToFocus="true"
    app:zsl="true" />
```

#### Setup camera
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Callbacks on UI thread
    camera.addCameraOpenedListener { /* Camera opened. */ }
        .addCameraErrorListener { t: Throwable, errorLevel: ErrorLevel -> /* Camera error! */ }
        .addCameraClosedListener { /* Camera closed. */ }
}

override fun onResume() {
    super.onResume()
    camera.start()
}

override fun onPause() {
    camera.stop()
    super.onPause()
}

override fun onDestroyView() {
    camera.destroy()
    super.onDestroyView()
}
```

#### Capture still picture
```kotlin
// enable only single capture mode
camera.setCameraMode(Modes.CameraMode.SINGLE_CAPTURE)
// OR keep other modes as is and enable single capture mode
camera.enableCameraMode(Modes.CameraMode.SINGLE_CAPTURE)
// Output format is whatever set for [app:outputFormat] parameter
// Callback on UI thread
camera.addPictureTakenListener { image: Image -> /* Picture taken. */ }
camera.capture()
// Disable single capture mode
camera.disableCameraMode(Modes.CameraMode.SINGLE_CAPTURE)
```

#### Process preview frames
```kotlin
// enable only continuous frame mode
camera.setCameraMode(Modes.CameraMode.CONTINUOUS_FRAME)
// OR keep other modes as is and enable continuous frame mode
camera.enableCameraMode(Modes.CameraMode.CONTINUOUS_FRAME)
// Output format is always ImageFormat.YUV_420_888
// Callback on background thread
camera.setContinuousFrameListener(maxFrameRate = 10f /*optional*/) { image: Image -> /* Frame available. */ }
// Disable continuous frame mode
camera.disableCameraMode(Modes.CameraMode.CONTINUOUS_FRAME)
```

#### Record video
```kotlin
// enable only video capture mode
camera.setCameraMode(Modes.CameraMode.VIDEO_CAPTURE)
// OR keep other modes as is and enable video capture mode
camera.enableCameraMode(Modes.CameraMode.VIDEO_CAPTURE)

// Callback on UI thread
camera.addVideoRecordStartedListener { /* Video recording started */ }

// Callback on UI thread
camera.addVideoRecordStoppedListener { isSuccess ->
    // Video recording stopped
    // isSuccess is true if video was recorded and saved successfully
}

camera.startVideoRecording(outputFile) {
    // Configure video (MediaRecorder) parameters
    audioEncoder = AudioEncoder.Aac
    videoFrameRate = 30
    videoStabilization = true
}
// When done recording
camera.stopVideoRecording()

// On APIs 24 and above video recording can be paused and resumed as well
camera.pauseVideoRecording()
camera.resumeVideoRecording()

// Disable video capture mode
camera.disableCameraMode(Modes.CameraMode.VIDEO_CAPTURE)
```

#### Set multiple modes simultaneously
- **In xml**
```xml
<com.priyankvasa.android.cameraviewex.CameraView
    android:id="@+id/camera"
    app:cameraMode="single_capture|continuous_frame|video_capture" />
```
- **Or in code**
```kotlin
camera.setCameraMode(Modes.CameraMode.SINGLE_CAPTURE or Modes.CameraMode.CONTINUOUS_FRAME or Modes.CameraMode.VIDEO_CAPTURE)

// Setup all the listeners including preview frame listener

camera.startVideoRecording(outputFile)
camera.capture()

// The listeners will receive their respective outputs
```

#### Switch through cameras for set facing
```kotlin
camera.facing = Modes.Facing.FACING_BACK // Open default back facing camera
camera.nextCamera() // Switch to next back facing camera
```


## Sample apps
- [Kotlin sample app](https://github.com/pvasa/cameraview-ex/tree/master/CameraEx) - Advanced usage
- [Java sample app](https://github.com/pvasa/cameraview-ex/tree/master/sampleAppJava) - Very simple usage

## Configuration

| CameraView property                          | XML Attribute            | Possible Values <br/> (bold value is the default one)                                                                           | Camera1 Support (API 14 to 20) | Camera2 Support (API 21+)   |
|----------------------------------------------|--------------------------|---------------------------------------------------------------------------------------------------------------------------------|--------------------------------|-----------------------------|
| cameraMode                                   | app:cameraMode           | **single_capture**, continuous_frame, video_capture                                                                             | :heavy_check_mark:             | :heavy_check_mark:          |
| facing                                       | app:facing               | **back**, front, external                                                                                                       | :heavy_check_mark:             | :heavy_check_mark:          |
| aspectRatio                                  | app:aspectRatio          | **4:3**, 16:9, 3:2, 16:10, 17:10, 8:5 <br/> _(or any other ratio supported by device)_                                          | :heavy_check_mark:             | :heavy_check_mark:          |
| continuousFrameSize                          | app:continuousFrameSize  | `W1920,H1080`, `W1440,1080`, `1280,H720` <br/> _(or any other size)_                                                            | :heavy_check_mark:             | :heavy_check_mark:          |
| singleCaptureSize                            | app:singleCaptureSize    | `W1920,H1080`, `W1440,1080`, `1280,H720` <br/> _(or any other size)_                                                            | :heavy_check_mark:             | :heavy_check_mark:          |
| touchToFocus                                 | app:touchToFocus         | **false**, true                                                                                                                 | :x:                            | :heavy_check_mark:          |
| autoFocus                                    | app:autoFocus            | **off**, auto, macro, continuous_video, <br/> continuous_picture, edof                                                          | :heavy_check_mark:             | :heavy_check_mark:          |
| pinchToZoom                                  | app:pinchToZoom          | **false**, true                                                                                                                 | :x:                            | :heavy_check_mark:          |
| flash                                        | app:flash                | **off**, on, torch, auto, redEye                                                                                                | :heavy_check_mark:             | :heavy_check_mark:          |
| awb                                          | app:awb                  | **off**, auto, incandescent, fluorescent, warm_fluorescent, <br/> daylight, cloudy_daylight, twilight, shade                    | :x:                            | :heavy_check_mark:          |
| opticalStabilization                         | app:opticalStabilization | **false**, true                                                                                                                 | :x:                            | :heavy_check_mark:          |
| noiseReduction                               | app:noiseReduction       | **off**, fast, high_quality, minimal, zero_shutter_lag                                                                          | :x:                            | :heavy_check_mark:          |
| shutter                                      | app:shutter              | **off**, short_time, long_time                                                                                                  | :heavy_check_mark:             | :heavy_check_mark:          |
| outputFormat                                 | app:outputFormat         | **jpeg**, yuv_420_888, rgba_8888                                                                                                | :heavy_check_mark:             | :heavy_check_mark:          |
| jpegQuality                                  | app:jpegQuality          | **default (90)**, low (60), medium (80), high (100)                                                                             | :heavy_check_mark:             | :heavy_check_mark:          |
| zsl                                          | app:zsl                  | **false**, true                                                                                                                 | :x:                            | :heavy_check_mark:          |
| cameraId <br> (get only)                     | N/A                      | Id of currently opened camera device                                                                                            | :heavy_check_mark:             | :heavy_check_mark:          |
| cameraIdsForFacing <br> (get only)           | N/A                      | Sorted set of ids of camera devices for selected facing                                                                         | :heavy_check_mark:             | :heavy_check_mark:          |
| isActive <br> (get only)                     | N/A                      | True if this `CameraView` instance is active and usable, false otherwise. It is set to false after `CameraView.destroy()` call. | :heavy_check_mark:             | :heavy_check_mark:          |
| isCameraOpened <br> (get only)               | N/A                      | True if camera is opened, false otherwise.                                                                                      | :heavy_check_mark:             | :heavy_check_mark:          |
| isSingleCaptureModeEnabled <br> (get only)   | N/A                      | True if single capture mode is enabled, false otherwise.                                                                        | :heavy_check_mark:             | :heavy_check_mark:          |
| isContinuousFrameModeEnabled <br> (get only) | N/A                      | True if continuous frame mode is enabled, false otherwise.                                                                      | :heavy_check_mark:             | :heavy_check_mark:          |
| isVideoCaptureModeEnabled <br> (get only)    | N/A                      | True if video capture mode is enabled, false otherwise.                                                                         | :heavy_check_mark:             | :heavy_check_mark:          |
| isVideoRecording <br> (get only)             | N/A                      | True if there is a video recording in progress, false otherwise.                                                                | :heavy_check_mark:             | :heavy_check_mark:          |
| supportedAspectRatios <br> (get only)        | N/A                      | Returns list of `AspectRatio` supported by selected camera.                                                                     | :heavy_check_mark:             | :heavy_check_mark:          |
| maxDigitalZoom <br> (get only)               | N/A                      | Returns a float value which is the maximum possible digital zoom value supported by selected camera.                            | :x:                            | :heavy_check_mark:          |
| currentDigitalZoom                           | N/A                      | Set camera digital zoom value. Must be between 1.0 and `CameraView.maxDigitalZoom` inclusive.                                   | :x:                            | :heavy_check_mark:          |

## Documentation

For detailed documentation, please refer these [docs](https://priyankvasa.dev/cameraviewex).
## Contribution Guidelines
See [CONTRIBUTING.md](https://github.com/pvasa/cameraview-ex/blob/master/CONTRIBUTING.md).

[![Build Status](https://travis-ci.org/pvasa/cameraview-ex.svg?branch=master)](https://travis-ci.org/pvasa/cameraview-ex)
[![Download](https://api.bintray.com/packages/ryan652/android/cameraview-ex/images/download.svg)](https://bintray.com/ryan652/android/cameraview-ex/_latestVersion)
[![License](https://img.shields.io/github/license/pvasa/cameraview-ex.svg)](LICENSE)

# CameraViewEx

_This is an extended version of [Google's cameraview library](https://github.com/google/cameraview) which provides more features over original implementation._

CameraViewEx makes integration of camera implementation and various camera features into any Android project very easy.

Requires API Level 14. The library uses Camera 1 API on API Level 14-20 and Camera2 on 21 and above.

| API Level | Camera API | Preview View |
|:---------:|------------|--------------|
| 14-20     | Camera1    | TextureView  |
| 21+       | Camera2    | TextureView  |

## Usage

#### Import dependency
In app build.gradle,
```gradle
dependencies {
    // ...
    implementation "com.priyankvasa.android:cameraview-ex:2.4.1"
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
    app:autoFocus="true"
    app:awb="auto"
    app:cameraMode="single_capture"
    app:facing="back"
    app:flash="auto"
    app:jpegQuality="high"
    app:noiseReduction="high_quality"
    app:opticalStabilization="true"
    app:outputFormat="jpeg"
    app:shutter="short_time"
    app:touchToFocus="true"
    app:zsl="true" />

<!-- Or to apply all those params as mentioned above use Widget.CameraView style -->

<com.priyankvasa.android.cameraviewex.CameraView
    android:id="@+id/camera"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    style="@style/Widget.CameraView" />
```

#### Setup camera
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    camera.addCameraOpenedListener { /* Camera opened. */ }
        .addCameraErrorListener { t: Throwable -> /* Camera error! */ }
        .addCameraClosedListener { /* Camera closed. */ }
}

override fun onResume() {
    super.onResume()
    camera.run { if (!isCameraOpened) start() }
}

override fun onPause() {
    camera.run { if (isCameraOpened) stop() }
    super.onPause()
}

override fun onDestroyView() {
    camera.run { if (isCameraOpened) stop(removeAllListeners = true) }
    super.onDestroyView()
}
```

#### Capture still picture
```kotlin
camera.cameraMode = Modes.CameraMode.SINGLE_CAPTURE
// Output format is whatever set for [app:outputFormat] parameter
camera.addPictureTakenListener { imageData: ByteArray -> /* Picture taken. */ }
camera.capture()
```

#### Process preview frames
```kotlin
camera.cameraMode = Modes.CameraMode.CONTINUOUS_FRAME
// Output format is always ImageFormat.YUV_420_888
camera.setPreviewFrameListener { image: Image -> /* Preview frame available. */ }
```

#### Record video
```kotlin
camera.cameraMode = Modes.CameraMode.VIDEO_CAPTURE
camera.startVideoRecording(outputFile) {
    // Configure video (MediaRecorder) parameters
    audioEncoder = AudioEncoder.Aac
    videoFrameRate = 120
    videoStabilization = true
}
// When done recording
camera.stopVideoRecording()

// On APIs 24 and above video recording can be paused and resumed as well
camera.pauseVideoRecording()
camera.resumeVideoRecording()
```

You can see a complete usage in the [sampleApp](https://github.com/pvasa/cameraview-ex/tree/development/sampleApp) app module or [sampleAppJava](https://github.com/pvasa/cameraview-ex/tree/development/sampleAppJava) for usage in Java.

## Features

| XML Attribute            | Possible Values <br/> (bold value is the default one)  |
|--------------------------|--------------------------------------------------------|
| app:cameraMode           | **single_capture**, continuous_frame, video_capture    |
| app:facing               | **back**, front                                        |
| app:aspectRatio          | **4:3**, 16:9, 3:2, 16:10, 17:10, 8:5 <br/> _(or any other ratio supported by device)_ |
| app:autoFocus            | **false**, true                                        |
| app:flash                | **off**, on, torch, auto, redEye                       |
| app:awb                  | **off**, auto, incandescent, fluorescent, warm_fluorescent, <br/> daylight, cloudy_daylight, twilight, shade |
| app:opticalStabilization | **false**, true                                        |
| app:noiseReduction       | **off**, fast, high_quality, minimal, zero_shutter_lag |
| app:shutter              | **off**, short_time, long_time                         |
| app:outputFormat         | **jpeg**, yuv_420_888, rgba_8888                       |
| app:jpegQuality          | **default**, low, medium, high                         |
| app:zsl                  | **false**, true                                        |

_**Note:** Devices that run **Camera1** implementation will only support **app:aspectRatio**, **app:autoFocus**, and **app:flash** attributes. All others will be ignored. Camera2 implementations (ie. API 21 and above) will support all features._

## Documentation
For detailed documentation, please refer these [docs](https://pvasa.github.io/cameraview-ex/camera-view-ex/com.priyankvasa.android.cameraviewex/-camera-view/index.html).

## Contribution Guidelines
See [CONTRIBUTING.md](https://github.com/pvasa/cameraview-ex/blob/master/CONTRIBUTING.md).

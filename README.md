# CameraViewEx

_This is an extended version of [Google's cameraview library](https://github.com/google/cameraview)_

*This is a preview release. The API is subject to change.*

CameraViewEx makes integration of camera implementation and various camera features into any Android project very easy.

Requires API Level 14. The library uses Camera 1 API on API Level 14-20 and Camera2 on 21 and above.

| API Level | Camera API | Preview View |
|:---------:|------------|--------------|
| 14-20     | Camera1    | TextureView  |
| 21+       | Camera2    | TextureView  |

## Features

- Camera preview by placing it in a layout XML (and calling the start method)
- Configuration by attributes
  - Facing (app:facing)
  - Aspect ratio (app:aspectRatio)
  - Auto-focus (app:autoFocus)
  - Flash (app:flash)
  - Auto white balance (app:awb)
  - Optical Stabilization (app:opticalStabilization)
  - Noise Reduction (app:noiseReduction)
  - Camera shutter view (app:shutter)
  - Output format (app:outputFormat)

## Usage

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
    app:facing="back"
    app:flash="auto"
    app:noiseReduction="high_quality"
    app:opticalStabilization="true"
    app:outputFormat="jpeg"
    app:shutter="short_time" />
```

#### In Fragment
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    camera.addCameraOpenedListener { Timber.i("Camera opened.") }
        .setPreviewFrameListener { image: Image -> Timber.i("Preview frame available.") }
        .addPictureTakenListener { imageData: ByteArray -> Timber.i("Picture taken.") }
        .addCameraClosedListener { Timber.i("Camera closed.") }
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

You can see a complete usage in the *sampleApp* app module.

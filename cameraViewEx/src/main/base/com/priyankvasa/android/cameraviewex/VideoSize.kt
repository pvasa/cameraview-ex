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

package com.priyankvasa.android.cameraviewex

/**
 * Class for common video resolutions
 */
sealed class VideoSize(val size: Size) : Size(size.width, size.height) {
    object SizeMax : VideoSize(Size(0, 0))
    object SizeMax16x9 : VideoSize(Size(16, 9))
    object SizeMax4x3 : VideoSize(Size(4, 3))
    object Size1080p : VideoSize(Size(1920, 1080))
    object Size720p : VideoSize(Size(1280, 720))
}
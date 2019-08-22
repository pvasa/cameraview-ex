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

object Config {

    const val jvmTarget = "1.8"

    private const val majorVersion = 3
    private const val minorVersion = 5
    private const val patchVersion = 5
    private const val versionClassifier = "alpha"

    val versionName: String
        get() = "$majorVersion.$minorVersion.$patchVersion${if (versionClassifier.isNotBlank()) "-" else ""}$versionClassifier"

    val versionCode: Int
        get() = Android.minSdkLib * 10000000 +
            majorVersion * 10000 +
            minorVersion * 100 +
            patchVersion

    object Versions {
        const val kotlin = "1.3.31"
        const val support = "28.0.0"
        const val constraintLayout = "1.1.3"
        const val dokka = "0.9.17"

        const val lifecycle = "1.1.1"

        const val googleServices = "4.2.0"
        const val firebaseCore = "16.0.5"
        const val firebaseMlVision = "18.0.1"
        const val koin = "1.0.2"
        const val coroutines = "1.2.0"
        const val timber = "4.7.1"
        const val glide = "4.8.0"

        const val mavenGradlePlugin = "2.1"
        const val bintrayPlugin = "1.8.1"

        // Unit tests
        const val jUnit5 = "5.3.1"

        // Android tests
        const val junit = "1.0.2"
        const val runner = "1.0.2"
        const val rules = "1.0.2"
        const val espressoCore = "3.0.2"
    }

    object Android {
        const val minSdk = 21
        const val minSdkLib = 14
        const val sdk = 28
    }

    object Libs {
        const val kotlinStdLibJdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"

        const val supportAnnotations = "com.android.support:support-annotations:${Versions.support}"
        const val appcompatV7 = "com.android.support:appcompat-v7:${Versions.support}"
        const val supportTransition = "com.android.support:transition:${Versions.support}"
        const val supportExifInterface = "com.android.support:exifinterface:${Versions.support}"
        const val constraintLayout = "com.android.support.constraint:constraint-layout:${Versions.constraintLayout}"

        const val lifecycleLivedata = "android.arch.lifecycle:livedata-core:${Versions.lifecycle}"
        const val koin = "org.koin:koin-android:${Versions.koin}"
        const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}"
        const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"

        const val firebaseCore = "com.google.firebase:firebase-core:${Versions.firebaseCore}"
        const val firebaseMlVision = "com.google.firebase:firebase-ml-vision:${Versions.firebaseMlVision}"

        const val glide = "com.github.bumptech.glide:glide:${Versions.glide}"

        const val timber = "com.jakewharton.timber:timber:${Versions.timber}"
    }

    object TestLibs {
        const val junitJupiterApi = "org.junit.jupiter:junit-jupiter-api:${Versions.jUnit5}"
        const val junitJupiterEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.jUnit5}"
        const val junitJupiterParams = "org.junit.jupiter:junit-jupiter-params:${Versions.jUnit5}"
    }

    object AndroidTestLibs {
        const val junit = "com.android.support.test:runner:${Versions.junit}"
        const val testRules = "com.android.support.test:rules:${Versions.rules}"
        const val testEspressoCore = "com.android.support.test.espresso:espresso-core:${Versions.espressoCore}"
    }
}
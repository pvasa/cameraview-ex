/*
 * Copyright 2018 Priyank Vasa
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

    private const val majorVersion = 2
    private const val minorVersion = 8
    private const val patchVersion = 0

    val versionName: String get() = "$majorVersion.$minorVersion.$patchVersion"

    val versionCode: Int
        get() = Android.minSdkLib * 10000000 +
                majorVersion * 10000 +
                minorVersion * 100 +
                patchVersion

    object Versions {
        const val kotlin = "1.3.11"
        const val ktxCore = "1.0.0"
        const val support = "1.0.0"
        const val constraintLayout = "1.1.3"
        const val dokka = "0.9.17"

        const val lifecycle = "2.0.0"

        const val googleServices = "4.2.0"
        const val firebaseCore = "16.0.5"
        const val firebaseMlVision = "18.0.1"
        const val coroutines = "1.0.1"
        const val timber = "4.7.1"
        const val glide = "4.8.0"

        const val mavenGradlePlugin = "2.1"
        const val bintrayPlugin = "1.8.1"

        // Unit tests
        const val jUnit5 = "5.3.1"

        // Android tests
        const val junit = "1.1.0"
        const val runner = "1.1.0"
        const val rules = "1.1.0"
        const val espressoCore = "3.1.0"
    }

    object Android {
        const val minSdk = 21
        const val minSdkLib = 14
        const val sdk = 28
    }

    object Libs {
        const val kotlinStdLibJdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"

        const val ktxCore = "androidx.core:core-ktx:${Versions.ktxCore}"

        const val supportAnnotations = "androidx.annotation:annotation:${Versions.support}"
        const val appcompatV7 = "androidx.appcompat:appcompat:${Versions.support}"
        const val supportTransition = "androidx.transition:transition:${Versions.support}"
        const val constraintLayout = "androidx.constraintlayout:constraintlayout:${Versions.constraintLayout}"

        const val lifecycleLivedata = "androidx.lifecycle:lifecycle-livedata-core:${Versions.lifecycle}"

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
        const val junit = "androidx.test.ext:junit:${Versions.junit}"
        const val testRules = "androidx.test:rules:${Versions.rules}"
        const val testEspressoCore = "androidx.test.espresso:espresso-core:${Versions.espressoCore}"
    }
}
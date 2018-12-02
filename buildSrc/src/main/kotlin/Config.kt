object Config {

    const val jvmTarget = "1.8"

    private const val majorVersion = 2
    private const val minorVersion = 3
    private const val patchVersion = 0

    public val versionName: String get() = "$majorVersion.$minorVersion.$patchVersion"

    public val versionCode: Int
        get() = Android.minSdkLib * 10000000 +
                majorVersion * 10000 +
                minorVersion * 100 +
                patchVersion

    object Versions {
        const val kotlin = "1.3.10"
        const val support = "28.0.0"
        const val constraintLayout = "1.1.3"
        const val dokka = "0.9.17"

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
        const val kotlinStdLibJdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.10"

        const val supportAnnotations = "com.android.support:support-annotations:${Versions.support}"
        const val supportV4 = "com.android.support:support-v4:${Versions.support}"
        const val appcompatV7 = "com.android.support:appcompat-v7:${Versions.support}"
        const val supportTransition = "com.android.support:transition:${Versions.support}"
        const val constraintLayout = "com.android.support.constraint:constraint-layout:${Versions.constraintLayout}"

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
        const val testRunner = "com.android.support.test:runner:${Versions.runner}"
        const val testRules = "com.android.support.test:rules:${Versions.rules}"
        const val testEspressoCore = "com.android.support.test.espresso:espresso-core:${Versions.espressoCore}"
    }
}
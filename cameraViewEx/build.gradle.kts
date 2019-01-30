// Copyright 2019 Priyank Vasa
//
// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import org.jetbrains.dokka.gradle.DokkaAndroidTask
import org.jetbrains.dokka.gradle.LinkMapping
import org.jetbrains.dokka.gradle.SourceRoot
import org.jetbrains.kotlin.gradle.internal.AndroidExtensionsExtension
import org.jetbrains.kotlin.gradle.internal.CacheImplementation

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
    kotlin("kapt")
    id("org.jetbrains.dokka-android")
}

ext { set("versionName", Config.versionName) }

android {

    signingConfigs {
        create("config") {
            storeFile = file("$rootDir/keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "android-release"
            keyPassword = System.getenv("KEYALIAS_PASSWORD")
        }
    }

    compileSdkVersion(Config.Android.sdk)

    defaultConfig {
        minSdkVersion(Config.Android.minSdkLib)
        targetSdkVersion(Config.Android.sdk)
        versionCode = Config.versionCode
        versionName = Config.versionName
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")
        renderscriptTargetApi = 21
    }

    testBuildType = "stage"

    buildTypes {

        getByName("debug") {
            isMinifyEnabled = false
            isUseProguard = false
            isDebuggable = true
            versionNameSuffix = "-debug"
        }

        create("stage").apply {
            initWith(buildTypes["debug"])
            versionNameSuffix = "-stage"
        }

        getByName("release") {
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs["config"]
        }
    }

    sourceSets["main"].java.srcDirs(
        "src/main/base",
        "src/main/api9",
        "src/main/api14",
        "src/main/api21",
        "src/main/api23",
        "src/main/api24",
        "src/main/sample"
    )

    compileOptions {
        setSourceCompatibility(JavaVersion.VERSION_1_8)
        setTargetCompatibility(JavaVersion.VERSION_1_8)
    }
}

androidExtensions {
    configure(delegateClosureOf<AndroidExtensionsExtension> {
        isExperimental = true
        defaultCacheImplementation = CacheImplementation.SPARSE_ARRAY
    })
}

tasks.withType(Test::class.java) {
    useJUnitPlatform {
        //includeTags "fast", "smoke & feature-a"
        //excludeTags "slow", "ci"
        includeEngines = setOf("junit-jupiter")
        excludeEngines = setOf("junit-vintage")
    }
    systemProperty("java.util.logging.manager", "java.util.logging.LogManager")
    systemProperty("junit.jupiter.conditions.deactivate", "*")
    systemProperties = mutableMapOf<String, Any>(
        "junit.jupiter.extensions.autodetection.enabled" to "true",
        "junit.jupiter.testinstance.lifecycle.default" to "per_class"
    )
}

dependencies {

    // Kotlin
    implementation(Config.Libs.kotlinStdLibJdk8)

    // Android support
    implementation(Config.Libs.supportAnnotations)
    implementation(Config.Libs.supportTransition)

    // Dependency injection
    implementation(Config.Libs.koin)

    // KotlinX
    implementation(Config.Libs.coroutinesCore)
    implementation(Config.Libs.coroutinesAndroid)

    // Lifecycle
    implementation(Config.Libs.lifecycleLivedata)

    // Timber
    implementation(Config.Libs.timber)

    // Unit tests
    // (Required) Writing and executing Unit Tests on the JUnit Platform
    testImplementation(Config.TestLibs.junitJupiterApi)
    testRuntimeOnly(Config.TestLibs.junitJupiterEngine)

    // (Optional) If you need "Parameterized Tests"
    testImplementation(Config.TestLibs.junitJupiterParams)

    // Android tests
    androidTestImplementation(Config.AndroidTestLibs.junit) { exclude("support-annotations") }
    androidTestImplementation(Config.AndroidTestLibs.testRules) { exclude("support-annotations") }
    androidTestImplementation(Config.AndroidTestLibs.testEspressoCore) { exclude("support-annotations") }
}

tasks.getByName("dokka") {

    this as DokkaAndroidTask

    moduleName = "cameraViewEx"
    outputFormat = "html"
    outputDirectory = "../docs"

    // These tasks will be used to determine source directories and classpath

    kotlinTasks(closureOf<Any?> {
        defaultKotlinTasks()/* + [":some:otherCompileKotlin", project("another").compileKotlin]*/
    })

    // List of files with module and package documentation
    // http://kotlinlang.org/docs/reference/kotlin-doc.html#module-and-package-documentation
    includes = listOf("../README.md")

    // The list of files or directories containing sample code (referenced with @sample tags)
    samples = listOf("src/main/sample")

    jdkVersion = 8 // Used for linking to JDK

    // Use default or set to custom path to cache directory
    // to enable package-list caching
    // When set to default, caches stored in $USER_HOME/.cache/dokka
    cacheRoot = "default"

    // Use to include or exclude non public members.
    includeNonPublic = false

    // Do not output deprecated members. Applies globally, can be overridden by packageOptions
    skipDeprecated = false

    // Emit warnings about not documented members. Applies globally, also can be overridden by packageOptions
    reportUndocumented = false

    skipEmptyPackages = true // Do not create index pages for empty packages

    impliedPlatforms = mutableListOf("JVM", "Android") // See platforms section of documentation

    // Manual adding files to classpath
    // This property not overrides classpath collected from kotlinTasks but appends to it
    //classpath = [new File("$buildDir/other.jar")]

    // By default, sourceRoots is taken from kotlinTasks, following roots will be appended to it
    // Short form sourceRoots
    sourceDirs = files("src/main/java").asIterable()

    // By default, sourceRoots is taken from kotlinTasks, following roots will be appended to it
    // Full form sourceRoot declaration
    // Repeat for multiple sourceRoots
    sourceRoot(delegateClosureOf<SourceRoot> {
        // Path to source root
        path = "src"
        // See platforms section of documentation
        platforms = listOf("JVM", "Android")
    })

    // Specifies the location of the project source code on the Web.
    // If provided, Dokka generates "source" links for each declaration.
    // Repeat for multiple mappings
    linkMapping(delegateClosureOf<LinkMapping> {

        // Source directory
        dir = "src/main/java"

        // URL showing where the source code can be accessed through the web browser
        url = "https://github.com/pvasa/cameraview-ex/tree/master/cameraViewEx/src/main"

        // Suffix which is used to append the line number to the URL. Use #L for GitHub
        suffix = "#L"
    })

    // No default documentation link to kotlin-stdlib
    noStdlibLink = false

    // Allows linking to documentation of the project's dependencies (generated with Javadoc or Dokka)
    // Repeat for multiple links
    //externalDocumentationLink(delegateClosureOf<DokkaConfiguration.ExternalDocumentationLink.Builder> {
    // Root URL of the generated documentation to link with. The trailing slash is required!
    //url = uri("https://example.com/docs/").toURL()

    // If package-list file located in non-standard location
    //packageListUrl = uri("file:///home/user/localdocs/package-list").toURL()
    //})
}

apply {
    from("publish.gradle")
}
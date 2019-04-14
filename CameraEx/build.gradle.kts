import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {

    repositories {
        google()
        jcenter()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:3.3.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Config.Versions.kotlin}")
        classpath("com.google.gms:google-services:${Config.Versions.googleServices}") // google-services plugin
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {

    repositories {
        google()
        maven { url = uri("file:$rootDir/../mavenRepo/") }
        jcenter()
    }

    dependencies {
        configurations.all {
            resolutionStrategy.eachDependency {
                when (requested.group) {
                    "com.github.bumptech.glide" -> exclude("support-annotations")
                }
            }
        }
    }

    tasks.withType<KotlinCompile>().all {
        kotlinOptions {
            freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
            jvmTarget = Config.jvmTarget
        }
    }
}

tasks.create<Delete>("clean") {
    delete(rootProject.buildDir)
}

tasks.create<Exec>("cleanCameraViewEx") {
    workingDir("$rootDir/../")
    // Clean cameraViewEx module
    commandLine("sh", "-c", "./gradlew clean")
}
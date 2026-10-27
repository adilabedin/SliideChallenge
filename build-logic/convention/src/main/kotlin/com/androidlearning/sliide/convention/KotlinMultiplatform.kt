package com.androidlearning.sliide.convention

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
            namespace = pathToPackageName()
            compileSdk = libs.versionInt("projectCompileSdkVersion")
            minSdk = libs.versionInt("projectMinSdkVersion")
            withHostTest { }
        }

        iosArm64()
        iosSimulatorArm64()

        applyDefaultHierarchyTemplate()

        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.time.ExperimentalTime",
            )
        }
    }

    configureJvmTarget()
}

/** Required by Compose Multiplatform resources on Android. */
internal fun Project.enableAndroidResources() {
    extensions.configure<KotlinMultiplatformExtension> {
        extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
            androidResources.enable = true
        }
    }
}

internal fun Project.configureJvmTarget() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
}

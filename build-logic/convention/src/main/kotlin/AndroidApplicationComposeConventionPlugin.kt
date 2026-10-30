import com.android.build.api.dsl.ApplicationExtension
import com.androidlearning.sliide.convention.configureJvmTarget
import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import com.androidlearning.sliide.convention.versionInt
import com.androidlearning.sliide.convention.versionString
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** For `:androidApp` — the thin Android shell around `:composeApp`. */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("com.androidlearning.sliide.convention.ktlint")
            }

            extensions.configure<ApplicationExtension> {
                namespace = libs.versionString("projectApplicationId")
                compileSdk = libs.versionInt("projectCompileSdkVersion")

                defaultConfig {
                    applicationId = libs.versionString("projectApplicationId")
                    minSdk = libs.versionInt("projectMinSdkVersion")
                    targetSdk = libs.versionInt("projectTargetSdkVersion")
                    versionCode = libs.versionInt("projectVersionCode")
                    versionName = libs.versionString("projectVersionName")
                }
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = false
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
                buildFeatures {
                    compose = true
                }
            }

            configureJvmTarget()

            dependencies {
                "implementation"(libs.library("androidx-activity-compose"))
                "debugImplementation"(libs.library("compose-ui-tooling"))
            }
        }
    }
}

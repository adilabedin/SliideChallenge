import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * For `:composeApp` — the shared KMP library that hosts the app-level UI, navigation and DI graph
 * and is exported to iOS as the `ComposeApp` framework.
 */
class CmpApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.androidlearning.sliide.convention.cmp.library")

            extensions.configure<KotlinMultiplatformExtension> {
                targets.withType(KotlinNativeTarget::class.java).configureEach {
                    binaries.framework {
                        baseName = "ComposeApp"
                        isStatic = true
                    }
                }
            }

            dependencies {
                "commonMainImplementation"(libs.library("koin-core"))
                "commonMainImplementation"(libs.library("koin-compose"))
                "commonMainImplementation"(libs.library("koin-compose-viewmodel"))
                "commonMainImplementation"(libs.library("lifecycle-viewmodel-compose"))
                "commonMainImplementation"(libs.library("lifecycle-runtime-compose"))
                "commonMainImplementation"(libs.library("navigation-compose"))
                "androidMainImplementation"(libs.library("koin-android"))
            }
        }
    }
}

import com.androidlearning.sliide.convention.enableAndroidResources
import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class CmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.androidlearning.sliide.convention.kmp.library")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
            }

            enableAndroidResources()

            dependencies {
                "commonMainImplementation"(libs.library("compose-runtime"))
                "commonMainImplementation"(libs.library("compose-foundation"))
                "commonMainImplementation"(libs.library("compose-ui"))
                "commonMainImplementation"(libs.library("compose-material3"))
                "commonMainImplementation"(libs.library("compose-material-icons-core"))
                "commonMainImplementation"(libs.library("compose-components-resources"))
                "commonMainImplementation"(libs.library("compose-ui-tooling-preview"))

                // KMP modules have no build types, so debugImplementation is unavailable for previews.
                "androidMainImplementation"(libs.library("compose-ui-tooling"))
            }
        }
    }
}

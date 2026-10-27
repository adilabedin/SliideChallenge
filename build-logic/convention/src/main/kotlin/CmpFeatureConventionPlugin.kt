import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class CmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.androidlearning.sliide.convention.cmp.library")

            dependencies {
                "commonMainImplementation"(project(":core:domain"))
                "commonMainImplementation"(project(":core:presentation"))
                "commonMainImplementation"(project(":core:designsystem"))

                "commonMainImplementation"(libs.library("koin-core"))
                "commonMainImplementation"(libs.library("koin-compose"))
                "commonMainImplementation"(libs.library("koin-compose-viewmodel"))

                "commonMainImplementation"(libs.library("lifecycle-viewmodel-compose"))
                "commonMainImplementation"(libs.library("lifecycle-runtime-compose"))
                "commonMainImplementation"(libs.library("compose-material3-adaptive"))
            }
        }
    }
}

import androidx.room.gradle.RoomExtension
import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("androidx.room")
            }

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                "commonMainApi"(libs.library("room-runtime"))
                "commonMainApi"(libs.library("sqlite-bundled"))

                val roomCompiler = libs.library("room-compiler")
                "kspAndroid"(roomCompiler)
                "kspIosArm64"(roomCompiler)
                "kspIosSimulatorArm64"(roomCompiler)
            }
        }
    }
}

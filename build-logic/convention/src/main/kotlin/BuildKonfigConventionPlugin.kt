import com.androidlearning.sliide.convention.pathToPackageName
import com.codingfeline.buildkonfig.compiler.FieldSpec
import com.codingfeline.buildkonfig.gradle.BuildKonfigExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.util.Properties

/** Injects secrets from root `local.properties` into generated `BuildKonfig` for KMP common code. */
class BuildKonfigConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.codingfeline.buildkonfig")

            extensions.configure<BuildKonfigExtension> {
                packageName = target.pathToPackageName()

                defaultConfigs {
                    val apiKey = readLocalProperty(API_KEY_PROPERTY)
                        ?: throw IllegalStateException(
                            "Missing $API_KEY_PROPERTY in local.properties at project root. " +
                                "Copy local.properties.example and set your GoRest API key.",
                        )
                    buildConfigField(
                        type = FieldSpec.Type.STRING,
                        name = "API_KEY",
                        value = apiKey,
                    )
                }
            }
        }
    }

    private fun Project.readLocalProperty(key: String): String? {
        val localPropertiesFile = rootProject.file("local.properties")
        if (!localPropertiesFile.exists()) return null
        return localPropertiesFile.inputStream().use { stream ->
            Properties().apply { load(stream) }.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private companion object {
        const val API_KEY_PROPERTY = "API_KEY"
    }
}

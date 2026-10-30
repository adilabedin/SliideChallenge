import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.versionString
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Lints every Kotlin source set with ktlint. Rules come from the root `.editorconfig`; violations that
 * predate the linter live in each module's `ktlint-baseline.xml`, so only new code has to be clean.
 * `ktlintCheck` is wired into `check`, so `./gradlew build` fails on new violations.
 */
class KtlintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<KtlintExtension> {
                version.set(libs.versionString("ktlint"))
                baseline.set(file("ktlint-baseline.xml"))
                filter {
                    exclude { it.file.path.contains("/build/") }
                }
            }
        }
    }
}

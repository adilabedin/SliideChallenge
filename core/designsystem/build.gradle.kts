plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.presentation)
        }
    }
}

compose.resources {
    packageOfResClass = "com.androidlearning.sliide_challenge.core.designsystem.resources"
}

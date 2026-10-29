plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            // Exposed so the design system and features can read the window size through DeviceConfiguration.
            api(libs.compose.material3.adaptive)
        }
    }
}

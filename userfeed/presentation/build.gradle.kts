plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.userfeed.domain)
            implementation(libs.navigationevent.compose)
        }
    }
}

compose.resources {
    packageOfResClass = "com.androidlearning.sliide_challenge.userfeed.presentation.resources"
}

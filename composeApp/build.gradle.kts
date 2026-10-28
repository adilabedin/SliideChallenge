plugins {
    alias(libs.plugins.convention.cmp.application)
    alias(libs.plugins.convention.buildkonfig)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.presentation)
            implementation(projects.core.designsystem)

            implementation(projects.userfeed.domain)
            implementation(projects.userfeed.data)
            implementation(projects.userfeed.database)
            implementation(projects.userfeed.presentation)
        }
    }
}

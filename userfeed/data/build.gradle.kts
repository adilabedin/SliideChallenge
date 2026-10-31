plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.userfeed.domain)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.androidx.workmanager)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

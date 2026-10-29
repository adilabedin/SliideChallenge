plugins {
    alias(libs.plugins.convention.kmp.library)
    alias(libs.plugins.convention.room)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}

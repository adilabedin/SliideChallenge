plugins {
    alias(libs.plugins.convention.android.application.compose)
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.koin.android)
}

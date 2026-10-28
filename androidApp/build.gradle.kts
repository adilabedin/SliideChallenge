plugins {
    alias(libs.plugins.convention.android.application.compose)
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.presentation)
    implementation(libs.koin.android)
    implementation(libs.androidx.core.splashscreen)
}

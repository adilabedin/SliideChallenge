plugins {
    alias(libs.plugins.convention.android.application.compose)
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.domain)
    implementation(projects.core.presentation)
    implementation(projects.userfeed.data)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.core.splashscreen)
}

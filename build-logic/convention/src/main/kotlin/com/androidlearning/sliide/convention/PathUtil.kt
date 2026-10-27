package com.androidlearning.sliide.convention

import org.gradle.api.Project

private const val BASE_PACKAGE = "com.androidlearning.sliide_challenge"

/** `:userfeed:data` -> `com.androidlearning.sliide_challenge.userfeed.data` */
fun Project.pathToPackageName(): String =
    BASE_PACKAGE + path.replace(':', '.').replace('-', '_').lowercase()

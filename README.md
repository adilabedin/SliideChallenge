This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

## Architecture notes

### Typed results & safe networking

- **`Result<D, E : Error>`** (`:core:domain`, `core.domain.util`) is the single success/failure type used across layers, with
  `map`, `onSuccess`, `onFailure` and `asEmptyResult` helpers. Expected failures never surface as exceptions.
- **`DataError`** splits into `Network` and `Local`. `Network.UNPROCESSABLE_ENTITY` was added for GoRest's **422**
  validation responses (e.g. "email has already been taken") so the Add User form can react specifically.
- **`HttpClient.get / post / delete`** (`:core:data`, `HttpClientExt.kt`) wrap Ktor calls in `safeCall`, which maps
  HTTP statuses and exceptions (timeouts, serialization, connectivity) to `Result<T, DataError.Network>`.
  Data sources stay one-liners: `httpClient.get<List<UserDto>>("users", mapOf("page" to page))`.
- **Only `expect`/`actual`:** `Throwable.toPlatformNetworkError()`. Connectivity failures are genuinely engine-specific:
  OkHttp throws `UnknownHostException`/`ConnectException`, while Darwin throws `DarwinHttpRequestException` carrying an
  `NSURLError` code. Everything else in the networking stack is common code.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
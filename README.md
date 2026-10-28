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

### Loading users (launch screen → feed)

- **Flow:** `App()` → `MainViewModel` → `MainRepository` → `MainApi` → Ktor `HttpClient`. There is no splash
  *route*: `App()` shows `SplashContent` while `MainViewModel.isLoading`, then crossfades to `NavigationRoot` (feed).
- **Android** keeps the system splash (`core-splashscreen`) up via `setKeepOnScreenCondition { isLoading }`;
  `MainActivity` passes its `MainViewModel` into `App()` so both observe the same state.
- **iOS** launch screens are static and can't be held, so `UILaunchScreen` (`LaunchBackground` + `LaunchLogo`, 160pt)
  is followed by `SplashContent`, which draws the same colour and logo at the same size — users see one continuous
  splash.
- **Contracts:** `User` and `MainRepository` live in `:core:domain`; `UserDto` (`@Serializable`), the `toDomain()`
  mapper, `MainApi`, `KtorMainApi` and `OfflineFirstMainRepository` live in `:core:data`, so serialization types never leak
  into domain or presentation.
- **Latest page instead of last page (deliberate deviation):** the brief asks for the *last* page of `/users`, but
  GoRest now lists users newest first, so the last page holds the oldest users and a user created from the app would
  never appear on it. We fetch page 1 (the latest users) in a single request instead: users you add land on that same
  page, so a refresh keeps them, and it saves the extra request that reading `X-Pagination-Pages` would need.
- **Offline-first:** Room is the single source of truth. `OfflineFirstMainRepository.loadUsers()` fetches the latest
  page and swaps it into the table with `UserDao.replaceAll()` (one transaction), so the table always holds exactly that
  page and needs no local pagination. `observeUsers()` only ever reads the database. A failed fetch leaves the cache
  untouched.
- **Startup:** `MainViewModel` only calls the network when the database is empty. With cached users it skips the
  fetch entirely and opens the feed; if the fetch fails, the feed opens on its empty state. `MainViewModel` exposes
  a single `isLoading: StateFlow<Boolean>` (no state class), set to `false` once startup is done.
- **Feed:** `UserFeedViewModel` observes `MainRepository.observeUsers()` and shows the cached list (name + email), or an
  empty-data state when the table is empty.

### Local database (`:core:database`)

- Room KMP database `SliideDatabase` (v1, schema exported to `core/database/schemas`) with a single table
  `UsersTable` (`UserEntity`, keyed by the GoRest id) and `UserDao` (`insertAll` with replace-on-conflict,
  transactional `replaceAll`, `getAll()` as a `Flow` ordered newest-first). Entity ↔ domain mappers live in `:core:data`.
- `DatabaseFactory.create(builder)` mirrors `HttpClientFactory.create(...)`: shared code sets the bundled SQLite driver
  and IO dispatcher. Only obtaining the builder is platform-specific (Android needs a `Context` for the file path; iOS
  uses the Documents directory).
- `UserDao` is tested against a real in-memory Room database on the iOS simulator target (no Android `Context` needed).

### Dependency injection

Koin modules live only in `:composeApp`, not one per Gradle module: `appModule()` holds app-wide singletons
(`HttpClient`, `SliideDatabase`, `UserDao`, API, repository, `MainViewModel`), and `userFeedModule()` wires the user
feed. Core and feature modules stay free of Koin, except presentation modules, which use `koinViewModel()`.

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
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
- **Last page:** `KtorMainApi.getLastPageUsers()` fetches page 1 to read the `x-pagination-pages` header, then requests
  that page (both with `per_page=20`). `HttpClient.getWithHeaders` is the header-preserving variant of `get`; it goes
  through the same `safeCall` status/exception mapping. See *Assumptions & trade-offs* for why it works this way.
- **Offline-first:** Room is the single source of truth. `OfflineFirstMainRepository.loadUsers()` fetches the last
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

- **Core is wired in the app.** `appModule()` in `:composeApp` registers the `HttpClient`, `SliideDatabase`, `UserDao`,
  API, repository and `MainViewModel` (which lives in `:core:presentation`). Core is fixed and `:composeApp` already
  depends on every core module, so per-layer core modules would add files without adding isolation. Two bindings could
  only live in the app anyway: the `HttpClient` needs `BuildKonfig.API_KEY`, and the Android database builder needs the
  app `Context`.
- **Features own their module.** `userFeedPresentationModule()` lives in `:userfeed:presentation`, so a feature can be
  added or removed without the app knowing its internals. `initKoin` only lists modules.
- Core data and database modules stay free of Koin.

## Assumptions & trade-offs

- **"Last page" means the final page of `/users`.** Confirmed with Sliide. GoRest lists users newest first, so the last
  page holds the oldest users.
- **Two requests to find it.** GoRest only reports the page count in the `x-pagination-pages` response header, and its
  `x-links-*` headers don't include a "last" link. So we fetch page 1, read the count, then fetch that page. A single
  request isn't possible without guessing.
- **`per_page=20`, sent on both requests.** The page count depends on the page size, so both calls must agree. GoRest
  accepts up to 100 and silently falls back to 10 above that. The trade-off: the last page holds `total % 20` users
  (anywhere from 1 to 20), so the feed can be short. We accept that because it's what "last page" literally means.
- **The dataset can change between the two requests.** GoRest is shared and constantly written to. If it shrinks, the
  requested page returns `[]` with the new count in the header; we retry that page once. A missing or unreadable count
  is reported as an error rather than guessing a page.
- **Created users are local only.** Confirmed with Sliide: other devices don't need to see them. A user created
  from the app (201) is stored in Room and shown at the top immediately; GoRest ids increase, and the feed sorts by
  `id DESC`.
- **Created users live in their own permanent table.** GoRest puts new users on page 1, so they never come back in
  the last page we fetch, and a refresh replaces `UsersTable`. `CreatedUsersTable` keeps every user created on this
  device (with a nullable `remoteId` until the POST succeeds) and is never cleared by a refresh or a sync; the feed
  shows it above the fetched page.
- **Pending syncs: one table per operation (create / update / delete).** Modelled on the Tasky project, but split
  because the three have different shapes: a pending create only needs the created row's `localId` (its payload stays
  on that row), a pending update stores the latest edit keyed by server id (upsert, so only the newest edit is sent),
  and a pending delete is just the server id. A row is removed only after its request succeeds. Editing or deleting a
  user that hasn't been POSTed yet changes or removes the created row in place, so nothing is queued for it. Pending
  updates/deletes are exposed as `Flow`s so the feed can overlay them on server rows a refresh would otherwise restore.
- **Schema stays at version 1 during development.** The app isn't released, so schema changes edit version 1 in
  place and require reinstalling the app instead of carrying migrations.
- **Startup fetches only when the cache is empty.** This keeps launch instant and offline-friendly, at the cost of
  showing a cached page until the user refreshes.

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
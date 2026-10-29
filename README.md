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
- **Feed:** `UserFeedViewModel` observes `UserFeedRepository.observeUsers()` and shows the list (name, email and sync
  status), or an empty-data state when there are no users.
- **Relative timestamp = last sync.** Each row reads "Synced 5 minutes ago", or "Not synced" while it has a create or
  edit waiting in the pending-sync queue. `users.lastSyncedAt` is stamped by a refresh and by a successful PATCH;
  `created_users.lastSyncedAt` by the 201 and by a successful PATCH. `FeedUser.lastSyncedAt` is null whenever a change
  is pending. `formatSyncStatus()` (shared, pure) turns it into text against a `Clock`, and the ViewModel re-reads the
  clock every minute while the feed is observed, so labels keep moving.

### Creating, editing and deleting users (`:userfeed`)

- **Contracts:** `FeedUser`, `FeedUserId` and `UserFeedRepository` live in `:userfeed:domain`. `UserFeedApi` /
  `KtorUserFeedApi` (`POST users`, `PATCH users/{id}`, `DELETE users/{id}`) and `OfflineFirstUserFeedRepository` live in
  `:userfeed:data`, wired by `userFeedDataModule()`. The DAOs stay in `:core:database` (one `SliideDatabase`).
- **`FeedUserId` is sealed (`Created(localId)` / `Remote(id)`).** Local row ids and GoRest ids are separate sequences
  and can collide, and the repository needs to know which table a row lives in. The list flattens it to a string key
  (`created-1`, `remote-1`) because lazy-list keys must be saveable on Android.
- **Local first, then the network.** Every change is written to Room before its request, so the feed (a `combine` of
  the created, cached and pending-sync tables) updates immediately. A create is always stored in `CreatedUsersTable`;
  on 201 its `remoteId` is recorded. If a request fails, the change stays local and is queued in the matching
  pending-sync table, and the error is returned so the UI can say it hasn't reached the server yet.
- **Unsynced creates never hit the network again.** Editing one updates its row (the pending create carries the
  edit); deleting one removes the row and its pending create. Once a created user has a `remoteId`, edits and deletes
  go to the server like any other user.
- **Queued edits never go stale.** A successful PATCH drops any older queued edit for that user, and deleting a user
  drops its queued edit whether or not the DELETE succeeds.

### Add / edit user form (`UserFormScreen`)

- **One screen, two modes.** The feed's extended FAB ("Add user", shrinking to an icon once the list scrolls)
  navigates to `UserFormRoute()`. `UserFormRoute(createdLocalId, remoteId)` maps to `UserFormMode.Create` or
  `UserFormMode.Edit(FeedUserId)`, which reaches `UserFormViewModel` through Koin's `parametersOf`. In edit mode the
  ViewModel prefills from the feed (`observeUsers().first()`), saves with `updateUser`, and closes if the user no longer
  exists. The ViewModel and its tests support editing; the feed doesn't have an entry point for it yet.
- **Validation lives in `:userfeed:domain` (`UserValidator`) as typed `Result`s** (`NameValidationError`,
  `EmailValidationError`), so it is pure, shared, and unit-tested on both JVM and iOS. Input is normalised first
  (trimmed, with name whitespace collapsed), and the normalised value is both what's validated and what's saved.
  - *Name:* 2 to 100 characters. It must start with a letter in any script (`\p{L}`), followed by letters, combining
    marks, spaces, apostrophes (`'` or `’`), hyphens and full stops. "José Núñez", "O'Brien", "Jean-Luc" and "李小龍"
    pass; digits, symbols and emoji don't.
  - *Email:* a practical subset of RFC 5322, with no quoted local parts or IP-literal domains. The local part is
    dot-separated runs of RFC "atext" characters (no leading, trailing or double dots, at most 64 characters). The
    domain has two or more labels of letters, digits and inner hyphens (at most 63 characters each), ending in an
    alphabetic TLD of 2+ letters. The whole address is at most 254 characters.
- **Real-time, but not hostile.** Both fields are re-validated on every keystroke. The submit button is enabled only
  while both are valid, and a check mark appears in each field as soon as it is valid. An error stays hidden until the
  user has typed in a field and left it, so a fresh form doesn't open covered in red. After that the error follows
  every keystroke.
- **The form waits for GoRest, then closes.** The row is in Room before the POST, so when the 201 arrives and the form
  pops, the new user is already at the top of the feed. The feed scrolls to the top when a created user appears, because
  a keyed lazy list would otherwise keep the old first row in view. Any failure other than 422 has already been queued
  for sync, so the form closes the same way and the row reads "Not synced".
- **A save can't be cut in half.** The repository writes locally, POSTs, then queues the change on failure. Cancelling
  between those steps would leave a local user that never syncs. So the ViewModel runs the save in
  `withContext(NonCancellable)`, and the close button is disabled while it runs.
- **The form keeps its fields across process death** by storing name and email in `SavedStateHandle`. A restored
  edit form keeps the user's typing instead of re-prefilling.

### Background sync (WorkManager on Android, BGTaskScheduler on iOS)

- **Every 30 minutes, only while online and only while something is pending.** `PendingSyncScheduling` (shared code)
  watches `observeHasPendingSyncs()` (one Room query over the three pending tables) and schedules the sync whenever
  changes start pending. Only a finished sync cancels it, and only once nothing is left, so nothing wakes up for an
  empty queue. The observer never cancels: the queue can empty mid-sync (deleting a created user whose POST is in
  flight), and cancelling then would stop the worker before it queues the follow-up DELETE. A change queued just as
  the cancel goes through reschedules it. A schedule left over from an earlier run with an empty queue runs once and
  cancels itself.
  `WorkManagerUserSyncScheduler` enqueues a unique 30-minute periodic `UserSyncWorker` with a `CONNECTED` network
  constraint and `KEEP`, so an app restart doesn't reset the period. The first run happens as soon as a network is
  available.
- **The sync logic is shared; only scheduling is per platform.** `UserFeedRepository.syncPendingChanges()` and
  `PendingSyncScheduling` live in common code and are unit-tested on both targets. `UserSyncScheduler` is a domain
  interface with a WorkManager implementation (Android) and a `BGTaskScheduler` one (iOS), each wired by a
  platform-specific `userFeedSyncModule()`.
- **iOS: background app refresh plus a sync whenever the app becomes active.** `BackgroundTaskUserSyncScheduler`
  books a `BGAppRefreshTaskRequest` for no earlier than 30 minutes ahead. iOS decides when it actually runs (based on
  battery and how often the app is used), never runs it after the user force-quits the app, and simulators don't run
  it at all. So `IosUserSync` also syncs on every `UIApplicationDidBecomeActiveNotification`, which includes launch.
  An iOS request is used up once it launches, so each background run books the next one first, then syncs. The task
  handler must be registered before launch finishes, so Swift's `iOSApp.init()` calls `startIosApp()`, which starts
  Koin (`MainViewController` no longer does). The identifier is listed in `Info.plist`
  (`BGTaskSchedulerPermittedIdentifiers`, `UIBackgroundModes: fetch`).
- **Syncs never overlap.** A foreground and a background sync can run at the same time on iOS, so
  `syncPendingChanges()` holds a `Mutex`; otherwise both would send the same queued changes.
- **Order: creates, then edits, then deletes.** Each item is sent on its own, so one failure doesn't block the rest.
  A user created offline and then edited or deleted while its POST was in flight gets that edit or delete queued
  straight after the 201, and it is sent in the same run.
- **A queued edit is cleared only if it is still the one that was sent** (matched on `updatedAt`), so a newer edit
  made during the PATCH is kept.
- **WorkManager is initialized by Koin** (`workManagerFactory()` in `SliideApplication`, with the default initializer
  removed in the manifest), so the worker gets the repository by constructor injection. The worker returns `failure()`
  rather than `retry()`: failed items stay queued, and WorkManager resets periodic work to its next period after a
  failure (checked in `WorkerWrapper`) instead of marking it failed.

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
- **Features own their module.** `userFeedDataModule()` and `userFeedPresentationModule()` live in `:userfeed:data` and
  `:userfeed:presentation`, so a feature can be
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
- **Creates send placeholder `gender` and `status`.** GoRest rejects a create without them (422), but the brief's form
  only has name and email, so `KtorUserFeedApi` sends `gender = "female"`, `status = "active"`. They are passed
  explicitly rather than as DTO defaults, because the JSON encoder omits properties left at their default value.
- **A failed sync keeps the change queued; only success removes it.** There are two exceptions:
  - A 404 on a queued edit or delete means the user is already gone from GoRest, so the change is dropped.
  - A 422 on a create (GoRest refuses the user, in practice because the email is already taken) can never succeed.
    So the created user is removed and recorded in `RejectedCreationsTable`, whether the 422 comes back on the first
    POST or in a later sync. The UI reads `observeRejectedCreations()` to show a snackbar and then calls
    `dismissRejectedCreation()`. The record is persisted because the rejection can arrive from a background sync
    while the app is closed. When the 422 comes back to the add-user form, the form shows "This email is already
    taken" inline and stays open, so the user can fix it in place. It then dismisses the rejection record it just
    caused, so the feed won't report it a second time.
  - A 422 on a queued edit is still retried every 30 minutes until the user edits or deletes that user again.
- **iOS sync timing is best-effort.** iOS can't guarantee a 30-minute cadence, which is why the app also syncs
  whenever it becomes active.
- **Schema stays at version 1 during development.** The app isn't released, so schema changes edit version 1 in
  place and require reinstalling the app instead of carrying migrations.
- **The timestamp is when the row last matched the server, not when the user was created.** GoRest returns no
  timestamps (`id`, `name`, `email`, `gender`, `status` only), so creation time is unknowable for fetched users. Sync
  time is honest offline: unsynced changes read "Not synced" instead of a misleading age. The cost: rows from one
  refresh all share the same time.
- **Startup fetches only when the cache is empty.** This keeps launch instant and offline-friendly, at the cost of
  showing a cached page until the user refreshes.
- **Tapping a user opens their detail screen; long-pressing opens a delete confirmation bottom sheet.** A bottom sheet
  rather than a dialog keeps the destructive choice within thumb reach, and the long press gives haptic feedback. The
  detail screen ("User") shows the saved details and edits them in place: the app bar's edit button turns the details
  into prefilled, validated fields, the title into "Edit User" and the button into Save. Back (or the close button)
  drops an unsaved edit before it leaves the screen, and Save stays disabled until something actually changed, so an
  untouched save never queues a pointless sync. The feed's own Edit mode still opens the full-screen form.
- **Delete from the detail screen goes back to the feed for Undo.** The detail screen's Delete button opens the same
  bottom sheet as the long press (it lives in `presentation/composables`, along with the avatar and validated fields,
  so any screen in the feature can use them). Confirming does not delete there: the Undo window belongs to
  `UserFeedViewModel`, so the confirmed id is handed to it (`OnDeleteRequested`) and the app returns to the feed, where
  the row is hidden and the Undo snackbar appears. One Undo path means one set of rules for when a delete is
  actually sent.
- **Undo works by not sending the delete yet, rather than by reversing it.** A delete that has reached GoRest (204)
  can't be undone: re-creating the user would give it a new id, and could even be refused (422). So "restore the local
  state before the action is finalized" is read literally. After you confirm, `UserFeedViewModel` only hides the row,
  which the keyed list animates out, and offers Undo for 5 seconds. When that time runs out it calls `deleteUser`,
  which removes the row from Room and sends the DELETE (queuing it if offline). Undo simply shows the row again, and no
  request is ever made. The timer belongs to the ViewModel, not the snackbar, so the delete still goes through if you
  open another screen meanwhile. Only one Undo is offered at a time, so a second delete sends the first one
  immediately. Trade-offs:
  - The 204 arrives after the undo window, not before the row disappears. The offline-first feed never waited for the
    network to update the UI anyway.
  - If the app is killed or the feed is closed during those 5 seconds, the delete is never sent and the user is still
    there next time. We accept that: losing a delete is recoverable, whereas a delete that can't be undone is not.

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
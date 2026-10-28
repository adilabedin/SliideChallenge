import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Must run before launch finishes: it registers the background sync task handler.
        AppStartupKt.startIosApp()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

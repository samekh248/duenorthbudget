# Quickstart: Onboarding and Instance Setup

Validation guide for [contracts/](contracts/). Implementation tasks belong in `tasks.md`.

## Prerequisites

- JDK 21 (bytecode target 17), Android SDK 36
- The JVM localhost Actual-shaped server from spec 004 (`LocalActualServer`), with the `needs-bootstrap` switches from [server-check.md](contracts/server-check.md)
- A phone or emulator for the manual runs, with the debug APK installed fresh (`adb uninstall` first)

## Checks

```bash
./gradlew ktlintCheck :core:budget:test :app:testDebugUnitTest :app:verifyRoborazziDebug
```

Record screenshots only after a deliberate visual change:

```bash
./gradlew :app:recordRoborazziDebug
```

## Scenarios

1. **Server path.** Fresh install, local server with one budget and password sign-in. Welcome → `on my Actual server` → type the host without `https://` → next → sign in shows the password field and `name on this phone` prefilled → sign in → pick the budget → home pivot with it open. Settings names the instance by its label as active. No more than five pages.
2. **Phone-only path, offline.** Airplane mode, fresh install. Welcome → `only on this phone`. The currency is the phone region's. Blank name + done shows `enter a name`. Name + done → home pivot under 30 s from launch.
3. **Five outcomes.** Point the address at, in turn: a closed port, an HTML page, a server with `bootstrapped: false`, a server with only `header` sign-in, and a server with neither endpoint. Each shows its own message, the address stays, and `try again` / `only on this phone` show.
4. **Browser sign-in.** Server with `openid` only. Sign in shows `sign in with browser` and no password. Finish in the Custom Tab → pick a budget. Repeat, but back out of the tab → sign in, not signed in.
5. **Resume.** On the address step type an address, leave the app, `adb shell am force-stop`, reopen → same step, same address. On sign in, type a password, force-stop, reopen → password empty.
6. **Encrypted file.** Pick an encrypted budget → budget password page. Back → file list, file not opened. Correct password → home.
7. **Empty server.** Sign in to a server with no files → `no budgets on this server` and `new budget`. Create → home; the budget appears in the server's file list after the background send. With the server stopped before create, the budget is editable and sync says `not sent yet`.
8. **Existing install.** Install the previous release, create a budget, update to this build → home pivot opens, no welcome.
9. **Fluidity.** On each page, taps show feedback within 100 ms and step transitions hold the frame rate (existing fluidity harness). A slow server (5 s delay) never freezes the page, and back cancels the check.

## Screenshots

Roborazzi light and dark for: welcome, address (empty, checking, each error), sign in (password, browser, both), pick a budget (list, empty, duplicate names), new budget with region currency.

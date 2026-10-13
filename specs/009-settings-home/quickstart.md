# Quickstart: Settings and Instance

Validation guide for the contracts in [contracts/](contracts/). Implementation tasks belong in `tasks.md`.

## Prerequisites

- JDK 21 (bytecode target 17)
- Android SDK 36 (`sdk.dir` in `local.properties` or `ANDROID_HOME`)
- For instance tests: JVM localhost Actual-shaped server (same harness as spec 004)
- Airplane mode / disabled network for offline scenarios

## Checks

From the repo root, after implementation:

```bash
./gradlew :core:budget:test :app:testDebugUnitTest :app:verifyRoborazziDebug
```

Record screenshots only after a deliberate visual change:

```bash
./gradlew :app:recordRoborazziDebug
```

## Scenarios

1. **Add instance.** With no server saved, open settings from the panorama bar. Add an address, sign in with password against the localhost server. Settings names that server as active. Edit a category amount offline; the edit appears without waiting on the server.
2. **Change instance.** Save a second server (second localhost port or stub). Make it active; confirmation names the server left and the one opening. Local budgets still open. Sign out; budgets remain; remote list is gone until sign-in.
3. **Offline instance choice.** Disable the network. Select the other saved server. Choice persists. Settings says the server has not been reached. Open budget still accepts an edit in under 100 ms.
4. **Budgets from settings.** With two local files, switch from settings; confirmation names both. Create with a blank name; nothing is created. Create with a name and currency; that file is open.
5. **Appearance and formatting.** Set theme to dark and a non-default accent; restart; panorama matches. Change date format and hide decimals; the next date and amount use the new choices before the finger lifts. Re-open the same sqlite in a fixture that reads Actual prefs; `dateFormat` and `hideFraction` match. Method shows envelope (or tracking) with no convert control.
6. **Export and recovery.** Export the open budget; unzip; confirm `metadata.json` name and that a known account and transaction exist in `db.sqlite`. Choose reset sync / rebuild / repair without confirming; file unchanged. Confirm rebuild; month figures match transactions. Set an encryption password; wrong password reveals no amount.
7. **OpenID (harness).** Localhost server advertises `openid`. Browser path returns a token via the app redirect; settings shows that instance active. Cancelled browser flow leaves the previous active instance alone.

## Expected results

- JVM tests cover instance migration from `serverAddress`, active switching without merging files, preference writes, export zip entries, and confirmed recovery.
- Compose tests cover `settings-screen` and absence of formatting rows when no budget is open.
- Light and dark screenshots show the settings hub with instance and appearance sections.
- Spec 004 sync conflict tests remain green; this feature does not change which copy wins.

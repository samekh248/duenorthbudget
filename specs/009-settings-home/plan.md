# Implementation Plan: Settings and Instance

**Branch**: `009-settings-home` | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-settings-home/spec.md`

## Summary

One Metro settings place gathers the active Actual server (instance), other saved servers, the budgets on the phone, appearance, the open file's formatting, and advanced recovery. Spec 008's settings door becomes that place. Theme and accent stay in `phone.json`. Formatting, experimental features, and budget method live in the open file's `preferences` table with Actual's keys so desktop Actual shows the same dates and amounts. Multiple servers are saved; exactly one is active; switching names the server left and the one opening and never merges local budgets. Sign-in keeps the password path and adds browser OpenID when the server requires it. Export, rebuild totals, repair splits, reset sync, and encryption password changes require confirmation where the spec says so, and all of them finish from the on-phone copy when the network is off (except reaching a remote server).

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Jetpack Compose BOM (foundation, animation, UI; no Material), existing Metro components in `:core:design`, `BudgetLibrary` / `SyncCoordinator` / `HttpActualTransport` in `:core:budget`, AndroidX Browser Custom Tabs for OpenID return, AndroidX Security for per-instance tokens

**Storage**: `phone.json` gains a saved-instance list and `activeInstanceId` (migrated from today's single `serverAddress`). Per-instance tokens live in the keystore (`actual.token.<instanceId>`). Open-budget formatting and experimental flags write Actual preference ids (`dateFormat`, `numberFormat`, `hideFraction`, `firstDayOfWeekIdx`, and a single experimental switch) into `db.sqlite` `preferences`, with pending sync messages when the file syncs. Export writes a zip of `db.sqlite` + `metadata.json`. No new Gradle module.

**Testing**: JUnit for instance list / active migration, offline preference writes, formatting applied to dates and amounts, export zip contents, confirmed versus cancelled recovery, encryption password set/change/remove, and OpenID redirect handshake against a localhost server. Robolectric Compose tests for the settings hub, instance switch confirmation, and formatting rows when no budget is open. Roborazzi light and dark screenshots of settings.

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: Theme, accent, and formatting taps update the painted values before the finger lifts (under 100 ms). Settings opens from the bar without a full-screen spinner. Sign-out finishes while a background sync is running without freezing the screen. Disk and HTTP stay on `Dispatchers.IO`.

**Constraints**: Offline-first: settings opens and phone preferences save with the network off. Instance and file choices made offline are kept and applied when the server can next be reached. Changing, removing, or signing out of an instance does not delete local budgets and does not merge files. Failed sign-in does not mark an instance active. Budget method is display-only. Bank-link setup, reports, and experimental feature *behavior* stay in `specs/010-actual-parity`. Sync conflict rules from spec 004 are unchanged.

**Scale/Scope**: One open budget. Zero or more saved instances; at most one active. Settings hub plus existing create/switch/appearance/server screens reorganized under it. Advanced actions only when a budget is open.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Preference and formatting writes update UI state on the main thread after a local sqlite/`phone.json` write on `Dispatchers.IO` that does not block the tap path's painted result. Sign-out and instance switch never show a full-screen spinner. OpenID uses a Custom Tab; return applies the token off the draw thread. | Pass |
| II. Metro 2 | Settings is a secondary screen with oversized lowercase title, flat fills, 12 dp gutter, 48 dp targets, light and dark, accent from phone prefs. No Material cards, ripple, or elevation. | Pass |
| III. The Same Budget as Actual | Formatting keys match Actual synced prefs. Export is Actual's zip shape. Method stays envelope or tracking. Sign-in and sync reuse the Actual HTTP contract; OpenID uses `loginMethod: "openid"`. | Pass |
| IV. One Budget at a Time | File switch and instance switch each name what is left and what opens. Local directories are never merged into a server file. | Pass |
| V. Test What the Person Can See | JVM tests for instance, formatting, export, recovery. Compose and screenshot checks for the settings place in light and dark. Offline edit still under 100 ms. | Pass |
| VI. Phone First | Work stays in `:core:budget` and `:app`. No new module for organization. | Pass |

Post-design re-check: the same gates hold. Multi-instance storage is a migration of the existing single `serverAddress`, not a second sync engine. No constitution violation needs a complexity exception.

## Project Structure

### Documentation (this feature)

```text
specs/009-settings-home/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── settings-screens.md
│   ├── instances.md
│   └── budget-preferences.md
└── tasks.md             # /speckit-tasks, not this command
```

### Source Code (repository root)

```text
core/budget/src/main/kotlin/app/duenorth/budget/core/
  Models.kt                 # PhoneSettings instances list; format prefs types
  BudgetLibrary.kt          # preference read/write; export zip; recovery ops
  MoneyFormat.kt / Models   # honor numberFormat + hideFraction
  ActualTransport.kt        # login-methods / OpenID login + callback token
  SyncCoordinator.kt        # multi-instance token + active address; reset sync
app/src/main/kotlin/app/duenorth/budget/
  SettingsScreens.kt        # settings hub, instance list, formatting, advanced
  ShellScreens.kt           # keep create/switch/appearance; remove stub door
  SyncScreens.kt            # sign-in reused from settings instance flow
  ShellViewModel.kt         # settings routes and offline-safe writes
  DueNorthApp.kt            # route wiring; Custom Tab return
```

**Structure Decision**: No new Gradle module. Instance and preference logic that must run on the JVM lives in `:core:budget`. Screens and Custom Tabs live in `:app`. Spec 008's `ShellRoute.Settings` stays; its body is replaced by the full hub.

## Complexity Tracking

No constitution violations.

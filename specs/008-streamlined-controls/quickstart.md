# Quickstart: Streamlined Metro Controls

Validation guide for the contracts in [contracts/](contracts/). Implementation tasks belong in `tasks.md`.

## Prerequisites

- JDK 21 (bytecode target 17)
- Android SDK 36 (`sdk.dir` in `local.properties` or `ANDROID_HOME`)
- A local budget. No server is required. Airplane mode is the interesting case.

## Checks

From the repo root, after implementation:

```bash
./gradlew :core:design:testDebugUnitTest :app:testDebugUnitTest :app:verifyRoborazziDebug
```

`:core:budget:test` should stay green. This feature does not change envelope math. Run it when a month snapshot is wired through `readShell`:

```bash
./gradlew :core:budget:test
```

Record screenshots only after a deliberate visual change:

```bash
./gradlew :app:recordRoborazziDebug
```

## Scenarios

1. **One month.** Open a budget on October with a known to-budget amount. Previous month shows September's name, year, and to-budget. Pick a month twelve months away from the list. Current calendar month returns in one tap. The review section shows the same month as the budget section. The budget list has no previous, next, hold, or manage word buttons.
2. **Before the finger lifts.** With the month window warm, previous month changes the `to-budget` node in the same click that selects it. Scroll the category list, change month during that scroll, and confirm the rows do not jump until the scroll ends.
3. **Pinned editor.** Open a category budget on October, then select November from the month control. The editor still saves against October. Back on the panorama, November is showing.
4. **Bar.** On the panorama, with labels collapsed, tap add, hold, categories, and settings. Each runs once. None of the circles are empty. On a register, tap add, transfer, reconcile, and import the same way. On a form, save writes and cancel leaves without writing. Settings is not on the form's bar.
5. **Title.** Swipe from one section to the next. The title moves by the width of "due", less than the section, and no letter of "budget" or "due north" is clipped. With animator scale 0, the section changes and the title translation stays 0. With one section, the title does not move.
6. **Offline.** Disable the network. Change month and run a bar action that writes (hold or add). Force a new process. The file still has that write. The month figures match the local file.

## Expected results

- JVM tests cover the ±24 window, cache-hit versus gesture-pending selection, pinned months, alternating title shift, and the route action lists.
- Compose tests cover tags `month-label`, `month-previous`, `app-bar-add`, and `app-bar-ellipsis`.
- Light and dark screenshots show the month control and a bar of drawn icons.

# Quickstart: Metro Budget Shell

## Prerequisites

- JDK 21 (bytecode target 17)
- Android SDK 36, build-tools 36.0.0 (`sdk.dir` in `local.properties` or `ANDROID_HOME`)

## Checks

From the repo root:

```bash
./gradlew :core:budget:test :core:design:testDebugUnitTest :app:testDebugUnitTest :app:verifyRoborazziDebug
```

`:core:budget:test` covers envelope and tracking figures, currency formatting, create, switch, and reopen. It does not draw.

`:core:design:testDebugUnitTest` covers accent contrast, title parallax, and press feedback.

`:app:testDebugUnitTest` covers the panorama copy, a long name beside a large amount, and a lazy list of 300 groups.

`verifyRoborazziDebug` compares light and dark screenshots. To record after a deliberate visual change:

```bash
./gradlew :app:recordRoborazziDebug
```

## What a passing run shows

- An empty budget's to-budget amount is zero.
- The October fixture's to-budget amount is `$1,800.00` and the Living group is `$500.00`.
- Switching files does not rewrite the file that was left.
- A reload during a gesture keeps the rows that were already on screen.

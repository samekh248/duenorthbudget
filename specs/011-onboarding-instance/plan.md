# Implementation Plan: Onboarding and Instance Setup

**Branch**: `011-onboarding-instance` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-onboarding-instance/spec.md`

## Summary

A fresh install opens on a Metro welcome page that asks where the budget lives, instead of today's fallback to "new budget". The server path asks for the address, runs one `ServerProbe` that classifies the server into five outcomes, asks only for the sign-in that server accepts (password, browser, or both), names the instance, and lists the server's budgets to open or a new budget to create and send. The phone-only path is today's create screen with the region's currency preselected. Progress lives in `onboarding.json` so leaving the app, a browser sign-in, or a force stop resumes on the same step, never with a password. Everything ends in the same result as spec 009's settings sign-in and spec 004's open and upload paths; no second sync or sign-in implementation. The welcome, address, sign-in and file-list frames are added to `docs/design/metro-mockups.svg` in light and dark.

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Jetpack Compose BOM (foundation, animation, UI; no Material), Metro components in `:core:design`, `BudgetLibrary` / `SyncCoordinator` / `HttpActualTransport` in `:core:budget`, AndroidX Browser Custom Tabs (already used for OpenID in spec 009). No new library.

**Storage**: New `onboarding.json` beside `phone.json` for `OnboardingProgress` (no secrets). `SavedInstance.label` in `phone.json` is now set. Tokens stay in the keystore as spec 009 stores them.

**Testing**: JUnit for `ServerAddress.parse`, every `ServerProbe` outcome against `LocalActualServer` (with new `needs-bootstrap` switches), progress save/restore and password exclusion, first-run gating for phones with and without budgets, and region currency fallback. Robolectric Compose tests for each step's navigation and back behavior, including OpenID return to the file list. Roborazzi light and dark screenshots for every onboarding page and error state.

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: Tap feedback under 100 ms on every page; turnstile between steps at the phone's frame rate. Probe, sign-in, download and progress writes on `Dispatchers.IO`. Cold start to the welcome page within the existing 1 s budget (the page needs only `phone.json`, the budget list and `onboarding.json`).

**Constraints**: Server path is offered, never forced; phone-only works with the network off. No full-screen spinner; checks are cancellable with back. Passwords never written to disk by the flow. No server bootstrap, account creation, or server settings (Principle VI). Existing installs with a budget never see the welcome page.

**Scale/Scope**: Four new routes (`Welcome`, `OnboardingAddress`, `OnboardingSignIn`, `OnboardingFiles`), reuse of `CreateBudgetScreen` and `BudgetPasswordScreen`, one new probe in core, eight new mockup frames.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Each step paints from view-model state; probe, sign-in, download and `onboarding.json` writes run on `Dispatchers.IO`. Checking shows progress dots under the field, not a full-screen spinner; back cancels. Welcome needs no network, so cold start stays under 1 s. | Pass |
| II. Metro 2 | Full pages (no flyouts), small uppercase title over oversized lowercase page title, primary action in the bottom app bar, phone back only, flat fills, 12 dp gutter, 48 dp targets, light and dark, turnstile and tilt with reduced-motion fallback. Frames added to `metro-mockups.svg`. | Pass |
| III. The Same Budget as Actual | Opening a server file and sending a new one reuse spec 004's paths. Sign-in uses Actual's own endpoints and methods. The probe only reads. A server sync stays optional: the phone-only path never asks about servers. | Pass |
| IV. One Budget at a Time | The flow ends with exactly one budget open and one active instance. It only runs when no budget is on the phone, so nothing is switched or merged. | Pass |
| V. Test What the Person Can See | Screenshot checks in light and dark for each page and error state; JVM tests for every probe outcome; fluidity harness covers step transitions and a slow server. | Pass |
| VI. Phone First, Nothing Extra | Phone layout only. No new Gradle module. Server administration (bootstrap) is explicitly not done; the not-set-up outcome tells the person to finish setup in Actual. | Pass |

Post-design re-check: the same gates hold. `ServerProbe` replaces the reachability check in settings rather than adding a parallel one, and onboarding calls the existing `connect` / `completeOpenId` / open / `uploadNew` paths. No constitution violation needs a complexity exception.

## Project Structure

### Documentation (this feature)

```text
specs/011-onboarding-instance/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── onboarding-screens.md
│   └── server-check.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks, not this command
```

### Source Code (repository root)

```text
core/budget/src/main/kotlin/app/duenorth/budget/core/
  ServerAddress.kt          # new: parse, candidates, secure flag, host
  ServerProbe.kt            # new: needs-bootstrap + login-methods → ServerCheck
  Onboarding.kt             # new: OnboardingStep, OnboardingProgress, store for onboarding.json
  ActualTransport.kt        # timeouts; login-methods accepts object entries
  SyncCoordinator.kt        # connect/completeOpenId take an optional label
  BudgetLibrary.kt          # onboarding.json path beside phone.json
core/budget/src/test/kotlin/app/duenorth/budget/core/
  LocalActualServer.kt      # needs-bootstrap switches, HTML mode
  ServerProbeTest.kt, ServerAddressTest.kt, OnboardingProgressTest.kt
app/src/main/kotlin/app/duenorth/budget/
  OnboardingScreens.kt      # new: welcome, address, sign in, file list
  ShellViewModelOnboarding.kt  # new: step state, probe job, resume, finish
  ShellViewModel.kt         # Welcome replaces the Create fallback; routes
  ShellScreens.kt           # CreateBudgetScreen default currency parameter
  DueNorthApp.kt            # route wiring; OpenID return target
app/src/test/kotlin/app/duenorth/budget/
  OnboardingContentTest.kt, OnboardingScreenshotTest.kt
docs/design/
  metro-mockups.svg         # onboarding rows, light and dark (added by this plan)
  README.md                 # mentions the onboarding frames
```

**Structure Decision**: No new Gradle module. Probe, address parsing and progress storage are plain JVM code in `:core:budget` so they are tested without Android. Screens and the step state machine live in `:app`, following the `ShellViewModel*.kt` split already used for months, rules and templates.

## Complexity Tracking

No constitution violations.

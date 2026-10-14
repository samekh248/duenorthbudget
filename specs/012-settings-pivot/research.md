# Research: Settings Pivot

All Technical Context items were known from the code; nothing was left as NEEDS CLARIFICATION. The decisions below settle how the spec maps onto what is already there.

## R1. Multi-view pattern: pivot

- **Decision**: Settings is one `MetroPivot` titled "SETTINGS" with five items.
- **Rationale**: The constitution (Principle II) says secondary screens that need tabs use a pivot, and keeps a panorama only for a hub that previews several areas. `MetroPivot` already exists, is tested on home, and handles looping, header taps, and reduced motion.
- **Alternatives considered**: Panorama (rejected by the constitution for this use). A Windows Phone style category list that drills into one page per category (more taps to reach anything, and a second page title per area). Keeping one page with collapsible sections (still a long scroll; no Metro precedent).

## R2. Knowing and restoring the selected item

- **Decision**: Add `onSelected: (Int) -> Unit` to `MetroPivot`, called when `pager.settledPage` changes. `ShellUiState.settingsPage: SettingsPage` holds the item; `SettingsScreen` maps it to an index in the current item list and passes it as `initialItem`. Items are keyed by `SettingsPage`, never by index, because format and advanced come and go.
- **Rationale**: Every sub-page (payees, encryption, confirm pages, add server) replaces the `Settings` route, which disposes the pivot. The view model is the only thing that outlives that. Reporting on settle, not on every scroll frame, avoids recomposing the shell during a swipe (Principle I).
- **Alternatives considered**: `rememberSaveable` inside the pivot (lost when the route changes). Hoisting a `PagerState` into `DueNorthApp` (leaks pager internals into the shell).

## R3. Scroll position inside an item

- **Decision**: Each item keeps its own `ScrollState`. `SettingsScreen` saves each item's scroll value into `ShellUiState.settingsScroll: Map<SettingsPage, Int>` when the route leaves `Settings`, and restores it on return.
- **Rationale**: US5 asks to return "scrolled where they were"; only the advanced item and large-font cases scroll at all, so the map is tiny.
- **Alternatives considered**: Not restoring scroll (spec asks for it).

## R4. Adding a server reuses onboarding with an origin

- **Decision**: Add `origin: OnboardingOrigin = FIRST_RUN` to `OnboardingProgress`. "add" on the server item calls a new `onboardingAddServer()` that starts progress with `origin = SETTINGS`, `step = ADDRESS`, a blank address, and routes to `OnboardingAddress`. Origin changes four places:
  - `onboardingBack()`: from the address page with `SETTINGS`, clear progress and return to `Settings` on the server item (instead of `Welcome`). It also no longer bails out when a budget is open, for settings origin.
  - `onboardingSignIn` / `finishBrowserSignIn`: on success with `SETTINGS` and a budget open, clear progress and return to `Settings` on the server item; with no budget open, continue to `OnboardingFiles` as first run does.
  - `restoreOnboarding()`: keep a `SETTINGS` progress even when the phone has budgets, so leaving the app mid-add resumes (spec edge case).
  - `completeOpenId`: the existing "onboarding" test (`openIdPendingAddress != null`) already covers settings origin; its non-onboarding branch remains for older callers.
- **Rationale**: The person asked that server selection follow onboarding. One set of pages means one set of messages for the five check results (SC-004) and the same resume and password-never-saved rules.
- **Alternatives considered**: Copying the address and sign-in pages into settings (two places to keep in step). Keeping `connectInstance` and the inline form (the clutter the spec removes).

## R5. "Budgets on this server" from settings

- **Decision**: The server item offers "server budgets" when signed in. With a budget open it opens the existing spec 004 `Server` page (open, replace, send this budget, sync now, conflicts). With no budget open it opens `OnboardingFiles`, as the add flow does. Spec FR-009 was amended to say this.
- **Rationale**: The `Server` page is the only place that offers send, sync now and conflicts, and today nothing in the UI navigates to it except sign-out and replace flows. The pick-a-budget page has none of those. Linking the existing page fixes that gap without a third list.
- **Alternatives considered**: Pick-a-budget page in both cases (loses send, sync and conflicts). Folding the server file list into the server item (makes it scroll and puts open/replace next to sign out).

## R6. Which server is active after "add"

- **Decision**: As in spec 009, a successful sign-in makes the new server active (`SyncCoordinator.connect` already calls `setActiveInstanceId`). When another server was active, the settings-origin sign-in page shows one line: "signing in makes this your server; <current> stays saved".
- **Rationale**: Principle IV wants switching to be explicit and to name what is left. Adding a server is already an explicit act; the line names the server left without inserting a confirm page into the onboarding path.
- **Alternatives considered**: Saving the new server inactive and asking the person to make it active (an extra step every time; differs from onboarding). Running the confirm-switch page after sign-in (the switch has already happened by then).

## R7. Forms become pages

- **Decision**:
  - New budget: "new" on the budget item pushes `ShellRoute.Create` with `CreateBudgetScreen` (name and currency, region currency preselected as spec 011 does). On success the new budget opens and the route returns to `Settings` on the budget item. This also fixes today's hard-coded `"USD"` in `createFromSettings`.
  - Encryption: one `EncryptionPasswordScreen(mode)` with `SET` (new password), `CHANGE` (current, replacement), `REMOVE` (current). Route `ShellRoute.EncryptionPassword(mode)`, app bar save and cancel, errors from `encryptionError`, success returns to the advanced item.
- **Rationale**: FR-013, and Metro's full pages instead of inline forms.
- **Alternatives considered**: Three encryption routes (same layout three times).

## R8. Removing a server

- **Decision**: "remove" on a saved server opens `ShellRoute.ConfirmRemoveInstance` naming the server and saying the budgets on the phone stay. Confirm calls the existing `removeInstance`.
- **Rationale**: FR-007 and the spec edge case; today removal is immediate.

## R9. App bar per item

- **Decision**: `ScreenBar.specsFor` takes the `SettingsPage` for `ShellRoute.Settings`:
  - budget: primary `new`; menu `payees`.
  - server: primary `add`, plus `server budgets` when signed in; menu `sign out` when a server is active.
  - format, appearance: no primary actions; the bar shows only the ellipsis.
  - advanced: primary `export`.
  Encryption page: `save`, `cancel`. Confirm pages keep their buttons.
- **Rationale**: FR-014 and Principle II (every screen has a bottom app bar). The bar follows the settled item, so it does not flicker mid-swipe.

## R10. What moves where

| Today (one page) | Item |
|---|---|
| instance (status, saved servers, address, password, sign in, browser, sign out) | server (fields replaced by "add" → onboarding pages) |
| budgets (list, name field, create) | budget (field replaced by "new" → create page) |
| appearance (theme, accent) | appearance |
| launcher icon | appearance |
| payees | budget (app bar menu and a row) |
| formatting (date, number, first day, hide decimals) | format |
| method | budget |
| rules (learn categories) | budget |
| experimental (experimental, goal templates) | advanced |
| ids | advanced |
| export | advanced (app bar) |
| encryption | advanced (status + actions → encryption page) |
| advanced (rebuild, repair, reset sync) | advanced |

The `ShellRoute.Appearance` route, `showAppearance()` and `AppearanceScreen` are not reached from any UI and duplicate the appearance section; they are removed.

## R11. Mockups

- **Decision**: Add a "settings" row pair to `docs/design/metro-mockups.svg`, light and dark: each of the five items, the encryption page, and the settings-origin sign-in page with the active-server line. Update `docs/design/README.md`.
- **Rationale**: Constitution Development Workflow and Principle II: budget screens are drawn there.

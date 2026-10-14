# Contract: Onboarding screens

Metro 2 secondary pages, drawn in `docs/design/metro-mockups.svg` (rows "onboarding", light and dark). Every page: white or pure black fill, small uppercase `DUE NORTH BUDGET` at the top, oversized lowercase title (header 52 light), 12 dp gutter, 48 dp targets, no Material cards, ripple, shadow or elevation. Primary action is a round outlined app bar button; the ellipsis expands its label. Back is the phone's back gesture. Turnstile between steps, tilt on press, both dropped under reduced motion.

Strings below are the shipped copy. Test tags are in `code`.

## Welcome — `ShellRoute.Welcome`, `onboarding-welcome`

- Title `welcome`. Subheader `where is your budget?`.
- Two rows, each a subheader with a caption, whole row tappable:
  - `on my Actual server` / `sync with a server you already run` → Address. Tag `welcome-server`.
  - `only on this phone` / `start here, add a server later in settings` → Create. Tag `welcome-phone`.
- App bar: ellipsis only. In debug builds with developer mode on, the menu adds `sample budgets` (the existing screen).
- Back closes the app. Nothing is created.

## Server address — `ShellRoute.OnboardingAddress`, `onboarding-address`

- Title `server`. Subheader `where does Actual run?`.
- Field `address`, tag `onboarding-address-field`, URL keyboard, no autocorrect. Prefilled from a saved instance when one exists and the field has never been edited.
- Caption under the field: `tried as https:// first` when no scheme is typed; `this connection is not encrypted` when the check chose `http://`.
- While checking: accent progress dots under the field and `checking {host}`; the field stays editable; changing the text cancels the check. Tag `onboarding-checking`.
- On a non-`Ready` outcome: the message from [data-model.md](../data-model.md#servercheck-outcome-of-serverprobecheck) in accent text (tag `onboarding-address-error`), and two outlined buttons: `try again` and `only on this phone`.
- App bar: `next` (chevron). Ignored while a check runs; a blank or unparseable address shows `enter the server's address`. Feedback within 100 ms of the press.

## Sign in — `ShellRoute.OnboardingSignIn`, `onboarding-signin`

- Title `sign in`. Subheader is the instance host. Caption `Actual server · password sign-in` or `Actual server · browser sign-in`.
- Field `name on this phone`, prefilled with the host. Tag `onboarding-label`.
- Password method: concealed field `password`, tag `onboarding-password`. App bar: `sign in` (chevron).
- Browser method only: outlined button `sign in with browser` opens a Custom Tab. App bar: none besides the ellipsis.
- Both methods allowed: password field and `sign in with browser` both show.
- Refused: `that sign-in was not accepted` (existing `SyncCopy.SIGN_IN_FAILED`), password cleared, label kept.
- Network lost: `the server could not be reached`, entries kept.

## Pick a budget — `ShellRoute.OnboardingFiles`, `onboarding-files`

- Title `pick a budget`. Subheader `on {label}` in secondary text.
- One row per server file (name as subheader, caption per [data-model.md](../data-model.md#remotefile-row-display-only)). Tap downloads and opens it through spec 004's open path; the row shows progress in place, no full-screen spinner. Tags `onboarding-file-{fileId}`.
- No files: body `no budgets on this server` (existing `SyncCopy.NO_SERVER_FILES`).
- Outlined button `new budget` → Create, with the server path kept so the budget is sent after creation. Tag `onboarding-new`.
- App bar: `refresh` (sync glyph).
- Encrypted file: the existing budget password page; back from it returns here and does not open the file.

## New budget — existing `CreateBudgetScreen`

- Unchanged layout; title `new budget`. Currency preselected from the phone's region (R9).
- In onboarding, the app bar keeps the existing `save` / `cancel` (cancel is back). A blank name shows `enter a name` and creates nothing. The name and currency are kept in progress while typed.
- Back returns to Welcome or Files, whichever led here.

## Finish

- Home pivot opens with the budget open. `onboarding.json` is deleted.
- Server path: settings lists the instance by its label as active. A budget created on the server path shows sync status `not sent yet` until the background send succeeds.

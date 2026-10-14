# Research: Onboarding and Instance Setup

Phase 0 output for [plan.md](plan.md). Each entry is Decision / Rationale / Alternatives.

## R1. Metro guidance for a first-run flow

- **Decision**: There is no Windows Phone "onboarding" guideline to copy. The flow is built from the Metro rules that do exist, plus Principle II:
  - Phone Metro has no flyouts, so every step is a full page ("Requirements for Microsoft account sign-in": Windows Phone apps use pages where Store apps use flyouts).
  - A page's primary action lives in the bottom application bar, where the thumb is; actions at the top are covered by the hand ("How to translate common design principles to Windows Phone", MSDN Magazine, Dec 2011).
  - The phone's back is the back button; no on-screen back control (same article).
  - Sign in at launch only when the app cannot work without it; otherwise defer until needed (Windows Runtime account sign-in guidance). Due North works without a server, so the server path is offered, never forced.
  - Show that the person is signed in and offer sign-out (same sign-in requirements page). Spec 009's settings already does this; the server path ends by naming the instance.
  - No carousel or feature tour. Windows Phone's own setup is a sequence of single-question pages, which is the shape used here.
- **Rationale**: The person asked for Metro guidance "if there are any". These are the parts with a source, and they agree with Principle II's app bar and pivot rules.
- **Alternatives**: A Material-style swipe carousel with dots (not Metro, and adds pages without asking anything). A single long form with address, password and file on one page (hides the server-specific sign-in choice until it is too late).

## R2. When the welcome page shows

- **Decision**: `ShellRoute.Welcome` replaces today's fallback to `ShellRoute.Create` in `ShellViewModel.apply()` when no budget is on the phone (`budgets.isEmpty()`), and when `onboarding.json` holds an unfinished step, the route resumes that step instead. A phone with any budget never sees it, even if `onboarding.json` is left over (it is deleted on read in that case). `CreateBudgetScreen` stays as the phone-only step and as the "new budget" action from settings.
- **Rationale**: FR-001 and SC-006. Gating on "has a budget" means existing installs skip onboarding with no migration flag. Gating on "has a saved instance" would hide onboarding from someone whose only budget was removed.
- **Alternatives**: A `firstRunDone` flag in `phone.json` (needs a migration, and gets out of step if budgets are removed). Showing welcome whenever no budget is *open* (a locked budget would bounce to welcome).

## R3. Server check: one probe, five outcomes

- **Decision**: A new `ServerProbe` in `:core:budget` runs once per address on `Dispatchers.IO` and returns one `ServerCheck` outcome (see [data-model.md](data-model.md)):
  1. `GET {address}/account/needs-bootstrap`. A JSON body with `data.bootstrapped` means an Actual sync server. `bootstrapped == false` means **not set up**. When present, `data.availableLoginMethods` / `data.loginMethod` give the sign-ins.
  2. If step 1 gives no login methods, `GET {address}/account/login-methods` (what `HttpActualTransport.loginMethods` already calls), accepting either string entries or `{ method, active }` objects.
  3. Classification: connection refused, DNS failure or timeout → **unreachable**; TLS handshake failure → **unreachable** with the "certificate not trusted" reason; a 2xx/4xx that is not Actual's JSON → **not Actual**; an Actual server whose responses lack both endpoints, or whose methods include none of `password` / `openid`, → **unsupported** (with the method names when that is the reason); otherwise **ready** with the methods.
  - The exact response fields are to be confirmed against the current Actual sync-server source when the task is implemented; the localhost test server in `LocalActualServer.kt` gains these two routes so the outcomes are tested.
  - Today's `loginMethods()` swallows every error and returns `["password"]`, so `probeReachability()` in `ShellViewModel` always reports reachable. Settings moves to `ServerProbe` too, which fixes that.
- **Rationale**: FR-003 asks for five distinct messages. Each needs a different signal, and one probe keeps the "next" press to a single wait.
- **Alternatives**: Try a password login and read the error (needs the password before the person knows what sign-in to give, and cannot tell "not Actual" from "down"). `GET /info` for version (informative but not needed to decide any of the five outcomes; may be added for the sign-in page's caption).

## R4. Address handling

- **Decision**: A new `ServerAddress.parse(raw)` in `:core:budget` trims spaces and trailing slashes, keeps a sub-path (`https://host/actual`), and returns the candidates to try: when the person typed no scheme, `https://` first, then `http://` only if the secure attempt cannot connect at all (not when it gets a certificate error). A plain `http://` result carries `secure = false`, and the address page shows "this connection is not encrypted" under the field. `SyncCoordinator.normalizeAddress` stays as the final check and is fed the chosen candidate.
- **Rationale**: US3 scenario 1 and the sub-path edge case. Many self-hosted Actual servers sit at a bare host name on a LAN; making people type `https://` fails today (`normalizeAddress` returns null and the screen says "could not be reached").
- **Alternatives**: Require the scheme (today's behavior, the main first-run failure). Fall back to `http://` after a certificate error (would quietly downgrade a misconfigured secure server).

## R5. Keeping progress across leave, back and process death

- **Decision**: `onboarding.json` beside `phone.json` holds `OnboardingProgress`: the step, the typed address, the last `ServerCheck` summary, the instance display name, the chosen file id, and the phone-only name and currency. It is written on `Dispatchers.IO` after each step change and each debounced field edit, and deleted when a budget opens. Passwords are never in it. The pending OpenID address (today only in memory as `openIdPendingAddress`) is stored there too, so a browser sign-in survives the app being stopped while the Custom Tab is open. Back moves one step with entries kept, because the view-model holds the same progress object.
- **Rationale**: FR-011 and US4. Leaving the app to look up an address, or to finish a browser sign-in, is the normal path, and Android may stop the app meanwhile.
- **Alternatives**: `SavedStateHandle` (survives process death only while the task is in recents, not after a force stop as SC-005's test does). Storing it in `phone.json` (mixes a short-lived flow into long-lived prefs).

## R6. Browser sign-in returns to onboarding

- **Decision**: The OpenID flow from spec 009 is reused. `completeOpenId` gains a return target: when onboarding started it, the result routes to `ShellRoute.OnboardingFiles` instead of `ShellRoute.Settings`. Backing out of the Custom Tab returns to the sign-in step, not signed in.
- **Rationale**: Acceptance scenario US1-4.
- **Alternatives**: A second OpenID implementation for onboarding (two code paths for one handshake).

## R7. Instance display name

- **Decision**: The sign-in page has a "name on this phone" field, prefilled with the address's host (without `www.` and port). It is saved as `SavedInstance.label`, which spec 009 already has but never sets from the UI. Settings shows `label ?: host`.
- **Rationale**: FR-005. The field already exists in the model, so this is copy and wiring, not a migration.
- **Alternatives**: Ask for a name on its own page (one more page against SC-001's five-page budget).

## R8. Two server budgets with the same name

- **Decision**: Actual's `list-user-files` returns `fileId`, `groupId`, `name` and `encryptKeyId`, but no last-changed time. When two files share a name, each row's caption adds the first six characters of its file id, and "encrypted" where `encryptKeyId` is set. The spec's edge case is amended to say "told apart by a short id" instead of "by when each was last changed".
- **Rationale**: The server gives no date to show. A short id is stable and matches what Actual's own file list can show.
- **Alternatives**: Download each file's metadata to read a date (a full download per row, against Principle I).

## R9. Currency preselection

- **Decision**: `CreateBudgetScreen` takes a default currency from `java.util.Currency.getInstance(Locale.getDefault())`. When that code is in `Currencies.all`, it is preselected and scrolled into view; otherwise USD stays, as today.
- **Rationale**: US2 scenario 2. No new data: `Currencies.all` is the existing list.
- **Alternatives**: Ask the person's country (another question). Use the SIM country (needs a permission on some devices).

## R10. Creating a budget on the server path

- **Decision**: "new budget" on the server's file list opens the same create step, then on done creates the local budget, sets the instance active (already true after sign-in), opens home, and starts spec 004's existing `uploadNew()` in the background. A failed send leaves the sync status at "not sent yet" and the budget editable.
- **Rationale**: US5 and FR-015, reusing the existing upload.
- **Alternatives**: Create the file on the server first (fails offline and blocks the first edit on the network, against Principle I).

## R11. Cancelling a slow check

- **Decision**: The probe and sign-in run in a view-model `Job`. Back cancels the job, and the connection has a 10 s connect and 15 s read timeout so a dead host ends on its own. While it runs, the page shows Metro progress dots under the field and the next button is disabled; the page itself never blocks input.
- **Rationale**: FR-010 and the slow-server edge case.
- **Alternatives**: A modal "connecting" page (a full-screen spinner, forbidden by Principle I).

# Quickstart: Settings Pivot

Validation guide for [contracts/settings-pivot.md](contracts/settings-pivot.md). Implementation tasks belong in `tasks.md`.

## Prerequisites

- JDK 21 (bytecode target 17), Android SDK 36
- `LocalActualServer` from spec 004 with the spec 011 probe switches, for the add-server runs
- A phone or emulator with the debug APK and one local budget

## Checks

```bash
./gradlew ktlintCheck :core:budget:test :app:testDebugUnitTest :app:verifyRoborazziDebug
```

Record screenshots only after a deliberate visual change:

```bash
./gradlew :app:recordRoborazziDebug
```

## Scenarios

1. **Pivot shape.** Budget open → home → settings. Title "SETTINGS", headers budget, server, format, appearance, advanced; budget item showing. Swipe left five times → back on budget (loop). Tap "advanced" → advanced item.
2. **No budget open.** Remove or close every budget, open settings → headers budget, server, appearance only.
3. **Two gestures (SC-001).** From opening settings reach: an accent swatch, the date format, export, reset sync, sign out. Each takes one swipe or header tap, then the control (or the app bar).
4. **No empty fields (SC-003).** Visit each item; no text field is visible on any of them.
5. **Add a server.** Server item → `add` → address page empty → local server address → next → sign in with `name on this phone` → sign in → back on the server item, new server listed and active. Repeat with a second server active: the sign-in page names the server that stays saved.
6. **Add-server errors match onboarding (SC-004).** Run the five spec 011 outcomes (closed port, HTML page, not set up, header-only sign-in, unsupported) from settings; messages match onboarding word for word.
7. **Back and resume.** In add server, back on the address page → server item, nothing saved. Type an address, force-stop, reopen → address page with the address; password empty on the sign-in page.
8. **Server budgets.** Signed in with a budget open → `server budgets` → server page with send, sync now, conflicts. With no budget open → pick-a-budget page.
9. **Forms as pages.** Budget item → `new` → create page with region currency → save → new budget open, settings on the budget item. Advanced → `set password` → page with one field → save → advanced shows "encrypted". `change` and `remove` ask for the right fields; back leaves the file unchanged.
10. **Risky actions.** Rebuild, repair, reset sync only on advanced; each opens its confirm page. Remove server asks first and says the phone's budgets stay.
11. **Return to item.** From advanced open export, the encryption page, and a confirm page; each returns to advanced, at the same scroll position. From home, reopen settings → budget item.
12. **Audit (SC-005).** Every control listed in research R10 is present in its item.
13. **Fluidity (SC-006).** Swipe through all items on a Pixel 6a class device with the existing fluidity harness; no visible dropped frames, tap feedback within 100 ms. With "remove animations" on, header taps switch at once.

## Screenshots

Roborazzi light and dark for: each of the five items (budget open), budget and server items with no budget open, encryption page in three modes, remove-server confirm, settings-origin sign-in with the active-server line. Before-and-after for the old settings page goes in the PR.

# Contract: Settings screens

Metro secondary screens. Flat fill, Selawik ramp, 12 dp gutter, 48 dp targets. Light and dark. No Material cards, ripple, shadow, or elevation.

## Settings hub

- Route: `ShellRoute.Settings`. Title text `settings`, lowercase. Test tag `settings-screen`.
- Reached from the panorama bar's settings action (spec 008).
- Sections, top to bottom:
  1. **Instance** — active server name or "not connected"; list of saved servers with exactly one marked active when any exist; actions to add, make active, remove, sign in, sign out. See [instances.md](instances.md).
  2. **Budgets** — local file names and which is open; create and switch reuse the existing confirmation that names the file opening and the file left.
  3. **Appearance** — theme and accent (phone prefs).
  4. **Payees** — opens the existing payees screen.
  5. When a budget is open: **Formatting**, **Method** (read-only), **Experimental**, **Ids**, **Export**, **Encryption**, **Advanced** (rebuild, repair, reset sync). Absent entirely when no budget is open.
- Opening settings with the network off still shows every phone-local section. Active instance shows that it has not been reached when a probe fails or has not run.

## Instance switch confirmation

- Shown before changing `activeInstanceId` from A to B.
- Copy names A as left and B as opening (same spirit as file switch: leave / open).
- Confirm updates active id only. Cancel leaves the previous active id.
- Does not open, download, or merge any budget file.

## Formatting

- Rows for date format, number format, first day of week, hide decimals.
- A tap updates the preference in the open file and the next painted date/amount uses it before the finger lifts (same frame as the state commit after the local write is applied to UI state).
- See [budget-preferences.md](budget-preferences.md).

## Advanced

- Rebuild totals, repair splits, and reset sync each open a confirmation. Until confirm, no write.
- Reset sync is disabled or explains itself when there is no active instance / no sync state.
- Export starts a share/save of the zip; works offline.
- Encryption set / change / remove use concealed fields; wrong password shows the existing "that password did not open this budget" style message and reveals no amount.

## Bar

- On the settings hub, the application bar does not repeat panorama budget actions. Back / system back returns to the previous route.
- Form-style sub-screens (password, confirm) keep save/cancel or confirm/stay as today.

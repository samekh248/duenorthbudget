# Research: Settings and Instance

Phase 0 output for [plan.md](plan.md). Each entry is Decision / Rationale / Alternatives.

## R1. Settings hub replaces the stub door

- **Decision**: `ShellRoute.Settings` (added in spec 008) becomes the one settings place. Its body lists sections in order: instance (active and saved servers), budgets on the phone, appearance, then — only when a budget is open — formatting, experimental features, budget method (read-only), ids, export, encryption, and advanced recovery. Payees stays a settings row that opens the existing payees screen so the 008 door does not lose a path. Budgets, appearance, and sync are no longer the only content of settings; they are rows or embedded sections inside this hub.
- **Rationale**: FR-001 requires one place. Spec 008 explicitly deferred instance switching and real settings content to 009. Keeping the same route avoids a second "settings" entry on the bar.
- **Alternatives**: A new top-level route beside the stub (two settings screens). Deep-link every section from the bar again (undoes 008).

## R2. Multiple instances in phone.json

- **Decision**: Replace the single `PhoneSettings.serverAddress` with `instances: List<SavedInstance>` and `activeInstanceId: String?`. Each `SavedInstance` has `id` (UUID), `address` (normalized URL), and optional `label` (defaults to the host). On first read, if `serverAddress` is set and `instances` is empty, migrate to one instance with that address and set it active. `SyncCoordinator` resolves the active address from `activeInstanceId` and stores the token under `actual.token.<instanceId>` in the secret store. Sign-out clears that instance's token and remote file list; it does not delete local budget directories. Removing a non-active instance drops its row and token. Removing the active instance signs out and clears `activeInstanceId`. Making another instance active shows a confirmation that names the server left and the server opening; local budgets are untouched and not merged into a file on the new server.
- **Rationale**: The spec requires more than one saved server and exactly one active. Spec 004 already proved one address + one token; this is that model with a list. Offline activation keeps the choice in `phone.json` and shows that the server has not been reached until a later successful call.
- **Alternatives**: One address forever (fails US2). Storing tokens in `phone.json` (forbidden by the constitution). Merging remote file lists across instances (two servers can share display names; FR edge case forbids treating them as one file).

## R3. Password and browser (OpenID) sign-in

- **Decision**: Before password login, call `GET /account/login-methods` (or `needs-bootstrap` when that is what the server exposes) to learn allowed methods. Password path stays `POST /account/login` with `loginMethod: "password"` as in spec 004. When the server allows or requires OpenID, offer "sign in with browser": `POST /account/login` with `loginMethod: "openid"` and a `returnUrl` that is an app deep link (`duenorth://oauth`). Open the returned `redirect_url` / `returnUrl` in a Custom Tab. The Actual server finishes OIDC and redirects to the return URL with the token; the app captures it, stores it on the instance being signed into, marks that instance active only after success, and returns to settings. A failed or cancelled browser flow leaves the previous active instance (if any) unchanged.
- **Rationale**: FR-002 requires both methods. Actual's client already posts OpenID login with a return URL and receives a redirect. Custom Tabs keep the person in a recognizable browser without embedding a full WebView stack.
- **Alternatives**: Password only (fails the browser acceptance scenario). Embedding a WebView (heavier, worse cookie/OIDC behavior). Treating OpenID as out of scope (spec lists it).

## R4. Budget formatting preferences

- **Decision**: For the open budget, settings writes Actual synced preference ids in `preferences`: `dateFormat` (e.g. `MM/dd/yyyy`, `dd/MM/yyyy`, `yyyy-MM-dd`), `numberFormat` (comma-dot / dot-comma / space-dot as Actual's string values), `hideFraction` (`"true"` / `"false"`), `firstDayOfWeekIdx` (`"0"`…`"6"`, Sunday = 0). Reads use the same keys; missing keys use today's phone defaults (US-style date, comma thousands + dot decimals, show fraction when the currency has decimals, week starts Sunday). `MoneyFormat.format` and register date display honor these prefs so the next painted date and amount update in the same state commit as the write. Writes go through `INSERT OR REPLACE` plus a pending sync message on `preferences` when the file has sync state, so desktop Actual sees the same values after sync. Theme and accent remain only in `phone.json`.
- **Rationale**: FR-008 and SC-004 require file-owned formatting that matches desktop Actual. Actual documents these keys via `getPreferences` / synced prefs.
- **Alternatives**: Storing formats in `phone.json` (would diverge from desktop). Inventing parallel keys (fails "same budget as Actual").

## R5. Method display and experimental switch

- **Decision**: Settings shows `preferences.budgetType` as "envelope" or "tracking" with no control that writes the other value. Experimental features are one on/off switch stored as preference `flags.experimental` (`"true"` / `"false"`, default absent = off). Turning it on does not change any budgeted amount. What experimental features do when on belongs to spec 010.
- **Rationale**: FR-009 and FR-010. A single switch matches the spec's wording; Actual's multiple flags can be introduced later under that umbrella without expanding this settings surface.
- **Alternatives**: A convert-method control (forbidden). Surfacing every Actual experimental flag now (spec 010 owns behavior; would clutter settings).

## R6. Export

- **Decision**: Export builds an Actual-shaped zip containing `db.sqlite` and `metadata.json` from the open budget directory. The share/save name is derived from the budget display name only (sanitized); it never includes the encryption password or the server password. Export runs on `Dispatchers.IO`, works offline, and does not require a signed-in instance. Optional post-process: omit any local-only cache tables if present; do not strip accounts or transactions.
- **Rationale**: SC-005 and Actual's `exportBudget` (zip of sqlite + metadata). Desktop Actual can open that zip.
- **Alternatives**: Uploading to the server as the only export path (fails offline). Dumping only CSV (not what Actual opens as a budget).

## R7. Recovery actions

- **Decision**: Three confirmed actions on the open budget:
  1. **Rebuild calculated totals** — clear in-memory month cache and any on-disk cache tables if present; re-read the shell from transactions so month figures match the phone copy. Works offline.
  2. **Repair split transactions** — fix orphaned split parents/children the way Actual's repair tool does (delete or re-link inconsistent split rows). Works offline. Requires confirmation; no write until confirmed.
  3. **Reset sync** — only meaningful with an active instance and a file that has sync state: clear `messages`, reset `sync_state` clock / `lastRemote` (keep `cloudFileId` / `groupId` so the file identity remains), then the next sync is a fresh since. Requires confirmation. Local accounts and transactions stay.
  Encryption: set / change / remove the budget password using existing `BudgetCrypto` test blob + keystore rules from spec 004. Wrong password reveals no amount. Password never written into name, notes, or categories.
- **Rationale**: FR-012–FR-014. Confirmation gates match the acceptance scenarios. Offline rebuild/repair match the edge case.
- **Alternatives**: Silent recovery (fails "unchanged until confirm"). Full server wipe as reset sync (destroys identity; Actual keeps the file and resets the message log).

## R8. What this spec does not change

- **Decision**: Envelope math, conflict resolution, bank-link setup, reports, and experimental feature behavior stay as earlier specs / 010 define them. Spec 004's server-wins rule when a local cell is not pending is unchanged. Creating a budget still requires a non-blank name and a currency; currency still cannot change later.
- **Rationale**: FR-016 and the assumptions section.
- **Alternatives**: Folding 010 into settings (out of scope).

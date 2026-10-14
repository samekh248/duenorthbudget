# Contract: Settings Pivot UI

The settings screen is a UI contract: what each item shows, its app bar, and the test tags tests rely on. Existing tags from spec 009 keep their names where the control survives.

## Screen

- `MetroPivot(title = "SETTINGS")`, root tag `settings-screen`.
- Headers in order: `budget`, `server`, `format`*, `appearance`, `advanced`* (* only while a budget is open). Header tags come from `MetroPivot` (`pivot-header-N`); each item's content root carries `settings-item-<page>` (for example `settings-item-server`).
- No `MetroField` is composed inside any `settings-item-*` (FR-013, SC-003).

## Items

### budget (`settings-item-budget`)

| Heading | Content | Tags |
|---|---|---|
| open budget | name (accent), method line: "envelope budget" or "tracking budget", read-only | `settings-open-budget`, `settings-method-value` |
| other budgets | one row per other budget on the phone; tap → confirm switch | `settings-budget-<id>` |
| payees | action row | `settings-payees` |
| rules | "learn categories" switch | `settings-learning` |

With no budget open: "no budget open" in place of the first group, and every budget is listed.

App bar: primary `new` (→ `Create`); menu `payees`.

### server (`settings-item-server`)

| Heading | Content | Tags |
|---|---|---|
| this phone syncs with | active name (accent) or "not connected"; address; status "signed in", "signed out", or "not reached" | `instance-status`, `instance-address-line` |
| saved servers | one row per other saved server; actions `make active`, `remove` | `instance-<id>`, `instance-activate-<id>`, `instance-remove-<id>` |

App bar: primary `add` (→ settings-origin `OnboardingAddress`), primary `server budgets` while signed in (→ `Server` or `OnboardingFiles`, research R5); menu `sign out` while a server is active (`instance-sign-out`).

### format (`settings-item-format`)

Date format, number format, first day of week, hide decimals: the spec 009 choice rows and tags (`settings-date-*`, `settings-number-*`, `settings-first-day-*`, `settings-hide-fraction`). App bar: ellipsis only.

### appearance (`settings-item-appearance`)

Theme (follow phone, light, dark), accent swatches, launcher icon. Tags unchanged from spec 009 and the launcher icon section (`settings-icon-*`). App bar: ellipsis only.

### advanced (`settings-item-advanced`)

In order:

1. experimental: experimental features switch, goal templates switch (`settings-experimental-toggle`, `settings-templates`).
2. export: one line saying what export makes; action in the app bar.
3. ids: `settings-budget-id`, `settings-cloud-id`.
4. encryption: "encrypted" or "not encrypted", then `set password` or `change password` and `remove password` buttons (`encryption-set`, `encryption-change`, `encryption-remove`), each → `EncryptionPassword(mode)`.
5. repair: `rebuild totals`, `repair splits`, `reset sync` (`settings-rebuild`, `settings-repair`, `settings-reset-sync`) → existing confirm (`confirm-advanced`).

App bar: primary `export` (`settings-export`).

## Sub-pages

### Encryption password (`encryption-screen`)

| Mode | Title | Fields |
|---|---|---|
| SET | "set password" | `encryption-password` |
| CHANGE | "change password" | `encryption-current`, `encryption-next` |
| REMOVE | "remove password" | `encryption-current` |

App bar `save`, `cancel`. Error from `encryptionError` under the fields. Success or back → advanced item.

### Confirm remove server (`confirm-remove-instance`)

Title "remove <name>", subheader "budgets on this phone stay". Buttons `remove`, `keep`. Either → server item.

### Add server (settings origin)

Same pages and messages as spec 011 [onboarding-screens.md](../../011-onboarding-instance/contracts/onboarding-screens.md), with these differences:

- Address field starts empty.
- Back on the address page → server item, progress cleared.
- Sign-in page shows, when another server is active, `signing in makes this your server; <current> stays saved` (`onboarding-replaces-active`).
- Success with a budget open → server item, new server listed and active. With no budget open → `OnboardingFiles`.

## Back behavior

| From | Back goes to |
|---|---|
| Any settings item | home |
| Settings sub-page (any above, plus `Create`, `Payees`, `Server`, confirm pages) | settings, same item |
| Onboarding address, settings origin | settings, server item |

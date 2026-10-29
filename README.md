# duenorthbudget

Metro-styled Android app for Actual Budget.

The look is **Metro 2** (the Due North Light Panorama). Fluidity outranks
the look: the screen has to stay smooth. The budget on the phone is an
Actual Budget file, not a separate product.

## Specs

Planning uses [GitHub Spec Kit](https://github.com/github/spec-kit).

| Read this | For |
|---|---|
| [Constitution](.specify/memory/constitution.md) | Fluidity first, Metro 2, the same budget as Actual |
| [Metro 2](docs/design/README.md) | Where the reference mockups live |
| [001 Metro budget shell](specs/001-metro-budget-shell/spec.md) | Home pivot of the month, accounts, and inbox (a panorama until 2026-10-13) |
| [002 Envelope month](specs/002-envelope-month/spec.md) | Assign, move, cover, and hold money |
| [003 Account register](specs/003-account-register/spec.md) | Transactions, splits, transfers, cards |
| [004 Actual sync](specs/004-actual-sync/spec.md) | Server, offline edits, encrypted files |
| [005 Schedules, rules, and payees](specs/005-schedules-rules/spec.md) | Due items, rules, and payee cleanup |
| [006 Reconcile and review](specs/006-reconcile-review/spec.md) | Statements, spending, net worth |
| [007 Import transactions](specs/007-import-transactions/spec.md) | Files and existing bank connections |
| [008 Streamlined controls](specs/008-streamlined-controls/spec.md) | Month selection, working icons, actions, title lag |
| [009 Settings and instance](specs/009-settings-home/spec.md) | Server choice, budget file, formatting, recovery |
| [010 Actual parity](specs/010-actual-parity/spec.md) | Remaining Actual behaviors on the same file |
| [011 Onboarding and instance setup](specs/011-onboarding-instance/spec.md) | First launch, server choice, first budget |
| [012 Settings pivot](specs/012-settings-pivot/spec.md) | Settings as a five-item pivot |
| [013 Transaction details](specs/013-transaction-details/spec.md) | The transaction form, category list and date picker |

Specs 001–013 are implemented on `main`. Feature quickstarts cover the shipped specs. Work wanted but not in a spec yet is in the [backlog](docs/backlog.md).

### Checks

Every pull request runs `.github/workflows/ci.yml`: ktlint, the core tests, the app tests with each screenshot compared to its baseline in `app/src/test/snapshots`, and a debug build. When a screen changes on purpose, re-record its baselines with `./gradlew :app:recordRoborazziDebug` and commit them with the change.

### Release signing

Install release builds on a phone that holds a real budget. Debug builds are signed with `app/debug.keystore`, which is public in this repository, so anyone could sign an app that installs over a debug build.

The release key is private and never committed. Point the build at its `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), or set `DUENORTH_KEYSTORE`, `DUENORTH_KEYSTORE_PASSWORD`, `DUENORTH_KEY_ALIAS` and `DUENORTH_KEY_PASSWORD`:

```sh
DUENORTH_SIGNING=/path/to/keystore.properties ./gradlew :app:assembleRelease
```

Without a key, `assembleRelease` leaves the APK unsigned. Keep a backup of the key: an update signed with any other key will not install over the app.

### Debug sample budgets

Debug builds expose **sample budgets** on the budgets screen. Pick one to create a filled Actual file on the phone (household, full tour, or wide month). Release builds hide the entry point.

Selawik is bundled under the SIL Open Font License (`core/design/src/main/assets/licenses/selawik_OFL.txt`).

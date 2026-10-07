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
| [001 Metro budget shell](specs/001-metro-budget-shell/spec.md) | Panorama of the month, accounts, and inbox |
| [002 Envelope month](specs/002-envelope-month/spec.md) | Assign, move, cover, and hold money |
| [003 Account register](specs/003-account-register/spec.md) | Transactions, splits, transfers, cards |
| [004 Actual sync](specs/004-actual-sync/spec.md) | Server, offline edits, encrypted files |
| [005 Schedules, rules, and payees](specs/005-schedules-rules/spec.md) | Due items, rules, and payee cleanup |
| [006 Reconcile and review](specs/006-reconcile-review/spec.md) | Statements, spending, net worth |
| [007 Import transactions](specs/007-import-transactions/spec.md) | Files and existing bank connections |

Spec 001 is implemented. The checks are in [quickstart.md](specs/001-metro-budget-shell/quickstart.md). Plan the next spec with `/speckit-plan`.

Selawik is bundled under the SIL Open Font License (`core/design/src/main/assets/licenses/selawik_OFL.txt`).

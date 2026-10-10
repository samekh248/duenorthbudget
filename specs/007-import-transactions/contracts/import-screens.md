# Contract: Import screens

## Register actions

- **import file**: opens system file picker; on success navigates to import preview.
- **fetch**: visible only when the account has `account_sync_source`; otherwise hidden.

## Import preview

Shows counts `adding N · skipping M`. Lazy list of rows with duplicate label. **confirm** / **cancel**.

Errors: unreadable file → Metro message `the file was not understood`; fetch failure → `fetch did not finish` (account unchanged).

## Import review

Header `review import`. Rows from the current batch only. Category picker inline or navigates to picker without reordering the list. **done** clears batch and pops to register.

## Fetch progress

Uses the same small sync progress as server sync, not full-screen.

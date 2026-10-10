# Quickstart: Import Transactions

1. Open an account register. Choose **import file** and pick a QIF export.
2. Preview shows new vs duplicate counts. Confirm adds only new rows.
3. Review opens automatically; pick a category on each row. The row stays under your finger.
4. **Done** returns to the register.
5. With a linked account (`account_sync_source` set), **fetch** shows the same preview after the small sync bar completes.

Run `./gradlew :core:budget:test --tests ImportBookTest`.

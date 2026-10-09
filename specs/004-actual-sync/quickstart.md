# Quickstart: Actual Sync

JVM tests in `core/budget` start a localhost server that speaks [actual-server.md](contracts/actual-server.md). No desktop Actual process is required.

1. Create a budget directory with spec 001's schema, including an income transaction so the month has a to-budget figure.
2. Register those sqlite bytes with the localhost server under a file id.
3. From a second, empty library, sign in, list files, and open the file. The panorama's to-budget matches the figure from step 1.
4. Stop the server. Open the same library again. The shell still reads.
5. Assign `25.00` while the server is stopped. Restart the library. The assignment is still in `zero_budgets`. Start the server and sync. A new library that downloads and syncs shows the same assignment.
6. While offline, assign a different amount on the server message log for the same `zero_budgets` id. Sync. The conflict list contains both amounts, and both message timestamps are still stored.
7. Register an encrypted file. A wrong password leaves every budget directory untouched. The password screen does not contain a category amount or the password text. The right password opens the month. With "ask each time" on, locking clears the in-memory key and the shell is hidden.
8. Upload a phone-only budget. The previous server file's bytes are unchanged. Uploading onto a chosen file changes that file only.

Screenshots: `./gradlew :app:recordRoborazziDebug` after a visual change, then `:app:verifyRoborazziDebug` (or the unit test task, which compares).

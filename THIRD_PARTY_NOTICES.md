# Third-party notices

Due North Budget ships, or is partly derived from, the work below. Each part stays under its own license. The app shows the same list, with each license's full text, under settings, general, **open source licenses** (`app/src/main/kotlin/app/duenorth/budget/LicensesScreen.kt`).

Due North Budget is an independent app for Actual Budget files and servers. It is not made or endorsed by the Actual Budget project or by Microsoft. "Windows" and "Selawik" are trademarks of Microsoft Corporation; "Actual Budget" belongs to its project.

| Component | Used for | Copyright | License | Full text |
|---|---|---|---|---|
| [Actual Budget](https://github.com/actualbudget/actual) | Budget logic in `core/budget` ported from Actual's source (accounts, month actions, month math, templates, schedules, cleanups) | Copyright James Long | MIT | below, and `app/src/main/assets/licenses/actual_MIT.txt` |
| [Selawik](https://github.com/microsoft/Selawik) | The app's typeface, unmodified | Copyright 2015 Microsoft Corporation, Reserved Font Name Selawik | SIL OFL 1.1 | `core/design/src/main/assets/licenses/selawik_OFL.txt` |
| Android Jetpack (Compose, Activity, Lifecycle, WorkManager, Browser, Security, Profile Installer) | App framework | The Android Open Source Project | Apache 2.0 | `app/src/main/assets/licenses/apache_2.0.txt` |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Language and libraries | JetBrains s.r.o. and contributors | Apache 2.0 | same |
| Tink | Encrypted settings (via AndroidX Security) | Google LLC | Apache 2.0 | same |
| Guava ListenableFuture, JSpecify | Transitive, from AndroidX | The Guava Authors; The JSpecify Authors | Apache 2.0 | same |

Test-only and tooling dependencies (JUnit, Robolectric, Roborazzi, sqlite-jdbc, ktlint, and the Node packages in `tools/parity-fixtures`, one of which is GPL-3.0) never ship in the app.

When a dependency is added to a release build, add it here and to `LicensesCopy.notices`.

## Actual Budget (MIT)

```
Copyright James Long

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

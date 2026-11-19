# Gemini and AppFunctions

Due North exposes a few actions through [Android AppFunctions](https://developer.android.com/ai/appfunctions),
so Gemini (or any assistant the system trusts) can read and change the open budget on Android 16 and up.

| Function | What it does |
|---|---|
| `getBudgetOverview` | To budget, overspent categories, inbox count, assistant findings, account names |
| `getCategoryBalance(categoryName)` | One category's available, budgeted and spent this month |
| `listCategoryBalances` | Every visible expense category with its balance |
| `getInbox(limit)` | Transactions waiting for a category, newest first |
| `addTransaction(payee, amount, isIncome, accountName, categoryName, date, note)` | Adds a transaction; with no category it waits in the inbox |

Names are matched forgivingly (any case, a unique start or part of the name). When a name fits none or several,
the error lists the candidates, so the assistant can ask the user which one they meant.

## Where the code is

- `core/budget/.../AgentActions.kt`: the work, in plain Kotlin, covered by `AgentActionsTest`.
- `app/.../AgentAppFunctionService.kt`: the `@AppFunctionServiceEntryPoint` service. KSP generates the
  concrete `AgentAppFunctionService` and the schema `agent_app_function_service.xml`.
- `app/src/main/res/xml/app_metadata.xml`: instructions the agent reads about the app as a whole.

The library needs compile SDK 37 and AGP 9.1+. The scripts keep the separate Kotlin plugin and the old DSL
(`android.builtInKotlin=false`, `android.newDsl=false` in `gradle.properties`).

## Trying it without Gemini

On an Android 16+ phone or emulator with a debug build installed and a budget open:

```sh
adb shell cmd app_function list-app-functions --package app.duenorth.budget

adb shell "cmd app_function execute-app-function --package app.duenorth.budget \
  --function 'app.duenorth.budget.BaseAgentAppFunctionService#getCategoryBalance' \
  --parameters '{\"categoryName\": \"food\"}'"

adb shell "cmd app_function execute-app-function --package app.duenorth.budget \
  --function 'app.duenorth.budget.BaseAgentAppFunctionService#addTransaction' \
  --parameters '{\"payee\": \"Market\", \"amount\": 12.5, \"categoryName\": \"food\"}'"
```

## Getting it into Gemini

Gemini only calls third-party AppFunctions for apps published as a **Connected App** in Google Connected Console,
which is a developer preview you apply for. The app must be on Google Play (an internal testing track is enough),
uploaded as an app bundle, and linked to an "Android MCP" connected app. Until it is published, only accounts on
its tester list see it, in Gemini under Settings & help > Connected Apps, where they connect it and then ask
things like "@Due North how much is left for groceries?". A sideloaded APK can be tested with `adb` as above but
won't be what Gemini uses: Gemini works with the bundle version submitted to the connected app.

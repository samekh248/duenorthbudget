# Contract: Screen bar

The bar is the bottom chrome already in `ShellChrome`. It is a column under the content, so the last list row scrolls clear of it. Buttons are round, outlined, at least 48 dp, and at least 8 dp apart. The ellipsis is the characters `•••`, tagged `app-bar-ellipsis`. It expands and collapses. It does not change the route.

## Icons

Every `AppGlyph` except the ellipsis draws a stroked icon in the foreground color. A test can render each glyph and must see ink inside the circle. `AppGlyph.More` is not a glyph.

Primary buttons are tagged `app-bar-{id}`. A tap on the circle runs that action once with the bar collapsed and once with it expanded. A tap on one circle does not run the neighbor. Secondary actions are tagged `app-bar-menu-{id}` and are present only while the bar is expanded.

## Home

Budget open, envelope file, primary ids in order: `add`, `hold`, `categories`, `settings`.

Budget open, tracking file: `add`, `categories`, `settings`. Hold is absent.

No budget open: `settings` only.

`add` opens add transaction. `hold` opens hold for next month. `categories` opens manage categories. `settings` opens the settings route.

There is no secondary action on Home.

## Register

Primary ids in order: `add`, `transfer`, `reconcile`, `import`.

Those four word buttons are not also drawn in the register body.

## Settings

Route title text is `settings`, tag `settings-screen`. Rows open the existing budgets, appearance, sync, and payees screens. This route does not add a server, switch instance, or switch budget by itself. Spec 009 replaces the contents.

Settings is not shown as a primary button on top of a form.

## Write forms

Primary ids: `save`, `cancel`. No other bar buttons.

| Route | Save | Cancel |
|---|---|---|
| Create | Create the budget when the name is valid. A blank name still shows `enter a name` and does not create a file. | Leave, no file created. |
| Assign | The existing assign write. | Pop, no assign. |
| Add transaction | The existing add write. | Pop, no transaction. |
| Edit transaction | The existing save. | Pop, no save. |
| Split | The existing save. | Pop, no save. |
| Transfer | The existing transfer write. | Pop, no transfer. |
| Hold month | The existing hold write. | Pop, no hold. |
| Category budget | The existing category save, using `pinnedMonth`. | Pop, no save. |
| Move category | The existing move, using `pinnedMonth`. | Pop, no move. |
| Import preview | The existing confirm. | The existing cancel, nothing imported. |
| Reconcile | The existing finish. | The existing cancel, reconciliation not finished. |
| Budget password | The existing unlock. | Leave, budget file unchanged. |

The in-body save and cancel buttons for these routes are not duplicated under the bar.

## Other routes

Panorama actions are absent. A confirmation keeps its existing confirm and stay actions as the buttons that apply. Screens with no bar action still show the ellipsis and no empty circle.

## Offline

Bar actions call the same local writes as the buttons they replace. They do not wait on sync. A write that already persisted in sqlite is still there after the process starts again.

# Metro 2

This app uses **Metro 2**, the Due North Light Panorama language.

It is the second Metro design for Due North. The first mockups were a dark
pivot with no panorama. On 2026-10-04 the chosen look became design C,
Light Panorama, and that is Metro 2 here. On 2026-10-13 the home screen
moved from that panorama to a pivot: a small "DUE NORTH BUDGET" title, a
header row (budget, accounts, inbox, reports) with the
selected header in the foreground and the rest dimmed, and each item at
full width with no peek. The open month is a dropdown at the top of the
budget item. The colors, type, app bar, and motion are unchanged. On
2026-10-16 reports became the last home pivot item, the hub that lists
every report (net worth, month review, cash flow, net worth by month,
spending); due moved to its own view under home `•••`.

Budget screens are drawn in [metro-mockups.svg](metro-mockups.svg): the
month panorama, accounts, the budgets list, and switching files, in light
and dark. The shipped shell matches those four frames, except that the
month now lists each group's categories under it (tap a category to
assign; there is no separate group page), held amount under the list, outlined
month/hold/file actions, and the same app bar. The first-run onboarding pages
(welcome, server address, sign in, pick a budget) are drawn in two more
rows, light and dark, for spec 011. Settings is a pivot (spec 012): a small
"SETTINGS" title over budget, server, format, appearance and advanced headers;
the budget, server and advanced items and the change-password page are drawn
in the last two rows, light and dark. Surfaces that are not drawn
here still follow the Due North Tasks reference,
`docs/design/metro-mockups.svg` in
[samekh248/duenorthtasks](https://github.com/samekh248/duenorthtasks).

Every question the app asks before a change (copy last month, delete a
category, remove a server, post a schedule twice) is one shared confirm page,
`ConfirmPage` in `app/.../ConfirmPage.kt`: the title as a question, a lowercase
line on what changes, and its two answers on the app bar in place of the page
underneath's. The yes answer is the action's own verb (replace, delete, post
anyway); the other is always keep.

A budget made on the phone opens on the budget item with a get started list
over its month (`GetStartedList` in `app/.../GetStartedScreens.kt`): add your
accounts, set up categories, assign this month's money, add a bill that
repeats. Each line opens the real page for that job and ticks itself once the
budget shows it done; the list goes for good when all four are ticked or the
person taps hide this list. Set up categories opens a starter set to trim
while the budget has no categories, and the categories page after that.

A page's own commands go on the app bar, never as body buttons: `PageBar` in
`app/.../PageBar.kt` puts a page's current commands there (a step, a draft),
and draws them as buttons only when the page stands alone in a preview. Body
buttons stay only where each acts on one item in a list, such as accept on an
inbox card.

A row's commands open from the row in `MetroContextMenu` (core/design): the
rest of the screen dims and a full-width panel of lowercase commands opens
under the row. The inbox, the categories page and saved servers use it.

A gray label over a group of fields or rows is `MetroSectionHeading`, a
caption on forms (`SectionSize.FORM`) and a group header on pages such as
settings (`SectionSize.PAGE`). A box being typed in uses the `fieldActive`,
`onFieldActive` and `fieldHint` colors, and warnings use the accent; the red
`error` color is for figures in the red only.

The rules that matter are in `.specify/memory/constitution.md` (Principles I
and II). Fluidity wins when a Metro motion would stutter.

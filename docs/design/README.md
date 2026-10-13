# Metro 2

This app uses **Metro 2**, the Due North Light Panorama language.

It is the second Metro design for Due North. The first mockups were a dark
pivot with no panorama. On 2026-10-04 the chosen look became design C,
Light Panorama, and that is Metro 2 here. On 2026-10-13 the home screen
moved from that panorama to a pivot: a small "DUE NORTH BUDGET" title, a
header row (budget, accounts, due, review, net worth, inbox) with the
selected header in the foreground and the rest dimmed, and each item at
full width with no peek. The open month is a dropdown at the top of the
budget item. The colors, type, app bar, and motion are unchanged.

Budget screens are drawn in [metro-mockups.svg](metro-mockups.svg): the
month panorama, accounts, the budgets list, and switching files, in light
and dark. The shipped shell matches those four frames, except that the
month now lists each group's categories under it (tap a category to
assign; there is no separate group page), held amount under the list, outlined
month/hold/file actions, and the same app bar. Surfaces that are not drawn
here still follow the Due North Tasks reference,
`docs/design/metro-mockups.svg` in
[samekh248/duenorthtasks](https://github.com/samekh248/duenorthtasks).

The rules that matter are in `.specify/memory/constitution.md` (Principles I
and II). Fluidity wins when a Metro motion would stutter.

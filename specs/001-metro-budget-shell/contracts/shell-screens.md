# Contract: Shell screens

No screen in this spec writes categories, assignments, or transactions. None of them perform network I/O. The app does not request the network permission.

## Panorama

- Title text is `budget`, lowercase, Selawik Light 118sp, tracking −4%. `due north` sits on the next line, directly under that title. The title stays fully on screen, including the descender. It translates by [PanoramaMotion](../../../core/design/src/main/kotlin/app/duenorth/budget/design/PanoramaMotion.kt): across the span from the first section to the last, the title moves 15% of its own width, which is less than one section moves, and never far enough to leave the screen.
- Sections, in order: `budget`, `accounts`, `inbox`. The next section peeks 40dp from the right, including on the last section (the sections loop). A fling snaps to a section. Tapping the visible header of a section selects it.
- `budget` shows the month (`october 2026`), the header label and amount from the file contract, and one row per expense group (name and available amount). No groups: `nothing to budget`.
- `accounts` groups `on budget` above `off budget`. No accounts: `no accounts`.
- `inbox` shows payee and amount. Empty: `nothing to categorize`.
- A row with a long name keeps the amount beside it; the two do not overlap. Large amounts wrap inside their own column.
- While the panorama or a section list is scrolling, a newer shell does not replace the one on screen.
- System animator scale 0: section changes still happen, with no snap animation. Numbers stay the ones from the file.

## Application bar

72dp chrome, round outlined buttons, ellipsis. Collapsed buttons: `budgets`, `appearance`. Expanded: those labels. Press tilts the row when motion is allowed, and always sets a pressed fill on the way down.

## Create

Name field and a currency list. Primary action `create budget`. Blank name shows `enter a name` and leaves the directory tree unchanged. Success opens that budget's panorama on the current month with header amount 0 and no accounts.

## Switch

The budgets screen lists files on the phone and marks the open one. Choosing another opens a confirmation that names the file being opened and the file left on the phone (`leave {name} on the phone`). Confirm changes the open id only. Cancel leaves the open file in place.

## Appearance

`follow phone`, `light`, `dark`, and the accent grid. The choice is still in `phone.json` after a new process reads it. Light is white with `#111111` text. Dark is `#000000` with white text. Accent text meets 4.5:1 on that background.

## Launch

If `openBudgetId` points at a directory, that panorama is the first screen. If it does not, the create screen is the first screen. The first frame may be content-shaped placeholders. It is not a blank page and not a full-screen spinner.

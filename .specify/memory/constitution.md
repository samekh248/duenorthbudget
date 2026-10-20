# Due North Budget Constitution

## Core Principles

### I. Fluid First (NON-NEGOTIABLE)

The budget screen MUST feel instant. Fluidity outranks every other principle,
including the Metro look. A correct screen that stutters fails. A simpler
motion that stays smooth is required when the full Metro motion cannot hold
the phone's frame rate.

- Every tap MUST show visible feedback within 100 ms (tilt, state change, or
  the result itself).
- Scrolling, pivot swipes, and page transitions MUST hold 60 fps, and the
  phone's full rate (90 or 120 fps) on high-refresh screens, with no dropped
  frames a person can see.
- Budget edits MUST appear on screen before the finger lifts. They MUST NOT
  wait for sync or for any network call.
- Sync, file, and network work MUST NOT run on the thread that draws the
  screen. A sync MUST NOT block input, MUST NOT show a full-screen spinner,
  and MUST NOT reorder or jump a list under the finger. Remote changes fade
  in place.
- The on-phone budget MUST appear within 1 second of a cold start on a
  mid-range phone (Pixel 6a class). Anything still loading uses content-shaped
  placeholders that fade in. Blank screens and jumping layouts are forbidden.
- Motion MUST respect the system "remove animations" setting. Reduced motion
  still gives immediate state changes.
- These budgets are tested. A change that breaks one is a failing build.

Rationale: the brief says performance of the experience comes before
everything else. A Metro surface that hesitates is the wrong product.

### II. Metro 2 Is the Look (NON-NEGOTIABLE)

Every screen MUST follow Metro 2, the Due North Light Panorama language, and
MUST yield to Principle I when the two conflict.

Metro 2 is the second Metro design for Due North, chosen for Due North Tasks
on 2026-10-04 as design C. Budget screens are drawn in
`docs/design/metro-mockups.svg`. Surfaces that are not drawn there still
follow Due North Tasks `docs/design/metro-mockups.svg` and the rules below.
It replaces the first mockups (a dark pivot with no panorama).

- The home screen is a pivot (changed from a panorama on 2026-10-13). A
  small uppercase "DUE NORTH BUDGET" title sits over a row of pivot
  headers. The selected header leads the row in the foreground color; the
  others follow in loop order, dimmed, and the row slides as the person
  swipes. Each item fills the full width with no peek. Tapping a header
  opens that item. Items loop and snap. A panorama is kept only for a
  hub-style landing page that previews several areas at once; the app has
  none today.
- Every page opened from a pivot keeps the Windows Phone 8.1 title area: a
  small uppercase line naming the home section it sits under (ACCOUNTS over
  "visa"), or SETTINGS when opened from settings, or DUE NORTH BUDGET when it
  sits under no section, over the oversized lowercase page title. Pivots carry
  their own small title, and top-level views (the lists the app bar opens,
  such as accounts, rules and payees) are an area themselves and get none.
- Secondary screens use a pivot where they need tabs. Every screen has a
  bottom application bar: round outlined icon buttons and an ellipsis (`•••`)
  that expands labels and a menu.
- Typography is the interface. Use a Segoe-style light and semilight sans
  (Selawik as the licensed stand-in). Page titles are oversized and lowercase.
  Pivot headers stay extra-large semilight. Content text sits one step
  under the phone-kit sizes: header 48 semilight, subheader 26 semilight,
  body 16, secondary text 14 semilight.
- Content over chrome: no drop shadows, no gradients, no rounded cards, no
  elevation, no Material ripple. Flat color fills only. The exceptions are
  tile relief and mica, which the person picks (below).
- Metro is the default style. Under settings, appearance, the person may
  instead pick tile relief, which is Metro with soft light and shade: groups
  and register days lift as square panels, to budget is a flat accent tile,
  and add is the accent button. It may use soft shadows. It keeps everything
  else here: square corners, flat accent fills, the type, the pivot, the
  title area, round app-bar buttons in their order, the motion, the accent,
  and 4.5:1 text. Its grey page moves the accent's text just far enough to
  keep 4.5:1. Each style is drawn in light and dark.
- The person may also pick mica, the Windows 11 look: a tinted page, rounded
  layer cards edged by a hairline for groups, days, fields and keys, a
  selector bar (smaller headers, the chosen one underlined in the accent) in
  place of the pivot's big headers, and an acrylic app bar and keypad that
  float above the page with a soft shadow. It keeps the type, the title area,
  flat accent fills, the motion, the accent and 4.5:1 text.
- Light (white, black text) and dark (pure black) MUST both exist. The
  app follows the phone's setting unless the person overrides it. One accent
  drives highlights and tiles. The default swatch is Windows Phone magenta
  (`#D80073`). Accent text is `#B0005E` on light and `#F0389A` on dark so it
  stays at 4.5:1. The person may pick another Windows Phone 8.1 accent,
  plus light orange and coral. A swatch that misses 4.5:1 keeps a separate
  text color.
- Motion is part of the language: turnstile page transitions, tilt on press,
  continuum (the tapped row flies into the next page), and slide-in list
  stagger. Skip or shorten a motion that would drop frames (Principle I).
- Text and controls align to a 12 dp left gutter and a 24 dp grid. Touch
  targets are at least 48 dp.

Rationale: a Metro skin on default Android widgets would fail the brief. The
look is the one already chosen for Due North, applied to a budget.

### III. The Same Budget as Actual

The phone works on an Actual Budget file. It is not a second budgeting
product.

- Envelope math, accounts, categories, and transactions MUST match what Actual
  shows for that file. The phone MUST NOT invent a parallel set of numbers.
- A budget file is either an envelope budget or a tracking budget. The phone
  MUST keep the file's mode. It MUST NOT convert one into the other.
- The screen reads only the copy of the budget on the phone. Every action
  works with no network.
- A server sync is optional. When a budget is connected to an Actual server,
  local changes are sent in the background. When the copies disagree and the
  local change is not still pending, the server copy wins. Conflicts MUST be
  visible, never silently dropped.
- The phone MUST NOT claim a feature the budget file cannot store.

Rationale: "based on Actual Budget" means the person's existing budget, not a
look-alike with different rules.

### IV. One Budget at a Time

The person works in one budget file at a time.

- Switching files MUST be an explicit action that says which file is opening
  and which is left on the phone.
- The phone MUST NOT merge two budget files.
- Only one server connection is active for the open file.

Rationale: two open budgets make every number ambiguous. Actual itself works
one file at a time.

### V. Test What the Person Can See

- Each Metro surface has a screenshot check in light and dark.
- Envelope math (to budget, available, cover, credit-card payment, rollover)
  has tests that do not draw the screen.
- Sync has tests for offline edits, a later connection, and a conflict.
- Fluidity budgets from Principle I run against the home pivot, a long account
  register, and a sync that arrives while the person is scrolling.

### VI. Phone First, Nothing Extra

- The phone layout ships before tablet, widget, or watch layouts.
- No module exists only for organization.
- The daily loop (see the month, move money, record spending, sync) is fluid:
  specs 001–009 shipped it and its Principle I budgets pass. Goal templates
  read from category notes and Actual's fixed reports (cash flow, net worth,
  spending) are therefore in scope.
- Custom reports and dashboards, and server administration, stay out of scope.

## Technical Constraints

- Android phone app. Custom Metro components, not the platform's default
  material widgets.
- The screen reads a local copy of the budget. Sync runs in the background.
- Secrets (server passwords, budget encryption keys, keystores) MUST NOT be
  committed.
- Money is shown in the budget's currency, with the budget's decimal places.

## Development Workflow

- Features follow Spec Kit in spec order: `/speckit-specify` (done for the
  initial split), then `/speckit-plan`, `/speckit-tasks`, and
  `/speckit-implement`. One planned feature at a time.
- Every change MUST pass build, lint, unit tests, screenshot checks, and the
  Principle I fluidity budgets.
- Every visual change includes before-and-after screenshots in light and dark.

## Governance

This constitution supersedes other practices in this repo. Amendments are a
change to this file that bumps the version and states the reason. MAJOR:
remove or redefine a principle. MINOR: add a principle or materially expand
one. PATCH: wording only. Plans and reviews MUST check these principles. A
violation is listed with a justification before the work proceeds.

**Version**: 1.5.0 | **Ratified**: 2026-10-06 | **Last Amended**: 2026-10-20

**Amendment 1.0.1**: Wording only. Budget screens now live in
`docs/design/metro-mockups.svg`. The principles are unchanged.

**Amendment 1.1.0**: Principle VI materially changed. Its gate, "until the
daily loop is fluid", has been met, so goal templates and Actual's fixed
reports move into scope for spec 010 (actual parity). Custom reports and server
administration remain out. Proposed in `specs/010-actual-parity/plan.md`
(Complexity Tracking).

**Amendment 1.2.0**: Principle on Metro 2 expanded. Pages opened from a pivot
now carry a small uppercase section title over the page title, so a person can
tell where the page sits. Chosen by the owner on 2026-10-15.

**Amendment 1.3.0**: Principle on Metro 2 expanded. Metro stays the default,
and the person may pick one of three relief styles (pressed metro, tile
relief, carved): Metro with soft shadows, keeping square corners and flat
fills. Chosen by the owner on 2026-10-20 from three mockups.

**Amendment 1.4.0**: Principle on Metro 2 expanded. The person may also pick
mica: rounded layer cards, a selector bar and floating acrylic bars. Chosen
by the owner on 2026-10-20 from three acrylic mockups.

**Amendment 1.5.0**: Principle on Metro 2 narrowed. Pressed metro and carved
are removed; the styles are metro, tile relief and mica. Chosen by the owner
on 2026-10-20 after trying them on a phone.

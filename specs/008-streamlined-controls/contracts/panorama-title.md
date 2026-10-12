# Contract: Panorama title lag

The title text stays `budget`. The subtitle stays `due north`. The type ramp stays Selawik Light 118 sp with −4% tracking, and the subtitle stays the subheader style, unless the fit rule below scales the title block.

## Shift

Let `page` be the unwrapped pager position (integer page plus offset fraction). Let `step` be the width in pixels of the word `due` drawn in the subtitle style.

- If animations are off, or the panorama has one section, the translation is 0.
- If the page index is even, the resting translation is 0. If it is odd, the resting translation is `-step`. A fractional page is the linear blend of the two resting values. The blend is continuous. Landing on a page does not add a second correction.

A swipe from one section to the next therefore changes the title by `step` at the ends of the swipe, which is the width of `due`, and by less than one section width. The section width is the pager page, which is the viewport minus the 40 dp peek.

One section does not scroll sideways. The title stays at translation 0.

## Fit

The title block's width is the wider of the title line and the subtitle line. Its rest inset is at least `step`. If inset plus block width would exceed the viewport, the block is scaled down from 118 sp until it fits. Letters are not clipped at rest or at `-step`. The title layer does not clip its children. The old clamp of the shift to the 12 dp gutter is not applied.

When the section snaps, the title is at that page's resting translation, fully on screen, including the subtitle.

## Unchanged shell motion

The next section still peeks 40 dp from the right. Sections still snap. Tapping a section header still selects it. A shell or review refresh that finishes during the swipe does not move the title or the rows. The title offset comes only from the pager position.

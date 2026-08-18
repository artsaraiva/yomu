# Yomu — Paper-mâché Visual System

**Status:** Defined (issue [#19](https://github.com/artsaraiva/yomu/issues/19)). Implementation is a follow-up; this document is the spec that implementation maps to.
**Roadmap:** [`docs/roadmap/phase-7-ui-ux-redesign.md`](../roadmap/phase-7-ui-ux-redesign.md)

A reading-focused visual and interaction language for Yomu, inspired by Paper Mario and paper craft. This document is concrete enough to implement the core surfaces without inventing per-screen styles.

## Scope and boundary

The visual system governs two things and deliberately excludes a third:

- **Governs — [Chrome](../../CONTEXT.md):** the Compose-rendered Home, History, and Settings screens.
- **Governs — [Overlay controls](../../CONTEXT.md):** the floating button, quick-settings popup, close zone, and status toast drawn over live manga. The paper language applies, but legibility over arbitrary artwork is a hard constraint that outranks any cosmetic choice.
- **Excludes — [Typeset bubble](../../CONTEXT.md):** the translated text laid into a bubble over the page. Its appearance answers only to readability against the artwork underneath (today dark ink on a light fill). The paper aesthetic never touches it, and this redesign does not change translation correctness or bubble legibility.

Paper is expressed **procedurally** — through warm colour, shape, border, and offset shadow — never through photographic paper-texture image assets. See [ADR-0011](../adr/0011-procedural-paper-texture.md).

## Colour

Two modes: **day paper** (the primary identity) and **night paper**. Default follows the system setting; the existing `settings.theme` field selects an explicit mode. Warm cream paper carries the base, warm dark-brown ink carries text, and two saturated "sticker" accents carry action and information.

### Token roles and values

| role | use | day paper | night paper |
|---|---|---|---|
| `paper` | base screen surface | `#F4E9D0` | `#23201B` |
| `paper-raised` | cards, popups, sheets | `#FBF4E4` | `#2E2A23` |
| `ink` | primary text | `#2E2A24` | `#EDE3CE` |
| `ink-muted` | secondary text, captions | `#6B6353` | `#A99E86` |
| `accent` | primary action, active state, hero | `#E4572E` | `#F0724A` |
| `on-accent` | text/icon on an accent fill | `#FFF8EC` | `#1C1915` |
| `secondary` | informational, low-frequency affordances | `#2A9D8F` | `#3FB8A8` |
| `edge` | borders, deckled lines, dividers | `#D8C7A5` | `#4A4235` |
| `success` | success state | `#3A7D44` | `#6FBF7B` |
| `error` | error state | `#C1352B` | `#F0776C` |
| `warn` | warning state | `#C67A00` | `#E7A83A` |

Two accents only. More fights the calm reading focus. The hero accent is a warm Paper-Mario vermillion; the secondary is a muted teal for informational, less-frequent affordances.

### Material3 ColorScheme mapping

| Material3 slot | token |
|---|---|
| `background` / `surface` | `paper` |
| `surfaceVariant` / `surfaceContainer*` | `paper-raised` |
| `onBackground` / `onSurface` | `ink` |
| `onSurfaceVariant` | `ink-muted` |
| `primary` | `accent` |
| `onPrimary` | `on-accent` |
| `secondary` / `tertiary` | `secondary` |
| `outline` / `outlineVariant` | `edge` |
| `error` | `error` |

### Contrast — WCAG AA

The bar is **AA**: **4.5:1** for body text, **3:1** for large text (≥18.66px bold / ≥24px), UI components, and any border that carries meaning.

- Body text is always `ink` on `paper`/`paper-raised`. `accent` is for large text, icons, and fills only — **never body-size text on paper**, where it does not reach 4.5:1.
- Implementation carries a computed contrast table for every foreground/background token pair, per mode. Any hex nudge to pass AA comes from that table, not from eyeballing.

## Typography

Legibility where text is dense; personality where it is cheap.

- **Body / UI / label:** the system font (Roboto). Honours the user's font-scale setting; never overridden with a fixed size that ignores it.
- **Display (titles + Home hero only):** one bundled **OFL-licensed** rounded, hand-lettered display face. It carries most of the paper personality across a handful of large headers. The implementation names the exact font and its license; if no suitable OFL face is found, it falls back to a bold-weight styled system face. The [typeset bubble](../../CONTEXT.md) never uses the display face.

### Type scale

| token | role | face |
|---|---|---|
| `display` | Home hero | display |
| `title` | screen + section titles | display |
| `body` | primary content | system |
| `label` | buttons, tabs, controls | system |
| `caption` | secondary / metadata | system |

## Surface and shape

Paper made concrete, procedurally (per [ADR-0011](../adr/0011-procedural-paper-texture.md)). Restrained stacked paper — not literal cutout collage.

- **Corners:** generous rounded radius on paper surfaces.
- **Border:** a thin `edge` (warm ink) stroke defines each surface.
- **Layering:** a soft offset drop shadow sells stacked paper. **Elevation encodes interactivity** (see states).
- **No tilt on chrome cards** — rotation fights scannability and text baselines. Any playful rotation is reserved for decorative, non-content elements.
- **Deckled / torn edge** is an accent used sparingly, not on every surface.

### Elevation levels

| level | surface | shadow |
|---|---|---|
| `page` | base `paper` background | none |
| `card` | content surfaces on a screen | soft offset |
| `popup` | quick-settings, dialogs, sheets | larger offset |
| `overlay-control` | elements over live manga | minimal — legibility first |

## Component states

Every interactive surface defines all five. States reuse colour tokens above — no new colours.

- **Loading:** paper skeleton blocks in muted `paper-raised` with a gentle shimmer, or a small paper-craft spinner for indeterminate waits.
- **Error:** an `error`-edged surface, a plain-language message, and exactly one retry action. Never a raw stack trace or code.
- **Empty:** a light paper motif plus a single primary action — never a dead end.
- **Success:** a brief `accent` confirmation (toast / checkmark) that self-dismisses.
- **Disabled:** desaturated paper, **shadow removed so the surface lies flat**, `ink-muted` label, non-interactive.

**Rule:** elevation encodes interactivity. Disabled surfaces drop their shadow and lie flat; pressed surfaces sink.

## Motion

Cheap, short, paper-physical.

- **Appearance:** a short settle with slight overshoot — paper dropping into place. ~150–250ms.
- **Popups:** scale + fade from their origin point.
- **Screen transitions:** a quick horizontal slide. A paper "fold" is reserved for the Home hero only.
- **`reduce-motion` is a hard requirement, not a nicety:** overshoot and fold collapse to a plain fade.
- **No continuous or looping animation anywhere in chrome** (battery + distraction). Overlay controls animate minimally — they sit over live artwork.

## Navigation

The real reading happens in the overlay, not the chrome, so the chrome is shallow management UI that gets out of the way.

- **Bottom paper-tab navigation**, three destinations: **Home**, **History**, **Settings** — the current structure, no IA change.
- **Home is dominated by one large "start" affordance** that gets the user into capture fast.
- The **active tab uses the hero `accent`.**
- Immersion / edge-to-edge behaviour of the *overlay itself* is out of scope here — it is not chrome.

## Supported screen sizes

- **Phone portrait is the design target** — a reading app used one-handed.
- On wider or foldable screens, chrome centers in a **capped-width content column** with paper margins rather than stretching line lengths.
- **Landscape is supported functionally** (usable, scrollable) but not separately art-directed.
- **Overlay controls keep safe-area / edge clearance** so they never land off-screen or under system bars.

## Accessibility summary

- Colour contrast meets **WCAG AA** (§ Colour), verified by a computed table per mode.
- Text honours the user's **font-scale** setting.
- **`reduce-motion`** collapses expressive motion to fades.
- State is never signalled by colour alone — error/success/disabled also carry shape, icon, or elevation cues.

## Acceptance mapping (issue #19)

- *A small visual system covers the core app surfaces and overlay controls* → § Scope, Colour, Typography, Surface, Navigation.
- *States for loading, error, empty, success, and disabled are defined* → § Component states.
- *Accessibility basics and supported screen-size behavior are explicit* → § Accessibility, § Supported screen sizes.
- *Concrete enough to implement without inventing per-screen styles* → tokens, type scale, elevation levels, and named states are all pinned to values.

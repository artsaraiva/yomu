# Paper token contrast

Computed using WCAG relative sRGB luminance. Rows are foregrounds; columns are backgrounds. Values are ratios to 1. Exact palette values are locked by `YomuColorsTest`.

## Allowed use

- Body and small labels: `ink` or `inkMuted` on `paper` / `paperRaised` (at least 4.5).
- Accent: icons and large text only (at least 3). Day `onAccent` on accent is 3.49, so small buttons keep ink labels on raised paper.
- Meaningful outlines: `inkMuted`, `accent`, or `error`. `edge` is decorative only: it cannot identify an interactive control by itself.
- `secondary` and `warn` icons need raised paper in day mode; their contrast on base paper falls below 3. Success uses an icon as well as colour.
- Secondary / tertiary filled controls use the night `onAccent` dark ink in both modes for body contrast. No extra colour is introduced.

## Platform mapping and typography

The current Compose BOM exposes Material3 `surfaceVariant`, mapped to raised paper. This version does not expose the later `surfaceContainer*` slots; no dependency upgrade is needed.

Display typography uses the specified bold system sans-serif fallback (extra-bold titles / Home hero). Body, labels, and captions keep the system face and scalable `sp` metrics. No external font or texture asset is shipped. The typeset bubble is unchanged.

The owning Phase 7 milestone was restored as #20 because the roadmap’s former #17 no longer existed.

## Day paper

| Foreground / background | paper | paperRaised | ink | inkMuted | accent | onAccent | secondary | edge | success | error | warn |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| paper | 1.00 | 1.10 | 11.82 | 4.93 | 3.05 | 1.14 | 2.76 | 1.38 | 4.15 | 4.57 | 2.82 |
| paperRaised | 1.10 | 1.00 | 13.01 | 5.42 | 3.36 | 1.04 | 3.03 | 1.52 | 4.57 | 5.03 | 3.10 |
| ink | 11.82 | 13.01 | 1.00 | 2.40 | 3.87 | 13.50 | 4.29 | 8.58 | 2.85 | 2.59 | 4.20 |
| inkMuted | 4.93 | 5.42 | 2.40 | 1.00 | 1.61 | 5.63 | 1.79 | 3.57 | 1.19 | 1.08 | 1.75 |
| accent | 3.05 | 3.36 | 3.87 | 1.61 | 1.00 | 3.49 | 1.11 | 2.22 | 1.36 | 1.50 | 1.08 |
| onAccent | 1.14 | 1.04 | 13.50 | 5.63 | 3.49 | 1.00 | 3.15 | 1.57 | 4.74 | 5.22 | 3.22 |
| secondary | 2.76 | 3.03 | 4.29 | 1.79 | 1.11 | 3.15 | 1.00 | 2.00 | 1.51 | 1.66 | 1.02 |
| edge | 1.38 | 1.52 | 8.58 | 3.57 | 2.22 | 1.57 | 2.00 | 1.00 | 3.01 | 3.31 | 2.04 |
| success | 4.15 | 4.57 | 2.85 | 1.19 | 1.36 | 4.74 | 1.51 | 3.01 | 1.00 | 1.10 | 1.47 |
| error | 4.57 | 5.03 | 2.59 | 1.08 | 1.50 | 5.22 | 1.66 | 3.31 | 1.10 | 1.00 | 1.62 |
| warn | 2.82 | 3.10 | 4.20 | 1.75 | 1.08 | 3.22 | 1.02 | 2.04 | 1.47 | 1.62 | 1.00 |

## Night paper

| Foreground / background | paper | paperRaised | ink | inkMuted | accent | onAccent | secondary | edge | success | error | warn |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| paper | 1.00 | 1.14 | 12.74 | 6.12 | 5.57 | 1.08 | 6.67 | 1.64 | 7.28 | 5.84 | 7.77 |
| paperRaised | 1.14 | 1.00 | 11.20 | 5.38 | 4.90 | 1.23 | 5.87 | 1.44 | 6.40 | 5.14 | 6.84 |
| ink | 12.74 | 11.20 | 1.00 | 2.08 | 2.29 | 13.74 | 1.91 | 7.77 | 1.75 | 2.18 | 1.64 |
| inkMuted | 6.12 | 5.38 | 2.08 | 1.00 | 1.10 | 6.61 | 1.09 | 3.73 | 1.19 | 1.05 | 1.27 |
| accent | 5.57 | 4.90 | 2.29 | 1.10 | 1.00 | 6.01 | 1.20 | 3.40 | 1.31 | 1.05 | 1.40 |
| onAccent | 1.08 | 1.23 | 13.74 | 6.61 | 6.01 | 1.00 | 7.20 | 1.77 | 7.85 | 6.30 | 8.39 |
| secondary | 6.67 | 5.87 | 1.91 | 1.09 | 1.20 | 7.20 | 1.00 | 4.07 | 1.09 | 1.14 | 1.17 |
| edge | 1.64 | 1.44 | 7.77 | 3.73 | 3.40 | 1.77 | 4.07 | 1.00 | 4.44 | 3.56 | 4.74 |
| success | 7.28 | 6.40 | 1.75 | 1.19 | 1.31 | 7.85 | 1.09 | 4.44 | 1.00 | 1.25 | 1.07 |
| error | 5.84 | 5.14 | 2.18 | 1.05 | 1.05 | 6.30 | 1.14 | 3.56 | 1.25 | 1.00 | 1.33 |
| warn | 7.77 | 6.84 | 1.64 | 1.27 | 1.40 | 8.39 | 1.17 | 4.74 | 1.07 | 1.33 | 1.00 |

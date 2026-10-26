# BlueMob design rules

One look on every screen. When adding UI, use these before inventing new values.

## Colours (`ui/theme/Color.kt`, `Theme.kt`)

| Use | Token |
|---|---|
| Text, backgrounds, lines | `MaterialTheme.colorScheme.*`, `Extra.ink2/ink3/sand/line` (follow light and dark) |
| Main action, links | `MaterialTheme.colorScheme.primary` (pine) |
| Brand headline cards, tab bar | `Gradients.brand()` (blue to green) |
| SOS, danger, rescues | `Palette.DangerDeep` solid, `Gradients.danger` for headline cards |
| Rewards: level, medals | `Gradients.celebrate`, medal colours in `Tier` |
| Charts | `Palette.BrandBlue`, `Success`, `Warning`, `Danger`, `Violet` |

No new hex colours in screens: add a token if one is truly missing.

## Shapes and spacing

- Corners: 12 dp (small, chips inside cards), 16 dp (buttons, tiles), 22 dp (cards), 28 dp (sheets), 50% (pills).
- Spacing: `Space.xs/sm/md/lg/xl` (4, 8, 12, 16, 24 dp). Screen side padding is 16 dp.

## Components (`ui/components`)

- `HeroCard(brush)`: the one headline card per screen (white text).
- `Segmented(options, picked)`: date ranges and filters.
- `Group` + `GroupLabel` + `SettingRow`: settings and lists.
- `Chip`, `Tag`: filters and small labels.
- `SubScreen(title)`: every screen above the tabs (heading, back button).

## Words

Plain words over technical ones; every new string also goes into `assets/i18n/hi.tsv`.

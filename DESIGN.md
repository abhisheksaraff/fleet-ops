# FleetOps design system

The window is a survey sheet read at night. Every visual choice below is defined once in
`src/main/java/com/fleetops/gui/Theme.java` (colours, fonts, FlatLaf defaults) and `Ui.java`
(buttons, headings, tabs); change them there, not at the call site.

## Principles

- **Mode:** operate. People come to find an incident, plan a rescue and watch it run. The world lends
  type, palette, density and one signature move; it never changes the layout or the controls.
- **Signature move:** the map is framed like a survey sheet. A neat line with graticule ticks and
  grid values, a scale bar, grid references on every incident, and the header's live readout of
  where the map is centred.
- **No cards.** The incident list is ruled rows. Panels are separated by hairlines, not boxes with
  shadows. Corners are square (3 px on controls).
- **Colour means something.** Ochre is the one thing to act on. Water blue is ready, moss green is
  live, sage is done, brick red is danger. Nothing is decorative.

## Colour

| Token | Hex | Use |
|---|---|---|
| `BG` | `#0A121A` | window ground, dividers |
| `PANEL` | `#0E1823` | sidebar, header, status bar |
| `RAISED` | `#15232F` | inputs, progress track |
| `BORDER` | `#2A3B4A` | every hairline |
| `TEXT` | `#EBE4D0` | paper cream, primary text |
| `MUTED` | `#A39F8E` | secondary text, coordinates |
| `ACCENT` | `#DDA94B` | primary button, selection, focus, planned |
| `INFO` | `#6FB3D2` | ready to plan, flood |
| `LIVE` | `#9CCB6B` | mission running, rescued |
| `DONE` | `#7A9A86` | finished |
| `DANGER` | `#E0624A` | abort, found survivors, critical |
| `CONSOLE_BG` | `#070D13` | console |

Text on `PANEL` meets 4.5:1 for `TEXT` and `MUTED`. Disaster types have their own symbol colours
(`IncidentList.typeColor`): flood blue, fire orange, earthquake tan, famine dry-grass.

## Type

All three faces are bundled in `src/main/resources/fonts` (SIL Open Font License).

- **Young Serif** for place names and headings: incident titles, section headings, map labels.
- **Work Sans** for the interface: buttons, tabs, body text.
- **IBM Plex Mono** for anything measured: grid references, readouts, the console.

Sizes: headings 16-21, titles 14.5-17, body 12-13, mono 10.5-11. No all-caps eyebrow labels.

## Components

- **Tabs** (`Ui.Tab`): plain text, bold with an ochre underline when selected. Used for the list
  filters and the layout presets.
- **Row** (incident list): a square symbol frame, serif title, mono grid reference, severity swatch,
  people, and the state as a swatch plus a mono word on the right. Selected rows get an ochre outline.
- **Title block** (mission card, plan dialog): ink ground with a double hairline frame.
- **Map markers:** a square sign with the disaster's symbol and a people tag in the severity colour;
  boundaries are dashed, the selected one is solid with corner ticks.
- **Banner:** drawn as rectangles, not text, so it looks the same with any font.

## Rules for new work

- Reach for a hairline before a box, a tab before a pill, a swatch before a badge.
- A new state or status gets a colour from the table above or none; do not add accents.
- Numbers, coordinates and units are mono. Names of places and incidents are serif.

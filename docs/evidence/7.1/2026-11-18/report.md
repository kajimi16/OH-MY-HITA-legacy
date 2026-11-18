# 7.1 UI evidence — 2026-11-18

> Exploratory only. Upstream is logged out and shows five tabs (资讯 hidden). Our shell intentionally keeps six tabs, so the dock is excluded from these ratios. This is not a formal acceptance baseline.

## Environment

- Device: `emulator-5554`
- Resolution: 1080×2400, density 420
- Font scale: 1.0, light theme
- Upstream: `cn.limpu.hita` 3.1.2 (`2026103101`)
- Ours: `io.github.kajimi16.ohmyhita` 0.7.1-dev (`1795005613`)

## Results

| Page | State | Content diff | Note |
|---|---|---:|---|
| 今日 | logged out, no courses | 5.358% | Header card structure/colors now close; remaining glyph/icon edge differences |
| 待办 | logged out, empty | 3.540% | Content nearly aligned; remaining text raster differences |

Crop: top 130 px / bottom 310 px, tolerance 10. The bottom crop removes the floating dock.

## Source of truth

- Screenshots: `upstream/`, `ours/`
- Heatmaps: `diff/`
- Hashes and exact commands metadata: `manifest.json`

## Remaining

- Repeat with a logged-in upstream (6 tabs) for a full-screen comparison.
- Compare the dock as its own region.
- Continue with 时间表, 助手, 资讯, 更多.

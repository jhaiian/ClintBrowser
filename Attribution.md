# Attribution

This document lists all third-party libraries, icons, assets, and acknowledgments used in Clint Browser.

---

## Libraries

### AndroidX
- **Author:** Google
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://developer.android.com/jetpack/androidx

### Material Components for Android
- **Author:** Google
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://github.com/material-components/material-components-android

### Markwon
- **Author:** Noties (Dimitry Ivanov)
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://github.com/noties/Markwon

### OkHttp
- **Author:** Square
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://github.com/square/okhttp

### SimpleMagic
- **Author:** j256 (Gray Watson)
- **License:** [ISC License](https://opensource.org/licenses/ISC)
- **URL:** https://github.com/j256/simplemagic

### AndroidSVG
- **Author:** BigBadaboom
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://github.com/BigBadaboom/androidsvg

### Kotlin Coroutines
- **Author:** JetBrains
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://github.com/Kotlin/kotlinx.coroutines

### adblock-rust
- **Author:** Brave Software
- **License:** [Mozilla Public License 2.0](https://www.mozilla.org/en-US/MPL/2.0/)
- **URL:** https://github.com/brave/adblock-rust
- Bundled as a native library via JNI (see `native/quiverguard-jni`) to power Quiver Guard's filter compiling and ad/tracker blocking.

---

## Bundled Resources & Derived Content

### uBlock Origin
- **Author:** Raymond Hill (gorhill)
- **License:** [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html)
- **URL:** https://github.com/gorhill/uBlock
- Not a linked library — `native/quiverguard-jni/src/bundled_resources.rs` is generated directly from uBlock Origin's redirect resource and scriptlet registries (`src/js/redirect-resources.js`, `src/js/resources/scriptlets.js`), so Quiver Guard can serve the same redirects/scriptlets since `adblock-rust` does not ship them itself.

---

## Icons & Assets

### Material Icons
- **Author:** Google
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://github.com/google/material-design-icons

### Unknown File Icon (`ic_file_other_24.xml`)
- **Author:** [Ant Design](https://github.com/ant-design/ant-design-icons)
- **License:** [MIT License](https://opensource.org/licenses/MIT)
- **Source:** Via [SVG Repo](https://www.svgrepo.com/)

---

## Acknowledgments

### Download System — Inspired by 1DM
The download management system in Clint Browser drew inspiration from the design and user experience of [1DM (1 Download Manager)](https://play.google.com/store/apps/details?id=idm.internet.download.manager) by Innobyte. Special credit to the 1DM team for their excellent download management approach, which influenced Clint's download features.

### Theme — Inspired by ytdlnis
The theming system in Clint Browser drew inspiration from [ytdlnis](https://github.com/deniscerri/ytdlnis) by Denis Cerri. Special credit to the ytdlnis project for its theme design and approach, which influenced Clint's look and feel.

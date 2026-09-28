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

```
Copyright Gray Watson

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that this permission
notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
```

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

### Bouncy Castle
- **Author:** The Legion of the Bouncy Castle Inc.
- **License:** [Bouncy Castle License](https://www.bouncycastle.org/licence.html)
- **URL:** https://www.bouncycastle.org
- Provides Argon2id key derivation, used to turn a backup password into the AES-256-GCM encryption key.

```
Copyright (c) 2000 - 2026 The Legion of the Bouncy Castle Inc.
(https://www.bouncycastle.org)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### CodeView
- **Author:** Amr Hesham (AmrDeveloper)
- **License:** [MIT License](https://opensource.org/licenses/MIT)
- **URL:** https://github.com/AmrDeveloper/CodeView
- Powers syntax highlighting and line numbers in the user script code editor.

```
MIT License

Copyright (c) 2020 - Present Amr Hesham

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### AndroidX Media3
- **Author:** Google
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://developer.android.com/guide/topics/media/media3
- Used (`media3-muxer`, `media3-common`) alongside the platform `MediaExtractor` to remux downloaded video and audio tracks into a single file, entirely in the JVM with no native code.

### ColorPicker Compose
- **Author:** Jaewoong Eum (skydoves)
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **URL:** https://github.com/skydoves/colorpicker-compose
- Powers the HSV color wheel and brightness slider in the custom color picker (`com.github.skydoves:colorpicker-compose`).

---

## Bundled Resources & Derived Content

### uBlock Origin
- **Author:** Raymond Hill (gorhill)
- **License:** [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html)
- **URL:** https://github.com/gorhill/uBlock
- Not a linked library — `native/quiverguard-jni/src/bundled_resources.rs` is generated directly from uBlock Origin's redirect resource and scriptlet registries (`src/js/redirect-resources.js`, `src/js/resources/scriptlets.js`), so Quiver Guard can serve the same redirects/scriptlets since `adblock-rust` does not ship them itself.

### StevenBlack/hosts
- **Author:** Steven Black and contributors
- **License:** [MIT License](https://opensource.org/licenses/MIT)
- **URL:** https://github.com/StevenBlack/hosts
- Special thanks — Website Blocker downloads its Social category host list from this project.

```
The MIT License (MIT)

Copyright © 2023 Steven Black

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

### The Block List Project
- **Author:** blocklistproject and contributors
- **License:** [The Unlicense](https://unlicense.org)
- **URL:** https://github.com/blocklistproject/Lists
- Special thanks — Website Blocker downloads most of its category host lists (abuse, ads, crypto, drugs, fraud, gambling, malware, phishing, piracy, porn, ransomware, redirect, scam, torrent, tracking) from this project.

### EasyList & EasyPrivacy
- **Author:** EasyList authors and contributors
- **License:** [GNU General Public License v3.0](https://easylist.to/pages/licence.html)
- **URL:** https://easylist.to
- Special thanks — Quiver Guard includes EasyList and EasyPrivacy as default filter lists.

### Fanboy's Annoyance List
- **Author:** Ryan Broadfoot (Fanboy) and contributors
- **License:** [GNU General Public License v3.0](https://easylist.to/pages/licence.html)
- **URL:** https://secure.fanboy.co.nz/fanboy-annoyance.txt
- Special thanks — Quiver Guard includes this as a default filter list.

### AdGuard Filters
- **Author:** AdGuard
- **License:** [GNU General Public License v3.0](https://github.com/AdguardTeam/AdguardFilters/blob/master/LICENSE)
- **URL:** https://github.com/AdguardTeam/AdguardFilters
- Special thanks — Quiver Guard includes the AdGuard Base, Mobile Ads, and Annoyances filters as default filter lists.

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

```
MIT LICENSE

Copyright (c) 2018-present Ant UED, https://xtech.antfin.com/

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

---

## Acknowledgments

### Download System — Inspired by 1DM
The download management system in Clint Browser drew inspiration from the design and user experience of [1DM (1 Download Manager)](https://play.google.com/store/apps/details?id=idm.internet.download.manager) by Innobyte. Special credit to the 1DM team for their excellent download management approach, which influenced Clint's download features.

### Theme — Inspired by ytdlnis
The theming system in Clint Browser drew inspiration from [ytdlnis](https://github.com/deniscerri/ytdlnis) by Denis Cerri. Special credit to the ytdlnis project for its theme design and approach, which influenced Clint's look and feel.

<div align="center">

<img src="docs/clint_logo.png" width="120" alt="Clint Browser logo" />

# Clint Browser

**Customizable Layered Internet Navigation Tool**

A privacy-focused Android browser built on Android WebView — no Google telemetry, no tracking, no compromises.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blueviolet.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-blueviolet.svg)](https://developer.android.com)
[![Stable](https://img.shields.io/badge/Stable-1.0.1-blueviolet.svg)](https://github.com/jhaiian/Clint-Browser/releases/tag/v1.0.1)
[![Beta](https://img.shields.io/badge/Beta-1.0.2--beta--2-blueviolet.svg)](https://github.com/jhaiian/Clint-Browser/releases/tag/v1.0.2-beta-2)
[![Open Source](https://img.shields.io/badge/Open%20Source-Yes-blueviolet.svg)](https://github.com/jhaiian/Clint-Browser)

</div>

---

## What is Clint?

Clint is a free, open-source Android browser that puts privacy control in your hands. It blocks trackers and analytics at the network level, supports DNS over HTTPS, isolates incognito sessions completely, and ships with zero Google telemetry baked in.

Built and maintained by **[@jhaiian](https://github.com/jhaiian)** — a solo developer from the Philippines 🇵🇭

---

## Screenshots

<div align="center">

<table>
  <tr>
    <td align="center"><img src="docs/screenshot1.jpg" width="180" alt="Welcome screen" /><br/><sub>Welcome</sub></td>
    <td align="center"><img src="docs/screenshot2.jpg" width="180" alt="Search engine setup" /><br/><sub>Search Engine Setup</sub></td>
    <td align="center"><img src="docs/screenshot3.jpg" width="180" alt="Secure DNS setup" /><br/><sub>Secure DNS</sub></td>
    <td align="center"><img src="docs/screenshot4.jpg" width="180" alt="Default browser setup" /><br/><sub>Default Browser</sub></td>
    <td align="center"><img src="docs/screenshot5.jpg" width="180" alt="Browsing" /><br/><sub>Browsing</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshot6.jpg" width="180" alt="Tab switcher" /><br/><sub>Tab Switcher</sub></td>
    <td align="center"><img src="docs/screenshot7.jpg" width="180" alt="Menu" /><br/><sub>Menu</sub></td>
    <td align="center"><img src="docs/screenshot8.jpg" width="180" alt="Settings" /><br/><sub>Settings</sub></td>
    <td align="center"><img src="docs/screenshot9.jpg" width="180" alt="Downloads" /><br/><sub>Downloads</sub></td>
    <td align="center"><img src="docs/screenshot10.jpg" width="180" alt="Bookmarks" /><br/><sub>Bookmarks</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshot11.jpg" width="180" alt="Choose Your Theme setup screen" /><br/><sub>Theme Setup</sub></td>
  </tr>
</table>

</div>

---

## Features

### 🌐 Browser Core
- Full multi-tab browsing with a bottom sheet tab switcher
- Incognito mode — no cookies, no cache, no history saved
- Pull-to-refresh with smart nested scroll detection
- Scroll-hide toolbar and navigation bar — bars slide away while scrolling and return on scroll up, configurable in Settings
- **Hide Status Bar** option for full-screen browsing (off by default)
- Desktop Mode toggle with JavaScript injection for improved site compatibility
- Address bar with select-all on focus
- Back, forward, refresh, and home navigation
- Intent support — links open installed apps (YouTube, Spotify, etc.) with a prompt to choose between opening externally or staying in-app
- **"Open in ___"** option in the menu (grayed out when no app is available)
- Full-screen video and media support

### 📎 Uploads
- Upload support for images, videos, audio, and recordings directly from the browser

### 🔖 Bookmarks
- Save any page with a single tap from the navigation bar
- Bookmark icon updates live to reflect the saved state of the current page
- View, open, and delete bookmarks from a dedicated screen
- All bookmarks are stored locally on your device — never synced or uploaded

### 🔒 Privacy & Security
- **Tracker blocking** — 16+ known analytics and ad domains blocked at the network level
- **Third-party cookie blocking** — prevents cross-site tracking
- **Generic User-Agent** — reduces browser fingerprinting
- **DNS over HTTPS (DoH)** — four protection levels with Cloudflare and Quad9 support
- **SSL enforcement** — invalid certificates are always rejected, no exceptions
- **Incognito isolation** — separate cookie, cache, and DNS context per incognito tab

### 🔍 Search Engines
- DuckDuckGo (default)
- Brave Search
- Google *(with privacy warning)*
- Changeable at any time from Settings → General

### ⬇️ Downloads
- Custom download engine built on OkHttp — not the system DownloadManager
- Real-time progress with percentage and file size
- Cancel downloads in-app or from the notification
- Open completed files directly from the downloads screen
- Install downloaded APK files directly from the downloads screen
- Automatic duplicate filename handling

### 🎨 Look & Feel
- Dedicated **Look & Feel** section in Settings for web appearance and display options
- **App Theme** selection with three built-in themes:
  - 🟣 **Default** — Deep purple, signature Clint style
  - 🌙 **Dark** — Dark background with white accents
  - ⚪ **Light** — Clean white background with dark accents
- Theme can also be chosen during the **Setup Wizard** (shown after the Terms of Service and Privacy Policy step)
- Scroll-hide bars and status bar visibility configurable independently

### 🔄 Updates
- In-app update checker for **Stable** and **Beta** channels
- Updates install directly in-app with a progress dialog
- Architecture-aware APK download links
- Optional check on launch, with an option to skip on metered/mobile data connections
- **View Changelog** option in update settings

### 🐛 Debug & Crash Reports
- Local crash log viewer — all data stays on your device, nothing is transmitted
- Auto-captures stack traces, device info, and timestamps on crash
- Copy crash logs and pre-filled GitHub issue template directly from the app
- Reports auto-deleted after 7 days

---

## DNS over HTTPS

Clint supports four DoH protection levels:

| Mode | Behavior |
|---|---|
| **Off** | System DNS resolver, no encryption |
| **Default** | Pre-resolves via your provider, falls back to system DNS if unavailable |
| **Increased** | Pre-resolves via your provider, minimal fallback |
| **Max** | Only your provider — DNS fails if the provider is unreachable |

**Providers:** Cloudflare (`1.1.1.1`) and Quad9 (`9.9.9.9`)

---

## Requirements

- Android 8.0 (API 26) or higher
- Android System WebView (pre-installed on all Android devices)

---

## Installation

Download the latest APK from the [Releases](https://github.com/jhaiian/Clint-Browser/releases) page.

Choose the APK for your device architecture:

| APK | Devices |
|---|---|
| `arm64-v8a` | Most modern Android phones (recommended) |
| `armeabi-v7a` | Older 32-bit ARM devices |
| `x86_64` | x86 64-bit devices and emulators |
| `x86` | x86 32-bit devices and emulators |
| `universal` | All architectures (larger file size) |

Not sure which to use? Install the **Universal** APK.
But right now, all the APKs don't have any differences because we haven't added the C++ library yet. However, the workflow is built that way so we won't have problems in future updates.

---

## Contributing

Contributions, bug reports, and feature requests are welcome.

1. [Open an issue](https://github.com/jhaiian/Clint-Browser/issues) to report a bug or suggest a feature
2. Fork the repo and create a branch for your change
3. Submit a pull request with a clear description

To report a crash, use the built-in **Debug & Crash Reports** screen in Settings. It generates a pre-filled GitHub issue template with your device info and crash log. For more detail about contributing guidelines, view [Contributing](https://github.com/jhaiian/Clint-Browser/blob/main/Contributing.md).

---

## License

Clint Browser is licensed under the **GNU General Public License v3.0**.

You are free to use, study, modify, and distribute this software under the same license.

See [LICENSE](LICENSE) for the full text.

---

## Support

If Clint is useful to you, consider supporting development:

- ☕ [Ko-fi](https://ko-fi.com/jhaiian)
- 💳 PayPal: jhaiianbetter@gmail.com

---

## Legal

- [Privacy Policy](PRIVACY_POLICY.md)
- [Terms of Service](TERMS_OF_SERVICE.md)

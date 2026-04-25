# Changelog

All notable changes to Clint Browser are documented here.

---

# v1.0.2

## 🎨 Look and Feel Updates

### Theme

- **Default** – Deep purple signature Clint style
- **Dark** – Dark background with white accents
- **Light** – Clean white background with dark accents

---

## 🎨 Accent Color

Select an accent color that changes backgrounds and UI accents in **Dark** and **Light** modes.  
In the **Default** theme, only accent elements (icons, dialogs, etc.) are tinted.

- **Default** – Standard theme colors
- **Material You** – Dynamic colors based on wallpaper
- **Purple** – Cool violet tones across the UI
- **Blue** – Warm blue tones across the UI
- **Yellow** – Warm golden tones across the UI
- **Red** – Bold crimson tones across the UI
- **Green** – Fresh forest tones across the UI
- **Orange** – Warm sunset tones across the UI

---

## 🌗 Surface Intensity

Controls how strong background and surface colors appear. Three modes are available:

- **Soft Tint** – Subtle background with a gentle tint
- **Strong Tint** – Deep dark surfaces with higher contrast
- **Pure Mode** – Pure black or pure white surfaces for maximum contrast

> ℹ️ **Availability**  
> - **Purple, Blue, Yellow, Red, Green, Orange**: All three modes.  
> - **Default Accent**: Only Soft Tint and Pure Mode.
> - **Material You**: Strong Tint is not available (system limitation).  
> - **Default theme**: Surface Intensity is not applied (preserves original design).


🔢 **Estimated ~52 possible theme combinations**

---

## 🧭 Address Bar Position

Choose the location of the address bar / toolbar:

- **Top** – Only the address bar is shown; navigation moves to the menu
- **Bottom** – Address bar placed at the bottom of the screen (navigation also moves to the menu)
- **Split** – Address bar at the top, navigation buttons below (classic style)

---

## 📋 Menu Style

Choose how the main menu appears:

- **Pop-up Menu** – Compact menu floating near the toolbar/address bar (classic style)
- **Bottom Sheet** – Full-width menu sliding up from the bottom (new style)

---

## 🔄 Nested Scroll (Reworked)

The old “Hide Bar on Scroll” setting has been reworked into **Nested Scroll**.

- **Off** – Bars always stay visible
- **Search Bar** – Address bar / toolbar hides when scrolling down
- **Navigation Bar** – Navigation buttons / bottom bar hide when scrolling down *(Split only)*
- **Both** – Both bars hide when scrolling down *(Split only)*

---

## 🗂️ Tab Menu Rework

- Normal tabs and Incognito tabs now clearly separated with headers
- Improved visual structure for easier navigation
- Better distinction between private and regular sessions

---

## 🖼️ Bookmark & Favicon System

- Added bookmark favicon system to the tab menu
- Tabs now display website favicons instead of generic icons

---

## 🌐 Favicon System (Reworked)

- Uses caching to avoid reloading each time
- Reduces network requests for faster loading
- **Removed Google favicon service** due to privacy concerns
- Primary source: website favicon
- Fallback: DuckDuckGo favicon service

---

## ❤️ About Page Updates

- Added Patreon link
- Added PayPal link
- Added subreddit link
- Updated contact information

---

## 🔗 Repository Update

- Renamed repository to `ClintBrowser`
- Previous URL: `https://github.com/jhaiian/Clint-Browser`
- New URL: `https://github.com/jhaiian/ClintBrowser`
- All GitHub links updated; the old link still redirects for backward compatibility

---

## ⚙️ Improvements

- Improved desktop mode JavaScript (thanks @Vonjoo [#3](https://github.com/jhaiian/ClintBrowser/pull/3))
- Improved desktop mode HTTPS request headers
- Improved overall update and download stability
- Reworked search engine dialog in General Settings using Material Cards (matches setup screen)
- Replaced default Android Toast with a custom toast system
- Toasts now match all available app themes for a consistent UI experience

---

## 🔧 Dependency Updates

- **Android Gradle Plugin** – 8.6.0 → 9.2.0
- **Kotlin** – 2.0.0 → 2.3.10
- **Gradle Wrapper** – 8.7 → 9.4.1
- **core-ktx** – 1.16.0 → 1.18.0
- **appcompat** – 1.7.0 → 1.7.1
- **webkit** – 1.13.0 → 1.15.0
- **swiperefreshlayout** – 1.1.0 → 1.2.0
- **okhttp + dnsoverhttps** – 4.12.0 → 5.3.0

---

## 🐞 Fixes

- Fixed all bugs and issues in notification downloads
- Fixed Builder.yml not recognizing CHANGELOG.md format
- Fixed changelog reader cutting off content
- Fixed beta enrollment not checking stable releases
- Fixed app restarting after canceled status bar changes
- Fixed cancel behavior in Hide Status Bar dialog
- Fixed incorrect mobile detection in desktop mode
- Fixed both bars blocking WebView content
- Fixed update checker on lower Android versions
- Fixed status bar issues on lower Android versions
- Fixed Debug & Crash Reports crashing on lower Android versions
- Fixed downloads failing on some sites (missing headers/data)
- Fixed incorrect download file extensions
- Fixed About page crashing on lower Android versions
- Fixed issue where some icons did not properly follow the selected theme
- Fixed memory leak in DNS-over-HTTPS (DoH) causing app crashes (thanks @Snashy [#4](https://github.com/jhaiian/ClintBrowser/issues/4))

---

## 📁 Project Structure

- Reorganized Kotlin files for better structure
- Refactored modules for cleaner architecture
- Moved hardcoded strings to `strings.xml`
- Renamed and updated strings for clarity
- Removed unused strings
- Removed unused drawable resources
- For more details, see [Contributing.md](https://github.com/jhaiian/ClintBrowser/blob/main/Contributing.md)

---

## 🔥 TL;DR

- Major UI customization update (themes, accents, surface intensity)
- New address bar positions and menu styles
- Reworked tab menu with favicon support
- Improved privacy with new favicon system (no Google dependency)
- Better support and fixes for lower Android versions
- General performance, stability, and codebase improvements

---

# v1.0.2-beta-2

## 🛠️ Fixes
- 🧊 Fixed crash dialog viewer not following the theme  
- ⚠️ Fixed package installer warning dialog not following the theme  
- 🧭 Fixed toolbar title changing after switching themes  
- 📱 Fixed a status bar placeholder appearing in the main activity even when "Hide Status Bar" is enabled after a theme change  
- 🔄 Fixed nested views staying hidden after pausing and resuming the app  
- 🌐 Fixed desktop mode on GitHub and other websites  
- 🔧 Fixed app restarting even though the "Hide Status Bar" changes were canceled by the user.

## 🚀 Improvements
- 💻 Improved desktop mode JavaScript (thanks @Vonjoo [#3](https://github.com/jhaiian/Clint-Browser/pull/3))  
- 📡 Improved desktop mode HTTPS request headers — no longer incorrectly identifies the device as mobile  
- ❌ Added a cancel button to the Hide Status Bar dialog  

## 🔧 Refactor
- 📁 Moved all JavaScript from hardcoded Kotlin files to the assets folder  

## 🗂️ Project Structure
- 🧹 Reorganized all Kotlin files  
- 📖 For more details, see [Contributing.md](https://raw.githubusercontent.com/jhaiian/Clint-Browser/refs/heads/main/Contributing.md) on GitHub  

## ⚙️ CI/CD
- 🏷️ Fixed release changelog extraction not including the version heading

---

# 🚀 v1.0.2-beta-1

I added an App Theme setting in the **Look and Feel** fragment. From there, you can select a theme:

## 🎨 Themes

- 🟣 **Default** – Deep purple, signature Clint style  
- 🌙 **Dark** – Dark background with white accents  
- ⚪ **Light** – Clean white background with dark accents  

## ⚙️ Changes

- 🧭 The App Theme option has also been added to the second page of the Setup Wizard, next to the Terms of Service and Privacy Policy agreements.  

## 🐞 Bug Fixes

- Fixed all bugs and issues in notification downloads.  
- Fixed `Builder.yml` not recognizing the new format of `CHANGELOG.md`.  
- Fixed the issue where the update changelog reader was cutting off content.  
- Fixed the beta enrollment not checking for stable releases.  

## 📁 Project Structure

The `MainActivity.kt` file has now been split into multiple components to improve maintainability and reduce its size (it was previously 1092 lines, which made it hard to manage).

- **MainActivity.kt (241 lines)**  
  Handles fields, lifecycle, preferences listener, and back key handling.

- **MainTabDelegate.kt (139 lines)**  
  Manages tab opening, closing, saving, restoring, and switching.

- **MainWebViewDelegate.kt (144 lines)**  
  Responsible for WebView creation, settings configuration, dark mode, user agent, desktop mode, and search engine URLs.

- **MainScrollDelegate.kt (131 lines)**  
  Handles bar animations, scroll tracking, and swipe refresh behavior.

- **MainUiDelegate.kt (264 lines)**  
  Manages the address bar, navigation buttons, popup menu, and overall UI updates.

- **MainFullscreenDelegate.kt (78 lines)**  
  Handles entering and exiting fullscreen mode.

- **MainFileChooserDelegate.kt (101 lines)**  
  Manages file selection and camera chooser functionality.

This change was made because `MainActivity.kt` had grown to 1092 lines, making it difficult to maintain and manage.

---

# v1.0.1

## ✨ New Features

### 📑 Bookmarks
- Save any page with a single tap from the navigation bar bookmark button  
- Live-updating bookmark icon reflecting current page state  
- Dedicated Bookmarks screen to view, open, and delete saved pages  
- Bookmarks accessible from the ⋮ menu  
- All bookmarks stored locally (no sync or upload)

### 📤 Media & Upload Support
- Upload support for images, videos, audio, and recordings  

### 🖥️ Look & Feel & UI Enhancements
- New **Look & Feel** section in Settings  
- WebView Dark Mode support for easier night browsing  
- Scroll-hide toolbar & navigation bar (auto hide/show on scroll)  
- Pull-to-refresh automatically disabled while bars are hidden  

### 🌐 Browser Behavior & Navigation
- Default browser selection added (Setup + General settings)  
- “Open in ___” option (grayed out if no compatible app exists)  
- Improved intent handling:
  - Better detection of supported apps  
  - Prompt to choose between external app or in-app browsing  

### ⬇️ Updates & System Tools
- In-app updater with progress dialog (no browser redirect)  
- “View Changelog” option in update settings  
- Skip update checks on mobile data or metered connections  
- Crash log viewer redesigned into dialog UI  
- Terms of Service / Privacy Policy now open in dialogs instead of browser  

---

## 🔄 Improvements

### 🌐 Web & Browsing
- Address bar updates in real time (including redirects & SPA routing)  
- Full URL display (protocol + full path)  
- Improved desktop mode via JavaScript injection for compatibility  
- SwipeRefreshLayout disabled on YouTube Shorts to fix refresh issues  

### 📚 UI & Architecture
- Settings reorganization:
  - Appearance moved to **Look & Feel**
  - Search engine moved to **General**
  - Removed separate search engine screen  
- Reworked `strings.xml` for readability  
- Markwon integrated for Markdown rendering and credited in About page  
- About page updates:
  - Author link → `linktr.ee/jhaiian`  
  - Updated contact system (email instead of PayPal)  
  - Added Contributors section  

### 📄 Documentation
- Updated README with new screenshots and features  
- Updated Privacy Policy (uploads + permissions + bookmarks)  
- Updated CONTRIBUTING.md for new structure  

---

## 🐛 Fixes
- Fixed download list resetting when app closes  
- Fixed duplicate file numbering display issues  
- Fixed incorrect download toast file numbering  
- Fixed missing strings in `strings.xml`  
- Fixed visible dot on loading progress bar  
- Fixed YouTube Shorts refresh issue  

---

## ⚙️ Dependencies & Platform
- `compileSdk` / `targetSdk` upgraded: 34 → 36  
- `androidx.core:core-ktx`: 1.12.0 → 1.16.0  
- `androidx.appcompat:appcompat`: 1.6.1 → 1.7.0  
- `com.google.android.material:material`: 1.11.0 → 1.12.0  
- `androidx.webkit:webkit`: 1.10.0 → 1.13.0  

---

## 🔁 CI/CD & Build System
- Auto version bump on tag push (`versionName` & `versionCode`)  
- Release manifest now includes `versionCode`  
- Automatic deletion of old draft releases per tag  
- GitHub Actions now uses `GIT_USERNAME` and `GIT_EMAIL` secrets  
- Added `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24` for Actions compatibility  

---

## 🧱 Internal Changes
- Full package restructure:

---

## 🙏 Credits
- **Vonjooo** — improvements to `release.yml` ([#1](https://github.com/jhaiian/Clint-Browser/pull/1))  
- **snashyturner** — reported APK install issue in Downloads ([#2](https://github.com/jhaiian/Clint-Browser/issues/2))

---

# v1.0.1-beta-2

## ✨ Added
- Upload support for image files, video, audio, and recordings.  
- **“Hide Status Bar”** option in **Settings → General** (full screen browsing, off by default).  
- **“Skip update check on launch when on mobile data or a metered connection”** option to reduce data usage.  
- **“Open in ___”** option (grayed out if no app is available).  
- **“View Changelog”** option in the update settings to view the full app changelog.  
- New **“Look & Feel”** section in Settings.  
- **WebView Dark Mode** support for easier night-time browsing.  
- **Default Browser selection** added in **General settings** and **Setup activity**.  

## 🔄 Changed / Improved
- The updater now installs updates directly in-app with a progress dialog instead of opening the browser.  
- All Markdown files now use **Markwon** for formatting.  
- Markwon is now credited on the About page.  
- Settings reorganization:
  - Moved general appearance settings to **Look & Feel**.  
  - Moved the search engine selector to **General**.  
  - Removed the separate search engine settings screen.  
- Terms of Service, Privacy Policy, and Policy viewer now open in a dialog instead of the browser.  
- Crash log viewer now uses a dialog with the same style as ToS and Privacy Policy.  
- Improved intent support:
  - Fixed cases where supported apps were not detected.  
  - Added prompt to choose between opening an external app or continuing in-app.  
- Improved desktop mode by injecting JavaScript for better site compatibility.  
- Reworked some strings in `strings.xml` for better readability.  
- Updated the Privacy Policy to reflect new permissions and uploads.  
- Updated `CONTRIBUTING.md` to reflect the new project structure.  
- Updated **README.md** to reflect all new features and replace all screenshots.  

## 🐛 Fixed
- Page refresh issues on YouTube Shorts (SwipeRefreshLayout is now disabled on Shorts).  
- Download list being deleted when the app is closed.  
- Incorrect display in the download list when duplicate file numbers are added.  
- Download toast not displaying the correct file number for duplicates.  
- Some missing strings in `strings.xml`.  
- Visible dot appearing on the right side of the webpage loading progress bar.

---

## v1.0.1-beta-1

### New Features

#### Bookmarks
- Save any page with a single tap from the bookmark button in the navigation bar
- Bookmark icon updates live to reflect the saved state of the current page
- Dedicated Bookmarks screen — view, open, and delete saved pages
- Bookmarks accessible from the ⋮ menu
- All bookmarks stored locally on device — never synced or uploaded

#### Scroll-Hide Toolbar & Navigation Bar
- Toolbar and navigation bar automatically slide away when scrolling down, and return when scrolling up
- Smooth height-based animation — WebView resizes in sync, no content is hidden
- Pull-to-refresh is automatically disabled while bars are hidden to prevent accidental refresh
- Toggled from Settings → General → Hide bars when scrolling (enabled by default)

#### General Settings
- New General section added to Settings
- Houses the scroll-hide behavior toggle

#### Permissions
- Added `REQUEST_INSTALL_PACKAGES` — used to install APK files downloaded through the browser and to apply in-app updates. Installation always requires explicit user confirmation.

### Improvements

- **Address bar now updates in real time** — URL reflects redirects and SPA route changes as they happen, not just on page start and finish
- **Address bar displays the full URL** — protocol and full path shown as-is

### Dependencies

- Raised `compileSdk` and `targetSdk` from 34 to 36
- `androidx.core:core-ktx` 1.12.0 → 1.16.0
- `androidx.appcompat:appcompat` 1.6.1 → 1.7.0
- `com.google.android.material:material` 1.11.0 → 1.12.0
- `androidx.webkit:webkit` 1.10.0 → 1.13.0

### CI/CD

- `release.yml` now automatically bumps `versionName` and `versionCode` in `build.gradle` when a tag is pushed and commits the change back to `main`
- `release.yml` now writes `versionCode` into the update manifest alongside `version`
- Draft releases for the same tag are automatically deleted before a new release is created
- Manifest commit now uses `GIT_USERNAME` and `GIT_EMAIL` secrets instead of the generic `github-actions[bot]` identity
- Added `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24` environment variable for Actions compatibility

### Internal

- Full package restructure — all source files reorganised into `activities/`, `bookmarks/`, `crash/`, `downloads/`, `network/`, `settings/`, `tabs/`, `update/`, and `webview/` packages

### Documentation

- Privacy Policy updated to cover the bookmark system and the `REQUEST_INSTALL_PACKAGES` permission
- README updated with screenshots, beta version badge, and corrected release tag links
- Contributing.md project structure updated to reflect the new package layout

### About Page

- Author link now points to `linktr.ee/jhaiian` instead of `github.com/jhaiian`
- Author hypertext label updated to `jhaiian`
- PayPal donate link replaced with a dedicated Contact section using the email address
- New Contributors section added — links to `Contributors.md` on GitHub
- Email intent subject line changed from `"Clint Browser Support"` to `"Clint Browser"`

### Credits

- **Vonjooo** — improvements to `release.yml` ([#1](https://github.com/jhaiian/Clint-Browser/pull/1))
- **snashyturner** — reported unable to install APK from Downloads ([#2](https://github.com/jhaiian/Clint-Browser/issues/2))

---

## v1.0.0 — First Release

### Browser Core
- WebView-based browsing with full tab management
- Multi-tab support with tab switcher
- Incognito mode with no cookies, cache, or history saved
- Address bar with select-all on focus for easy URL replacement
- Back, forward, refresh, and home navigation
- Pull-to-refresh support
- Desktop Mode toggle via the ⋮ menu
- Intent support — websites can open installed apps (e.g. YouTube, Spotify)
- Full screen video and media support

### Privacy & Security
- Tracker and analytics domain blocking at the network level
- Third-party cookie blocking
- Generic User-Agent to reduce fingerprinting
- DNS over HTTPS (DoH) with four modes: Off, Default, Increased, Max
- DoH provider choice: Cloudflare or Quad9
- SSL error enforcement — invalid certificates are always rejected
- Incognito tabs fully isolated from normal session data

### Search
- Default search engine selection: DuckDuckGo, Brave Search, or Google
- Google privacy warning shown when switching to Google
- Search engine changeable at any time from settings

### Downloads
- Custom download engine built on OkHttp — no system DownloadManager
- Real-time download progress screen with percentage and file size
- Cancel downloads in-app or from the notification
- Open completed files directly from the downloads screen or notification
- Duplicate filename handling — auto-renames to avoid overwrites

### UI & Experience
- Dark purple theme throughout
- Custom popup menu replacing the system overflow menu
- Adaptive launcher icon with black background
- Full screen support for video playback

### Updates
- In-app update checker for Stable and Beta channels
- Architecture-aware download links (arm64-v8a, armeabi-v7a, x86, x86_64, universal)
- Check for updates on launch (optional)
- Beta enrolment with channel description

### Setup & Onboarding
- First-launch setup wizard: Privacy Policy & Terms consent, search engine selection, and DoH configuration
- Privacy Policy and Terms of Service linked to GitHub — always up to date

### About
- App version, architecture, and build info
- Links to GitHub repository, Privacy Policy, Terms of Service, Discord community, Ko-fi, and PayPal

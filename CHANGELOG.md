# Changelog

All notable changes to Clint Browser are documented here.

---

# v1.0.6

*A small update.*

---

## Added

- Added the ability to add manual filter rules.
- Added the ability to add filter lists via file.

---

## Enhancement

- The tab created by a pop-up dialog will now exit and return to the previous tab when you press the phone's back button, instead of showing the exit confirmation.

---

## Bug Fixes

- Fixed content preview not applying cosmetic rules.

---

## Extra Changes

- Updated the Quiver Guard description in the main settings.
- Added Dependabot to the repository.

---

# v1.0.5

*Finally guys, I'm done with the update. I'm really sorry, y'all. It took a while because I had to learn Rust.*

## Overview

First thing: the app logo got completely redesigned.

The previous one was overcomplicated. It had too much detail, and overall, it was too flashy. Yeah, that's what I decided anyway. I hope y'all like it.

Here we go: the biggest update I have ever released in my life.

---

## Quiver Guard

Quiver Guard is an adblock engine that runs on Brave's adblock-rust.

How it works is simple: you download a filter list, enable it, compile it, and that's it.

For a more technical explanation: basically, we send the compiled list to adblock-rust through JNI, and once it's ready, we use it to block ads, cosmetic scripts, scriplets, and more.

### Preset Filter Lists

- AdGuard Annoyances
- AdGuard Base Filter
- AdGuard Mobile Ads
- EasyList
- EasyPrivacy
- Fanboy Annoyances

You need to download them because I don't want to embed them into the app. I don't want to increase the file size. Just download the filter lists, save them, and you should be good to go.

You can also add filter lists that are not included in the preset list by adding a link. It will be downloaded and added to the list automatically.

### Available Actions

- Force update
- Check for updates
- Copy links
- Copy names

And just like all the other activities that use RecyclerView, you can:

- Sort
- Search
- Select
- Remove

> *Yeah, it is still experimental. Improvements and fixes can still happen. It depends on whether I can fix the bugs that are found.*

---

## Acknowledgments

Thankfully, this feature was requested by [**@manhd89**](https://github.com/manhd89) ([#5](https://github.com/jhaiian/ClintBrowser/issues/5)).

And thanks to my friend **Von** for helping me extract the uBlock JS into Rust: [**@vonjooo**](https://github.com/vonjoo).

---

## Bug Fixes

- Fixed navigation not working with gesture controls on some devices. Thank you for reporting this bug, [**@brnwlshubh**](https://github.com/brnwlshubh) ([#6](https://github.com/jhaiian/ClintBrowser/issues/6)).
- Fixed the hold listener showing images even when you hold-click on a hyperlink or link.
- *I think that's all, or I might have forgotten something. This version has been in development for months, so I'm sorry.*

---

## Improvements

- The download engine now runs on Kotlin Coroutines.

---

## Extra Changes

I changed all the back buttons back to normal because [**@snashyturner**](https://github.com/snashyturner) said they looked like a bonefish, which, not gonna lie, is true.

- Change build gradle language from groovy to kotlin.
- Bump compile SDK and target SDK from 36 (Android 16) to 37 (Android 17).
- Update dependencies.
- Changed all notification small icons to the app icon.
- Updated privacy policy.
- Rework :Updatechecker.kt'

I also started adding comments to new files. I will gradually add comments to existing files as well. Right now, I'm starting to do this because, yeah, it was a rookie mistake. I sometimes forget how my own code works and have to reread everything again.

---

*Also, I'm really sorry if this update took so long. I really needed to learn Rust in order to implement this feature, and yeah, it wasn't easy. Learning a new language while developing a big feature took a lot of time.*

---

# v1.0.4

*Sorry, everyone. This update took a very long time.*

## Overview

The download system has been completely rewritten and is heavily inspired by 1DM on Android. However, this does not mean I copied their code. I simply observed how their download manager works and implemented my own version from scratch.

The performance may not be on the same level as 1DM yet, but I will continue improving it based on your feedback. So please help me make this app better by sharing bugs, suggestions, and feature requests.

My goal is to create the first true all-in-one browser for Android that combines browsing, downloading, and productivity features in a single app.

---

## New Download System

Downloads now support pause and resume functionality.

- Resume support depends on whether the server supports resumable downloads (Thank you for @snashyturner for suggesting this).
- The app now clearly indicates whether a download can be resumed.
- Migrate the download page to SQLite

### Added

**Custom Download Location (SAF)**  
This allows you to save downloads to a custom location using the Storage Access Framework (SAF).

Because SAF provides limited write access, files are first downloaded to a temporary folder inside the app's data directory, then copied to the selected destination and deleted from the temporary folder. If your destination is on emulated storage, this process will temporarily require roughly twice the file size in storage space.

**Unmetered Only**  
Downloads will only run on Wi-Fi or other unmetered connections. Active downloads will automatically pause when mobile data is being used.

**Concurrent Downloads**  
Controls how many downloads can run at the same time. Additional downloads are queued and will automatically start when a slot becomes available.

- Maximum: 24 downloads

**Split Download Parts**  
Controls the maximum number of parts a file can be divided into. For smaller files, the actual number of parts may be lower to avoid unnecessary overhead.

- Maximum: 32 parts

**Max Simultaneous Parts**  
Controls how many download parts can run simultaneously. The download engine automatically adjusts the number of active parts based on download speed, up to the configured limit.

- Maximum: 8 parts

**Retry Settings**  
- Always retry downloads, even if the server error appears unrecoverable
- Retry count (0 = unlimited)
- Retry interval (seconds)

**Push Notifications**  
Get alerted when a download completes, fails, or starts retrying.

**Ignore Battery Optimizations Permission**  
Added support for requesting the Ignore Battery Optimizations permission to improve download reliability.

> All of these options can be found in the new **Download Settings** screen, accessible from both Settings and Downloads.

---

## Progress System Improvements

The traditional progress bar has been removed. Instead, download progress is now displayed directly in the background of each download card for a cleaner appearance.

Each download card now shows:

- Download percentage
- Current downloaded size
- Target download size (if known)
- Total file size
- Estimated time remaining
- Elapsed time

If the file size is unknown, the progress display automatically switches to an indeterminate state.

---

## New Download UI

### Added
- Fast scroller
- Search bar

### ViewPager Tabs
- All
- Downloading
- Finished
- Error

### Sorting Options
- Name
- Date
- Size
- Status
- Ascending order
- Descending order

---

## Download Actions

### Completed Downloads
When a download is finished, the Pause and Resume buttons are replaced with a menu containing:
- Open
- Share
- Open Folder
- Redownload
- Redownload with Additional Options
- Remove
- Copy Download Link
- Copy File Name
- Copy File Path
- Properties

When removing a download, a confirmation dialog will ask whether you also want to delete the downloaded file.

### Active Downloads
While a download is in progress, you can long-press it to select it. An overflow menu will appear with the following actions:
- Open
- Share
- Open Folder
- Redownload
- Redownload with Additional Options
- Update Download Link
- Update Download Link in Browser
- Remove
- Copy Download Link
- Copy File Name
- Copy File Path
- Properties

### Multiple Selection Actions
- Redownload
- Remove
- Copy Download Link
- Copy File Name
- Copy File Path

---

## New Download Dialog

When a downloadable file is detected, the download will no longer start automatically. Instead, a new download dialog will appear where you can:

- View the download link
- Copy the download link
- Rename the file and its extension
- Configure download settings before starting

**Metered Network Detection**  
If you try to download a file while **Unmetered Only** is enabled, a dialog will appear:

> *Metered Network Detected*  
> Unmetered Only is enabled, but you're connected to a metered network. Do you want to use a metered network to download this file?  
> [Cancel] [No] [Yes]

**File Already Exists**  
If the file you're trying to download already exists in the final destination, a dialog will appear:

> *File Already Exists*  
> Add Duplicate  
> Overwrite Existing File

**Manual Download**  
A new Floating Action Button (FAB) has also been added to the Downloads page. Tapping it allows you to create a new download manually. It opens the same dialog used by the download detection flow, but the download URL is fully editable.

---

## Enhancements

- Pure Mode color intensity now uses black bottom sheets and popups across all themes.
- The entire Settings UI and all settings fragments have been redesigned. The old basic list layout has been replaced with a modern, custom card-style interface.
- The custom toast shown when copying text from the app will no longer appear on Android 13 and above. This is to prevent two toasts from being displayed at the same time (one from the system and one from the app itself).
- The popup dialog will now show the URL that the website is trying to open.

---

## Bug Fixes

- Fixed an issue where the Hold Image Listener failed to display GIF images.
- Fixed an issue where the Hold Image Listener failed to display Base64 images.
- Fixed an issue where the Hold Image Listener failed to display SVG images..
- Fixed the SwipeRefreshLayout being triggered on map websites such as Bing Maps and Google Maps. The app should now detect when the user is interacting with a canvas element (used by most map websites) and prevent SwipeRefreshLayout from being triggered while the canvas is being touched
- Fixed download not working in the background

---

## Extra Changes

- The default theme before was renamed to **Legacy** and put last on the theme list. The new default theme is **Dark**.
- Renamed the default accent color to **Monochrome** and moved it to the third position after Material You. **Purple** was moved to the first position because it is now the default accent color with a strong tint color intensity.
- Removed the custom color on incognito tabs – now incognito tabs will follow the theme instead of having a custom green.
- Created an **F-Droid flavor**. In the F-Droid flavor, all update-related code has been completely removed.
- Limited search suggestions history to 20 and bookmarks to 10 to prevent the app from trying to load too many lists, improving performance on low-end devices.
- Updated About page
- Updated TOS
- Updated Privacy Policy
- Update dependency `com.google.android.material:material` from 1.13 to 1.14.

---

*Thank you everyone for the 8 stars – highly appreciate it. And to anyone who was waiting for this update, I hope I don't disappoint you. If you don't like anything, please share your feedback.*

---

# v1.0.3-r2

## 🛠 Fixes

- Fixed Site Permissions not recognizing that some URLs belong to the same website.

  For example:
  - `en.wikipedia.org`
  - `www.wikipedia.org`

  These are part of the same website, but they were previously treated as different URLs, causing duplicate site settings.

- Fixed an issue that heavily affected **Desktop Mode**.

  This was especially noticeable on Facebook, where enabling Desktop Mode would sometimes switch back to mobile mode. This happened because subdomains like:

  - `m.facebook.com`
  - `web.facebook.com`

  were treated as separate websites, creating an infinite loop.

- Fixed by using `okhttp3.HttpUrl.Companion.toHttpUrlOrNull()` to properly detect the original domain name.

- Fixed multiple UI and functionality inconsistencies across **Site Settings**.

  Behavior should now be consistent with the History page/system.

## ✨ Improvements

- Migrated tabs storage from **SharedPreferences** to **SQLite**.

  Your existing tab data should not be lost, as the app will automatically migrate everything after the update.

- Added a **JavaScript warning message** when disabling JavaScript.

  Users will now be informed that some app features relying on JavaScript may stop working when it is turned off.

- Reworked `app/build.gradle`, `build.yml`, and `release.yml`.

  The build system will now automatically detect whether the project actually requires **ABI splitting**.

  From now on, releases will generate only **one APK** by default 📦

  If native **C/C++ libraries** are added in the future, ABI splitting will automatically be enabled and separate APKs will be generated

## 📦 Dependency Updates

- Material Components:
  - `com.google.android.material:material` **1.12.0 → 1.13.0**

- RecyclerView:
  - `androidx.recyclerview:recyclerview` **1.3.2 → 1.4.0**

- OkHttp:
  - `com.squareup.okhttp3:okhttp` **4.12.0 → 5.3.2**
  
---

# v1.0.3

> This is the entire changelog from version 1.0.2 to 1.0.3.

## ✨ Added

### 🔐 Site Settings
Added a new **Site Settings** section in Settings. From here, you can manage site permissions for:

- Camera  
- Microphone  
- Location  
- Notifications  

Yes, websites now ask for permission for these features, and everything is configurable in Site Settings.

Each permission now has configurable default behavior:

- **Ask first** — Show a prompt when a site requests access (Default)  
- **Always deny** — Block all site requests without prompting  
- **Always allow** — Grant all site requests without prompting  

#### 🌐 Site Exceptions

Added **Site Exceptions** inside each permission setting.

Exceptions override the default behavior for specific websites.

If you allow or deny a permission and check **"Don't ask again"**, that website will automatically be added to Site Exceptions, where you can remove it later.

---

### 🖥️ Desktop Mode Settings
Added **Desktop Mode settings** inside Site Settings.

You can choose how Desktop Mode behaves per website:

- **Save State (Default)** — Remember Desktop Mode for this site and automatically apply it on future visits  
- **Do Not Save State** — Works like the old system. Desktop Mode is only applied for the current session and will not be remembered  

#### 💾 Save State Behavior

When **Save State** is enabled:

- Every time you activate Desktop Mode on a website, it is saved locally using SQLite  
- The website is added to a saved Desktop Mode list  
- You can remove it either by disabling Desktop Mode on that website or removing it directly from the list  
- Every time you revisit that website, Desktop Mode is automatically applied  
- When you leave the website, it returns to mobile mode until you visit a saved site again  

---

### 🛡️ Popup Protection

Added Popup Protection system.

Whenever a website tries to open a new window or popup, an alert will appear asking if you want to allow it or not. This helps prevent ads and unwanted websites from opening new tabs or windows automatically.

Ad blocker support will come eventually, but it is not a priority right now.

---

### 🔍 Search Suggestions
- Powered by DuckDuckGo API (`https://duckduckgo.com/ac/?q=`)
- Real-time query predictions
- Improved search UX with instant suggestions

---

### 🎤 Speech-to-Text Support
- Voice input in address bar
- Hands-free searching capability
- Enhanced accessibility features

---

### 📜 Search History Management
- Access via Settings → Browser → History
- Delete individual or all history entries
- Sort history by Title (ascending/descending)
- Sort history by Last Visit (ascending/descending)
- Search from the list
- Saved locally using SQLite
- Added a **History** shortcut in both menus for quicker access.

---

### 🔗 Intent Support
- Added more intent actions for better app integration
- Enhanced deep linking capabilities
- Improved third-party app communication

---

### 📖 Reader Mode
- Added Reader Mode, available via the menu.
- Reader Mode opens in content preview.

---

### 🗂️ Browser Fragment (New in Settings)
- Added a new Browser Fragment in Settings.
- Moved JavaScript setting from Privacy & Security to Browser Fragment.

#### 💾 Resource Caching Modes
You can now choose from 4 caching modes:

- **Smart Cache** – Uses network with cached data as a fallback when available
- **Cache First** – Shows saved pages first, fetches from network only if missing
- **Always Fresh** – Always fetches from the network, never uses locally saved pages
- **Offline Only** – Only loads saved pages, never connects to the network

This is essentially WebView cache behavior with renamed modes.

Offline Only is an advanced option and may rarely be needed, but it is available.

---

### 📡 Data Saver Fragment (New)
- **Data Saver** – Master switch available in both menu popup and bottom sheet
- **Disable Images** – Prevents all images from loading
- **Cache First** – Shows saved pages first, fetches from network only if missing (overrides Resource Caching)
- **Disable Autoplay** – Prevents videos and media from playing automatically

---

### 🖼️ Image Hold Listener in WebView

Added an image hold listener in WebView. Basically, when you long-press an image on a webpage, a bottom sheet will appear with these options:

- Open image in new tab  
- Open image in incognito  
- Preview image  
- Copy image  
- Download image  
- Share image  

#### 🔍 Preview Image Feature

“Preview image” opens a bottom sheet WebView that shows a preview of the image or the webpage content.

This image listener is also available inside the preview WebView. However, instead of **Preview image**, it is replaced with:

- Open image in current tab  

Everything else is self-explanatory.

---

### 🔗 Hold Link Listener

Added a hold link listener, similar to the image hold feature but for links. When long-pressing a link, the following options appear:

- Open in new tab  
- Open in incognito  
- Preview page  
- Copy link address  
- Copy link text  
- Share link  

#### 🌐 Preview Page Behavior

Just like the image feature, in the preview WebView the **Preview page** option replaces **Open in current tab**.

---

### 📦 Updated About Page

Updated the About page.

- Added remaining libraries used in the About page  
- Documented their usage across the app for better transparency

---

### ℹ️ WebView Version Information

- WebView version information added to the About page
- WebView version information included in crash handler reports
- WebView version information added to the GitHub issue template

All of this information is based on the WebView version installed on your device that helps run this app. This will help with bug identification, debugging, and general diagnostics.

---

## 🔄 Changed

### 📚 Bookmark System
- Complete overhaul of bookmark save system
- Migrated to SQLite
- Better performance and faster loading
- Cleaner bookmark organization

### 🎨 Bookmark UI
- Complete UI redesign matching the new history page layout
- New sorting options: Title, Last Visit, and Date Added
- Ascending and descending order for all sort types
- Search from the list
- Consistent look and feel with history section

### 🔍 Search Bar Component
- Replaced standard EditText with Material Search Bar
- Modern Material Design look and feel
- Improved user interaction and visual feedback

---

## 🗑️ Removed

### ❌ DNS over HTTPS (DoH)

Completely removed DNS over HTTPS (DoH). It never really worked as intended and only intercepted requests made through OkHttp, but it didn’t actually affect web requests across the app.

I originally planned to route it through a VPN-based implementation, but I changed my mind and decided to remove it completely. I don’t want any ghost features in this app.

---

### ⚙️ General Section from Settings

- Removed **General** section from Settings  
  - All settings previously inside General were redistributed into more relevant sections  
  - General section was removed because it no longer had a clear purpose in the app  
  - No future use case was defined, and it was contributing to unnecessary clutter  

---

## 🛠️ Fixes

- Fixed a download error when handling `blob:` URLs  
  - Error: `Failed: Expected URL scheme 'http' or 'https' but was 'blob'`

- Fixed tab switcher floating issue

- Fixed download issue caused by incorrect file extension detection  
  - Now using the **SimpleMagic by j256** library to properly detect file MIME types from content bytes
  
- Fixed the intent receiver not clearing properly, causing it to retrigger every time the activity is recreated

---

## 🎨 UI Improvements

- Hawkanized all directional arrow icons across the app  

---

## 📄 Legal Updates

- Updated **Terms of Service**  
- Updated **Privacy Policy**

---

## 🏗️ Project Structure Rework

Completely reorganized the project structure.

Moved all `.kt` files into their own proper folders and subfolders based on their purpose, responsibility, and feature area.

This makes the codebase much cleaner, easier to navigate, and more maintainable for future development.

---

# v1.0.3-beta-4

## 🗑️ Removed DNS over HTTPS (DoH)

Completely removed DNS over HTTPS (DoH). It never really worked as intended and only intercepted requests made through OkHttp, but it didn’t actually affect web requests across the app.

I originally planned to route it through a VPN-based implementation, but I changed my mind and decided to remove it completely. I don’t want any ghost features in this app.

---

## ⚙️ Settings Changes

- Removed **General** section from Settings  
  - All settings previously inside General were redistributed into more relevant sections  
  - General section was removed because it no longer had a clear purpose in the app  
  - No future use case was defined, and it was contributing to unnecessary clutter  

---

## 🔐 Added Site Settings

Added a new **Site Settings** section in Settings. From here, you can manage site permissions for:

- Camera  
- Microphone  
- Location  
- Notifications  

Yes, websites now ask for permission for these features, and everything is configurable in Site Settings.

Each permission now has configurable default behavior:

- **Ask first** — Show a prompt when a site requests access (Default)  
- **Always deny** — Block all site requests without prompting  
- **Always allow** — Grant all site requests without prompting  

### 🌐 Site Exceptions

Added **Site Exceptions** inside each permission setting.

Exceptions override the default behavior for specific websites.

If you allow or deny a permission and check **"Don't ask again"**, that website will automatically be added to Site Exceptions, where you can remove it later.

---

## 🖥️ Added Desktop Mode Settings

Added **Desktop Mode settings** inside Site Settings.

You can choose how Desktop Mode behaves per website:

- **Save State (Default)** — Remember Desktop Mode for this site and automatically apply it on future visits  
- **Do Not Save State** — Works like the old system. Desktop Mode is only applied for the current session and will not be remembered  

### 💾 Save State Behavior

When **Save State** is enabled:

- Every time you activate Desktop Mode on a website, it is saved locally using SQLite  
- The website is added to a saved Desktop Mode list  
- You can remove it either by disabling Desktop Mode on that website or removing it directly from the list  
- Every time you revisit that website, Desktop Mode is automatically applied  
- When you leave the website, it returns to mobile mode until you visit a saved site again  

---

## 🛡️ Popup Protection

Added Popup Protection system.

Whenever a website tries to open a new window or popup, an alert will appear asking if you want to allow it or not. This helps prevent ads and unwanted websites from opening new tabs or windows automatically.

Ad blocker support will come eventually, but it is not a priority right now.

---

## 📚 Added History Shortcut

Added a **History** shortcut in both menus for quicker access.

---

## 📦 Updated About Page

Updated the About page.

---

## 🛠️ Fixes

- Fixed a download error when handling `blob:` URLs  
  - Error: `Failed: Expected URL scheme 'http' or 'https' but was 'blob'`

---

## 🎨 UI Improvements

- Hawkanized all directional arrow icons across the app  

---

## 📄 Legal Updates

- Updated **Terms of Service**  
- Updated **Privacy Policy**

---

## 🏗️ Project Structure Rework

Completely reorganized the project structure.

Moved all `.kt` files into their own proper folders and subfolders based on their purpose, responsibility, and feature area.

This makes the codebase much cleaner, easier to navigate, and more maintainable for future development.

---

# v1.0.3-beta-3

## Added

### Search Suggestions
- Powered by DuckDuckGo API (`https://duckduckgo.com/ac/?q=`)
- Real-time query predictions
- Improved search UX with instant suggestions

### Speech-to-Text Support
- Voice input in address bar
- Hands-free searching capability
- Enhanced accessibility features

### Search History Management
- Access via Settings → Browser → History
- Delete individual or all history entries
- Sort history by Title (ascending/descending)
- Sort history by Last Visit (ascending/descending)
- Search from the list
- Saved locally using SQLite

### Intent Support
- Added more intent actions for better app integration
- Enhanced deep linking capabilities
- Improved third-party app communication

---

## Changed

### Bookmark System
- Complete overhaul of bookmark save system
- Migrated to SQLite
- Better performance and faster loading
- Cleaner bookmark organization

### Bookmark UI
- Complete UI redesign matching the new history page layout
- New sorting options: Title, Last Visit, and Date Added
- Ascending and descending order for all sort types
- Search from the list
- Consistent look and feel with history section

### Search Bar Component
- Replaced standard EditText with Material Search Bar
- Modern Material Design look and feel
- Improved user interaction and visual feedback

---

# v1.0.3-beta-2

## Added

### Reader Mode
- Added Reader Mode, available via the menu.
- Reader Mode opens in content preview.

### Browser Fragment (New in Settings)
- Added a new Browser Fragment in Settings.
- Moved JavaScript setting from Privacy & Security to Browser Fragment.

#### Resource Caching Modes
You can now choose from 4 caching modes:

- **Smart Cache** – Uses network with cached data as a fallback when available
- **Cache First** – Shows saved pages first, fetches from network only if missing
- **Always Fresh** – Always fetches from the network, never uses locally saved pages
- **Offline Only** – Only loads saved pages, never connects to the network

This is essentially WebView cache behavior with renamed modes.

Offline Only is an advanced option and may rarely be needed, but it is available.

### Data Saver Fragment (New)
- **Data Saver** – Master switch available in both menu popup and bottom sheet
- **Disable Images** – Prevents all images from loading
- **Cache First** – Shows saved pages first, fetches from network only if missing (overrides Resource Caching)
- **Disable Autoplay** – Prevents videos and media from playing automatically

---

## WebView Version Information

- WebView version information added to the About page
- WebView version information included in crash handler reports
- WebView version information added to the GitHub issue template

All of this information is based on the WebView version installed on your device that helps run this app. This will help with bug identification, debugging, and general diagnostics.

---

## Fixed

- Fixed tab switcher floating issue

---

# v1.0.3-beta-1

## ✨ Added Image Hold Listener in WebView

Added an image hold listener in WebView. Basically, when you long-press an image on a webpage, a bottom sheet will appear with these options:

- Open image in new tab  
- Open image in incognito  
- Preview image  
- Copy image  
- Download image  
- Share image  

### 🖼️ Preview Image Feature

“Preview image” opens a bottom sheet WebView that shows a preview of the image or the webpage content.

This image listener is also available inside the preview WebView. However, instead of **Preview image**, it is replaced with:

- Open image in current tab  

Everything else is self-explanatory.

---

## 🔗 Added Hold Link Listener

Added a hold link listener, similar to the image hold feature but for links. When long-pressing a link, the following options appear:

- Open in new tab  
- Open in incognito  
- Preview page  
- Copy link address  
- Copy link text  
- Share link  

### 🌐 Preview Page Behavior

Just like the image feature, in the preview WebView the **Preview page** option replaces **Open in current tab**.

---

## 🛠️ Fixes

- Fixed download issue caused by incorrect file extension detection  
- Now using the **SimpleMagic by j256** library to properly detect file MIME types from content bytes  

---

## 📦 About Page

- Added remaining libraries used in the About page  
- Documented their usage across the app for better transparency

---

# v1.0.2-r3

- Fixed the bug related to “Open in apps” by re-adding the QUERY_ALL_PACKAGES permission.

---

# v1.0.2-r2
- Remove QUERY_ALL_PACKAGES permission. I forgot to remove this before.
- Fixed download on lower Android versions. This was because WRITE_EXTERNAL_STORAGE was never requested. Now there is a runtime permission request.

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

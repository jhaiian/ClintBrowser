# Changelog

All notable changes to Clint Browser are documented here.

---

# v1.1.6-beta-1

---

## What's New

### Media Capture (Beta)

You can now download media directly from webpages using Clint's new Media Capture feature.

It works with normal video and audio files and also supports streaming media such as HLS/M3U8 streams. When Clint detects downloadable media on a webpage, you can easily download it.

You guys already know how this works, so I'm not going to explain it further.

But yeah, this is the entire reason after so many months I finally released a beta release again, because yeah, I did some testing, but it's different if other people test it, so expect some bugs and shenanigans.

---

## Download Manager — Bug Fixes & Improvements

- Fixed a race condition where concurrent calls to the download dequeue logic could launch two workers on the same download simultaneously, potentially corrupting the file.
- Fixed the speed limiter holding its lock while sleeping, which was causing parallel download parts to become serialized whenever a speed limit was set. Parallel downloads now actually run in parallel while still respecting the speed limit.
- Sanitized destination filenames to prevent path traversal through Content-Disposition headers. Also fixed a race condition where two downloads could claim the same filename at the same time.
- Fixed custom scheduled downloads permanently bypassing the daily download window after their first run. They now bypass the window only for the specific scheduled start.
- Fixed downloads with "Unmetered only" enabled never automatically resuming after being paused due to switching to a metered network. They now resume automatically when the network becomes unmetered again.
- Wrapped progress checkpoint writes so a temporary database error no longer causes the entire download to fail.
- Cleaned up a small memory leak where `removedIds` was never shrinking. Delete cleanup now also waits for the download job to fully stop before modifying the file or database.
- Converted unused `var` fields in `DownloadItem` to `val` after verifying that there were no direct mutations anywhere in the project.

---

## New: Keep Screen On

Added a new setting in **Download Settings**:

**Keep Screen On**

Keep the screen on while you're on the Downloads page until all pending downloads are finished.

This can be useful if your OS tends to kill downloads while you're sleeping.

---

## New: Expanded File Type Icons

Downloads now have more specific file type icons.

The file type system has been expanded from 6 icon categories covering around 140 extensions to 19 categories. New dedicated icons have been added for PDFs, spreadsheets, presentations, ebooks, subtitles, source code, fonts, design files, databases, installers and executables, certificates, calendars and contacts, torrents, and more.

---

## Translation

- Added Russian translation. (Thank you so much to @mirr1184-ctrl #52)

---

## Changes Outside Downloads

### New Browser Setting: Custom Select Menus

Added a new setting under **Browser Settings**:

**Custom Select Menus**

Use Clint's custom picker for `<select>` dropdowns instead of the WebView default.

This feature has actually been in Clint for a while, but there was previously no way to turn it off. You can now disable it from Browser Settings.

More options for overriding the ugly default WebView UI will be added in the future.

---

## Bug Fixes

- Fixed `SelectPickerDialog.kt` breaking on some websites. The title and description have now been removed from the picker to prevent this issue from happening again.

---

## Dependency Updates

- Bump `androidx.compose:compose-bom` from 2026.08.00 to 2026.09.00 by @dependabot[bot] in #51
- Bump `org.bouncycastle:bcprov-jdk18on` from 1.85.2 to 1.86 by @dependabot[bot] in #50
- Bump `org.jetbrains.kotlin.plugin.compose` from 2.4.10 to 2.4.20 by @dependabot[bot] in #49

---

> *And yeah, I'm still working on improving Clint's download manager and making it better. So feel free to report any bugs you find, and we'll work on fixing them!*

---


# v1.1.5

---

## What's New

If you guys didn't know, **Clint** is an acronym. It's not just "Clint." It stands for **Customizable Layered Internet Navigation Tool**.

See that word *Customizable*? Yeah. The app only had 40+ possible theme combinations before. I actually forgot the exact number, but guys, that ain't enough.

So I decided to:

### Revamp the Theme System

Before, Clint only had 8 accent colors. Now Clint has this many accent colors:

**Dynamic:**
- 🔄 Material You

**Purple Family:**
- 🟣 Purple
- 🪻 Deep Purple
- 👑 Royal Purple
- 💎 Amethyst
- 🌸 Lavender
- 🍇 Plum
- 🟪 Violet

**Dark/Neutral Family:**
- ⚫ Monochrome
- 🖤 Charcoal
- 🌑 Slate
- 🩶 Graphite
- ⬛ Obsidian
- ⚫ Onyx
- 🔘 Titanium

**Blue Family:**
- 🔵 Indigo
- 🔷 Blue
- 🌌 Midnight
- 🩵 Cyan
- ☁️ Sky
- 🌊 Teal
- 💠 Azure

**Yellow Family:**
- 🟡 Yellow
- 🍋 Lemon
- 🪙 Gold
- 🟠 Amber
- 🏖️ Sand
- 📜 Sepia
- 🌕 Mustard

**Red Family:**
- 🔴 Red
- 🟥 Crimson
- 💎 Ruby
- 🩷 Pink
- 🪸 Coral
- ❤️ Scarlet
- 🍷 Burgundy

**Orange Family:**
- 🟠 Orange
- 🔥 Deep Orange
- 🍊 Tangerine
- 🍑 Apricot
- 🟤 Copper
- 🍑 Peach
- 🧱 Terracotta

**Green Family:**
- 🟢 Green
- 💚 Emerald
- 🌿 Mint
- 🌲 Forest
- 🍋‍🟩 Lime
- 🫒 Olive
- 🌱 Sage

Total = 1+7+7+7+7+7+7+7 = 50

---

### Surface Intensity / Color Intensity

I also expanded the Surface Intensity / Color Intensity options from 3 to 5.

- **No Tint (New):** Plain background with accent-colored buttons and icons only.
- **Soft Tint**
- **Strong Tint**
- **AMOLED** (formerly Pure Mode)
- **AMOLED No Tint:** True black surfaces with accent-colored buttons and icons only.

In exchange, I removed the Legacy theme. **RIP Legacy.**

BTW, those were the start of Clint.

But I also added Strong Tint to Material You, which previously was only available with Soft Tint and AMOLED.

Same thing with Monochrome. It now has a Strong Tint variant too.

---

### Removed Dark Mode Switches for Websites

I also removed the switches for Dark Mode on websites because Android WebView already automatically makes websites look light or dark depending on whether you select Light or Dark mode.

Those switches were mainly for the Legacy theme, so now they're basically useless.

---

## The Math

Now we're done discussing what's new.

Let's do the math, okay?

- **2** = Dark / Light
- **50** = Accent Colors
- **5** = Surface Intensity / Color Intensity

**2 × 50 × 5 = 500 possible theme combinations**

So yeah, Clint now has **500 possible theme combinations**.

---

> BTW guys, nobody asked for this. Not my friends, not even an issue request. I just felt like it had to be done.
>
> Clint is supposed to be *Customizable*, after all.
>
> And BTW, if you want me to add more colors, just let me know.

---

## Other Changes

- Added a dedicated activity for Donations. *(Guys, I'm broke, alright.)*

---

## Bug Fixes

- Fixed Automatic Space After Punctuation adding spaces to addresses in the Address Bar. (Thanks to @ncgjbr-ai for reporting this! #42)
- Fixed the default outline not following the selected theme.
- Fixed paused download count on download badge.

---

# v1.1.4-r3

---

## Bug Fixes

- Fixed visible visual artifacts on some GPUs. (Thanks to @ncgjbr-ai for reporting this in #39!)

---

## Other Changes

- Bump `com.android.application` from 9.3.2 to 9.4.0 by @dependabot[bot] in #40

---

# v1.1.4-r2

---

## Bug Fixes

- Fixed an issue where the status bar became transparent instead of having its own view when the status bar was enabled and the search bar was positioned at the bottom. (Thanks to @ncgjbr-ai for reporting this in #38!)
- Fixed the animation of the bottom search bar when opening the search overlay.

---

> *You can skip this version if you don't use this combination of settings. I just wanted to release a quick fix because I don't want anybody to experience these bugs, especially since the next major update might take longer.*

---

# v1.1.4

---

## What's New

### Userscripts

Added userscript support (Tampermonkey style). You can find it in **Settings → Browser Settings → Userscripts**.

You can add userscripts by:
- Uploading a script file
- Downloading via link from Greasy Fork (tap the "Get Script" button)
- Creating a new one directly in the app (it comes with a code editor — it's not great, but at least I built one)

It supports most scripts from my testing. Just let me know if a script isn't working as intended, whether there's something we can do about it, or if it's just another WebView limitation.

(Thanks to @brnwlshubh for suggesting this #8)

---

### Search Engine & Search Suggestions

- Added Ecosia as a search engine option.
- Added the ability to create a custom search engine.

Use `{query}` where the search term goes.

Example: `https://www.google.com/search?q={query}`

- Added the ability to create your own custom search suggestions API.

Use `{query}` where the search term goes.

Example: `https://duckduckgo.com/ac/?q={query}&type=list`

(Thanks to @ncgjbr-ai for suggesting this #35)

---

### Frameless Mode Shortcuts Rework

Frameless mode shortcuts have been reworked.

Now, shortcuts will no longer be visible in the tab menu. If you click on a shortcut, it will open. If you go back home and click Clint again, it will return to browsing, and vice versa.

The shortcut tab will also have session persistence. Instead of coming back every time you restart the app, you will resume your session.

(Thanks to @deniganda for suggesting this #32)

---

### Download Manager

Added the ability to switch your download manager between:

- Clint
- 1DM
- 1DM+
- 1DM Lite
- ADM

(Thanks to @iHarryPotter178 for suggesting this #31)

---

## Bug Fixes

- Fixed the WebView getting cut off at the top when the status bar is on. (Thanks to @deniganda and @ncgjbr-ai for reporting this #32 #34)
- Fixed search suggestions flickering every time the character changes. (Thanks to @snashyturner for reporting this #36)
- Fixed others that I forget.

---

## Extra Changes

- Updated Terms of Service.
- Updated Privacy Policy.

---

# v1.1.3

*Because Clint Barton is back in Avengers Secret Wars, the goat himself, here's the update for Clint Browser.*

---

## What's New

### Bookmarks Rework

The bookmark system has been completely reworked. It now supports folders and nested folders, allowing you to organize your bookmarks more efficiently.

- Added the ability to import and export bookmarks to HTML. (Thanks to @ncgjbr-ai for requesting this) #26

---

### Tab Switching

Added the ability to switch tabs by swiping on the search bar. (Thanks to @brnwlshubh for requesting this) #25

---

### Home Screen Shortcuts

- Added the ability to create home screen shortcuts. (Thanks to @reusting for reporting this) #27
- Added **Frameless** mode in Browser Settings.

Frameless mode hides the search bar and bottom bar when a shortcut is opened from the home screen.

---

### UI Improvements

- Reduced the popup menu max size to 90% of the screen so it doesn't get pushed toward the bottom of the screen.
- Moved the download option higher on `ImageLongPressSheet.kt`.
- The app now uses its own UI that matches the app theme when a website requests a selector, instead of the plain WebView built-in design. The app will now use a Compose selector.
- Added **Hide Navigation Bars** option in Look and Feel settings.

---

### Customizable Menu

Added the ability to reorder or hide all action buttons in the menu except Settings. You can find this in **Settings → Look and Feel → Customize Menu**.

---

## Bug Fixes

- Fixed an issue where, when paused and resumed, the website header would become stuck and broken. (Thanks to @deniganda for reporting this) #27
- Fixed the bug where you can't see what you're typing when "Hide Status Bar" is off in search overlay.
- Fixed a bug where tab images were getting cleared.

> *Maybe that's it. I forget, guys.*

---

## Dependency Updates

- Bump `com.android.application` from 9.3.1 to 9.3.2 by @dependabot[bot] in #29
- Bump `gradle-wrapper` from 9.7.0 to 9.7.1 by @dependabot[bot] in #28

---

> *Btw, I'm very sorry this is such a small update. Like I said, I'm a college student now, and yeah, the schedule is not looking good. Transportation fees as well, man. This college is killing me.*

---

# v1.1.2

---

## What's New

### Website Blocker

Added a high-performance website blocker that compiles domain blocklists into a compact 64-bit hash database. It blocks websites before they even load.

The engine is written in C++, so it's fast and uses very little RAM.

To find it, go to **Settings → Browser Settings → Website Blocker**.

The UI is very similar to Quiver Guard.

The goal is to allow users to block websites they don't want to access. You can block websites by category, including:

- Abuse
- Ads
- Crypto
- Drugs
- Fraud
- Gambling
- Malware
- Phishing
- Piracy
- Pornography
- Ransomware
- Redirects
- Scams
- Social
- Torrents
- Tracking

You can also manually add additional websites.

Unlike Quiver Guard, the Website Blocker blocks the entire website/page, rather than individual resources or sub-resources on a page.

---

### Quiver Guard

Quiver Guard has been moved to **Browser Settings**, alongside the new Website Blocker.

Changed the Quiver Guard icon to a security icon, while the Website Blocker uses the shield icon.

---

### Search Suggestions API

Added a new Search Suggestions API setting under **Browser Settings**.

You can now choose between:

- DuckDuckGo — Default
- Google

---

### Backup & Restore

- Improved Backup & Restore with a real progress bar instead of a spinner, allowing you to see the actual progress of the operation.
- Backup encryption and decryption now display real-time progress instead of showing generic messages such as "Creating backup" or "Reading backup."
- Improved backup decryption speed.
- Backup & Restore now includes Website Blocker data, including downloaded blocklists, compiled hosts, and other Website Blocker data.

---

## Other Changes

- Changed the download link update check from requiring an exact byte-for-byte file size match to allowing a 0.1% difference.

This helps prevent issues when a download link expires or its headers change, causing a small difference in the reported file size even though the downloaded file is still valid.

- Updated the Privacy Policy and Terms of Service.
- Updated the About page to include the new library information.
- Updated ATTRIBUTION.md.
- Added Keep Android Open to the About page.

---

## Bug Fixes

- Fixed an issue where the fast scroller indicator could trigger when simply scrolling instead of holding the indicator.
- Fixed visual glitch in quiver guard
- Fixed crush on setup activity

---

# v1.1.1

---

## What's New

### Backup & Restore

Backup & Restore has been added to Settings. You can now back up and restore your important browser data using a single backup file.

You can choose exactly what you want to include in your backup:

- Settings
- Tabs
- Downloads
- Quiver Guard
- Browser Cookies
- Bookmarks
- Search History
- Update Settings
- Other supported app data

You don't have to back up everything. A selection dialog lets you choose which categories you want to include.

### Secure Backup & Restore

Because backups can contain sensitive information, especially browser cookies, Clint requires device authentication whenever you perform a backup or restore.

Before starting a backup or restore, Clint will ask you to verify your identity using your device's available security method, such as:

- PIN
- Password
- Pattern
- Fingerprint
- Face authentication

This helps prevent someone else with access to your unlocked device from performing a backup or restoring potentially sensitive data.

### Encrypted Backups

You can also choose to encrypt your backup with a password.

When encryption is enabled, your password is not used directly as the encryption key. Instead, Clint derives a strong 256-bit encryption key using Argon2id, a password-hashing algorithm designed to make password-guessing attacks more expensive.

The encryption process works like this:

```

Your Password
↓
Argon2id
↓
256-bit Encryption Key
↓
AES-256-GCM
↓
Encrypted Backup File

```

AES-256-GCM is used to encrypt the actual backup data. It provides both encryption and authentication, meaning that unauthorized modifications to the backup can be detected.

Each encrypted backup also uses unique cryptographic values, such as a randomly generated salt and encryption nonce. The information required to derive the encryption key and decrypt the backup is stored as metadata in the backup file, but your password is never stored.

### Why Argon2id + AES-256-GCM?

The two algorithms have different jobs:

- **Argon2id** protects against password-guessing attacks by making each password attempt more computationally and memory intensive.
- **AES-256-GCM** encrypts the actual backup data and provides authentication to detect tampering.

This means that even if someone obtains your encrypted backup file, they still need your password to decrypt it.

However, encryption cannot make a weak password strong. Avoid passwords such as "password123" or other easily guessed passwords. A long, unique passphrase provides much better protection.

> **Important:** If you forget the password used to encrypt a backup, the encrypted data cannot be recovered by Clint. Keep your backup password somewhere safe.

### Portable Backup

All selected data is packaged into one backup file, making it easy to store, transfer, or restore later.

Encrypted backups remain protected even when the backup file is copied to another device or stored somewhere outside your phone.

**Your data stays yours. Your backup stays under your control.**

---

### Custom Download Location Check

Clint now checks whether the configured custom download location still exists and whether the app still has the required SAF permission to access it.

This helps prevent download failures when the selected folder has been deleted, moved, or when access to it has been revoked.

---

## Bug Fixes

- Fixed an issue where Download Settings was opened when pressing Back from Settings instead of returning directly to the Download page.
- Fixed the same issue in the Browser menu. Long-pressing Download or Data Saver now correctly returns to the Browser menu instead of opening their respective settings pages.
- Fixed the Skip button in the setup page appearing too dark. It now uses the same color as the other buttons.
- Fixed an issue where the download Snackbar appeared even when the download had not actually started. The Snackbar now only appears when the download successfully starts.

---

## Dependency Updates

- Bump `androidx.compose:compose-bom` from 2026.06.01 to 2026.08.00 by @dependabot[bot] in #24
- Bump `androidx.webkit:webkit` from 1.16.0 to 1.17.0 by @dependabot[bot] in #23
- Bump `androidx.appcompat:appcompat` from 1.7.1 to 1.8.0 by @dependabot[bot] in #22
- Bump `com.squareup.okhttp3:okhttp` from 5.4.0 to 5.5.0

---

# v1.1.0

---

## What's New

- **Added a new tab menu: Tab Grid.**  
  You can switch to it under **Settings → Look and Feel → Tab Menu → Tab Grid**.  
  Tab Grid will be the default when the app is opened for the first time. The old tab sheet is still available as an option in the same setting.
 
---

# v1.0.9

---

## What's New

- Added multi-selection, removal, search, and fast scroller to Manual Filter in Quiver Guard.
- Redesigned the Menu, LinkLongPressSheet, and ImageLongPressSheet.
- Added language support. You can help translate Clint Browser into your language by downloading the `strings.xml` file and editing the translations yourself.
- Added Filipino translation. Thanks to my friend **Reneil** for helping me out!
- Replaced download notification toasts with snackbars, including a View button.

---

## Bug Fixes

- Fixed an issue where entries couldn't be deleted from Manual Filter.
- Fixed scrolling issues in the LinkLongPressSheet and ImageLongPressSheet content previews on landscape mode, foldables, tablets, and other large-screen devices.
- Fixed the web progress bar remaining visible even when the page was no longer loading.
- Fixed search suggestions and visited pages being recorded during Incognito mode.

---

# v1.0.8

> This update focuses on Clint Browser's transition from XML-based Views to Jetpack Compose.

---

## What's New

- **Full Jetpack Compose Migration** — Converted the app's UI from XML-based Views to Jetpack Compose.
- **Updated Material Icons** — Replaced the previous icons with icons from the extended Material Icons library.
- **Improved Search UI** — Refined the appearance and overall presentation of the Search overlay/Search View.
- **Adaptive Layouts** — Added adaptive layouts to better support different screen sizes and form factors, including large screens and landscape mode.
- **Removed Custom Toasts** — Removed the custom Toast implementation and switched back to the standard Android Toast system.
- **Visual Improvements** — Some UI elements may look slightly different throughout the app as a result of the migration to Compose.
- **Improved Backward Compatibility** — Enhanced support for older Android versions.

---

## Dependency Updates

- **Updated adblock-rust** — Updated adblock-rust from 0.13.0 to 0.13.2.
- **Updated uBlock Origin Resources** — Updated the bundled uBlock Origin resources to version 1.73.0.
- **Updated Android Gradle Plugin** — Bumped `com.android.application` from 9.3.0 to 9.3.1 in [#19](https://github.com/jhaiian/ClintBrowser/pull/19) by [@dependabot[bot]](https://github.com/dependabot[bot]).
- **Updated Gradle Wrapper** — Bumped Gradle from 9.6.1 to 9.7.0 in [#20](https://github.com/jhaiian/ClintBrowser/pull/20) by [@dependabot[bot]](https://github.com/dependabot[bot]).

---

## A Major Transition

This update took a long time to complete because I essentially had to rewrite the entire UI codebase.

Looking back, migrating to Jetpack Compose from the beginning would have saved me a lot of time. Definitely a rookie mistake.

That said, the transition is finally complete, and development feels much easier now that Clint Browser is fully using Jetpack Compose.

This release is mainly focused on the transition to Compose rather than being a complete visual redesign of every screen.

---

## What's Next

I still have a lot of updates and ideas planned for Clint Browser, including some bigger features that will probably take quite a while to develop.

For now, though, I want to focus on improving what's already there before adding more new features. I'll be spending the next few updates cleaning up the existing UI, making everything look more polished, and improving the overall user experience.

Now that the Compose migration is finally out of the way, these smaller updates should come much faster... though I can't promise that because college has already begun, and yeah, I'm gonna get busy.

---

# v1.0.7

> This update mainly enhances and fixes the download manager.

## New Features

### Measurement System
- Binary System (Default): 1 GB = 1024 MB
- Decimal System: 1 GB = 1000 MB

All file size displays in the app (including Quiver Guard and GitHub updates) now adapt to your chosen system.

### Scheduled Downloads (Download Settings)
- Set a start and end time for a download.
- The "Schedule this download" option appears only in the download dialog.
- Pick a date and time to begin the download.

**Important:** Auto‑start and auto‑pause may not work reliably in the background – this depends on your OS optimisations. For best results, keep the app open and on screen, reduce brightness, and disable auto‑sleep. Even then, the download link might expire before it starts (many links do).

### Download Speed Limit
You can now set a speed limit in KB/s or MB/s. The measurement system (binary or decimal) applies here too.

### Change Settings During an Active Download
You can now adjust these options while a download is running:
- Retry
- Unmetered only
- Speed limit

Other settings are unsafe to change mid‑download – they could corrupt the file.

### All File Access Permission (GitHub releases only)
This permission is **not** requested automatically. To enable it:
1. Go to **Download Settings** → scroll to the **Permission** section (formerly labelled "Battery").
2. Tap it – the app will take you to system settings where you can grant All File Access.

Why this matters:  
Previously, the app always used **SAF mode** for custom locations – that writes the file to the app's data folder, copies it to your chosen destination, then deletes the original. This doubles the storage needed.

With All File Access granted (on Android 11+), the app writes directly to your chosen location – no extra copy, no double storage.  
- On **Android 10**, neither method works perfectly – sorry.  
- On **Pie and older**, the condition is always true because external storage permission is enough.

## Changes

- Removed all caching‑related settings (they were causing bugs).
- Removed "Block Trackers" from Privacy & Policy – Quiver Guard already handles tracker blocking (it blocks the 17 most common trackers), so this option was redundant.
- SAF mode progress is now split into two phases: copy, then delete the temporary file   it's no longer a single "move" operation.
- Update ToS
- Update Privacy and policy

## Bug Fixes

- Fixed downloads randomly restarting midway.
- Fixed progress decreasing when pausing and resuming.
- Fixed crash when using the Share button in Downloads.
- Fixed "Open Folder"  it now shows a clear error message with the folder path instead of failing silently.
- Fixed progress getting stuck when you immediately close a tab that was opened by a pop‑up.

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

## Dependency Updates

- Bump `androidx.lifecycle:lifecycle-runtime-ktx` from 2.8.7 to 2.11.0 by [@dependabot[bot]](https://github.com/dependabot[bot]) in [#14](https://github.com/jhaiian/ClintBrowser/pull/14)
- Bump `androidx.documentfile:documentfile` from 1.0.1 to 1.1.0 by [@dependabot[bot]](https://github.com/dependabot[bot]) in [#13](https://github.com/jhaiian/ClintBrowser/pull/13)
- Bump `androidx.activity:activity-ktx` from 1.10.1 to 1.13.0 by [@dependabot[bot]](https://github.com/dependabot[bot]) in [#12](https://github.com/jhaiian/ClintBrowser/pull/12)
- Bump `com.android.application` from 9.2.0 to 9.3.0 by [@dependabot[bot]](https://github.com/dependabot[bot]) in [#11](https://github.com/jhaiian/ClintBrowser/pull/11)
- Bump `org.jetbrains.kotlinx:kotlinx-coroutines-android` from 1.9.0 to 1.11.0 by [@dependabot[bot]](https://github.com/dependabot[bot]) in [#10](https://github.com/jhaiian/ClintBrowser/pull/10)

## New Contributors

- [@dependabot[bot]](https://github.com/dependabot[bot]) made their first contribution in [#14](https://github.com/jhaiian/ClintBrowser/pull/14)

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

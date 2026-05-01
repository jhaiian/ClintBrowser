## Contributing

Contributions, bug reports, and feature requests are welcome.

1. [Open an issue](https://github.com/jhaiian/ClintBrowser/issues) to report a bug or suggest a feature
2. Fork the repo and create a branch for your change
3. Submit a pull request with a clear description

To report a crash, use the built-in **Debug & Crash Reports** screen in Settings. It generates a pre-filled GitHub issue template with your device info and crash log.

---

## Building from Source

### Prerequisites
- Android Studio or JDK 17
- Android SDK (API 36)
- Gradle 9.4.1

### Steps

```bash
git clone https://github.com/jhaiian/ClintBrowser.git
cd ClintBrowser
```

Create a `local.properties` file in the root with your SDK path:

```properties
sdk.dir=/path/to/your/android/sdk
```

For a signed release build, also add:

```properties
signingConfig.storeFile=app/release_keystore.jks
signingConfig.storePassword=your_password
signingConfig.keyAlias=your_alias
signingConfig.keyPassword=your_password
```

Then build:

```bash
chmod +x gradlew
./gradlew assembleRelease
```

APKs will be output to `app/build/outputs/apk/release/`.

---

## Project Structure

```
ClintBrowser/
├── app/src/main/res/
│   ├── drawable/                     # Vector icons and shape drawables
│   ├── layout/
│   │   ├── activity_*.xml            # Activity root layouts
│   │   ├── bottom_sheet_*.xml        # Bottom sheet layouts
│   │   ├── dialog_*.xml              # Dialog layouts
│   │   ├── fragment_*.xml            # Fragment layouts
│   │   ├── item_*.xml                # RecyclerView item layouts
│   │   └── popup_menu.xml            # Overflow popup menu layout
│   ├── menu/
│   │   └── main_menu.xml             # Options menu
│   ├── mipmap-*/                     # Launcher icons (hdpi → xxxhdpi + anydpi-v26)
│   ├── values/
│   │   ├── arrays.xml                # String arrays (search engines, DNS providers)
│   │   ├── attrs.xml                 # Custom theme attributes
│   │   ├── colors.xml                # Color palette
│   │   ├── strings.xml               # All user-facing strings
│   │   └── themes.xml                # App themes and style definitions
│   └── xml/
│       ├── file_paths.xml            # FileProvider paths for downloads and camera
│       ├── *_preferences.xml         # Preference screen definitions
│       └── preferences.xml           # Root preference hierarchy
├── app/src/main/assets/
│   └── JavaScript/
│       ├── bottom_nav_detector.js    # Detects bottom navigation bars for scroll behavior
│       ├── dark_mode.js              # Injects dark mode styles into pages
│       ├── desktop_mode.js           # Overrides user-agent and viewport for desktop
│       ├── image_alt_text.js         # Reads alt text of a long-pressed image
│       ├── link_text.js              # Reads the text content of a long-pressed link
│       ├── link_touch_tracker.js     # Tracks the last touched link for long-press context menus
│       ├── scroll_tracker.js         # Tracks scroll position for hide-bars behavior
│       └── video_dimensions.js       # Detects video dimensions for fullscreen sizing
├── app/src/main/java/com/jhaiian/clint/
│   ├── app/
│   │   └── ClintApplication.kt       # Application class
│   ├── base/
│   │   └── ClintActivity.kt          # Base activity (theming, dialogs)
│   ├── bookmarks/
│   │   ├── Bookmark.kt               # Bookmark data model
│   │   ├── BookmarkManager.kt        # Local bookmark storage
│   │   ├── BookmarksActivity.kt      # Bookmarks screen
│   │   └── BookmarksAdapter.kt       # Bookmarks list adapter
│   ├── browser/
│   │   ├── ContentPreviewSheet.kt    # Bottom sheet for previewing a page or image URL
│   │   ├── ImageLongPressSheet.kt    # Bottom sheet shown on image long-press
│   │   ├── JsAssetLoader.kt          # JavaScript asset loading
│   │   ├── LinkLongPressSheet.kt     # Bottom sheet shown on link long-press
│   │   ├── MainActivity.kt           # Browser activity, state, and lifecycle
│   │   ├── MainDownloadPermissionDelegate.kt # Runtime storage permission handling for downloads
│   │   ├── MainFileChooserDelegate.kt# File chooser and camera capture logic
│   │   ├── MainFullscreenDelegate.kt # Video fullscreen enter/exit logic
│   │   ├── MainImageDelegate.kt      # Image long-press action handlers
│   │   ├── MainLinkDelegate.kt       # Link long-press action handlers
│   │   ├── MainScrollDelegate.kt     # Scroll-hide bars and swipe refresh setup
│   │   ├── MainTabDelegate.kt        # Tab open, close, restore, and switching
│   │   ├── MainUiDelegate.kt         # WebView setup, address bar, UI state updates
│   │   ├── MainWebViewDelegate.kt    # WebView configuration and settings apply
│   │   ├── MenuBottomSheet.kt        # Browser action menu bottom sheet
│   │   └── PreviewLinkLongPressSheet.kt # Bottom sheet shown on link long-press inside a content preview
│   ├── crash/
│   │   ├── CrashHandler.kt           # Local crash reporting
│   │   └── CrashReportFragment.kt    # Crash log viewer UI
│   ├── downloads/
│   │   ├── ClintDownloadManager.kt   # Custom download engine
│   │   ├── DownloadActionReceiver.kt # Notification action receiver
│   │   ├── DownloadsActivity.kt      # Downloads screen
│   │   └── DownloadsAdapter.kt       # Downloads list adapter
│   ├── network/
│   │   └── DohManager.kt             # DNS over HTTPS
│   ├── settings/
│   │   ├── AboutFragment.kt          # About screen
│   │   ├── DohSettingsFragment.kt    # DNS over HTTPS settings
│   │   ├── GeneralSettingsFragment.kt# General settings (scroll-hide, display)
│   │   ├── LookAndFeelFragment.kt    # Appearance & theme settings
│   │   ├── MainSettingsFragment.kt   # Settings root screen
│   │   ├── MiscFragment.kt           # Miscellaneous settings screen
│   │   ├── PrivacySettingsFragment.kt# Privacy & security settings
│   │   ├── SettingsActivity.kt       # Settings host activity
│   │   └── UpdateSettingsFragment.kt # Update channel settings
│   ├── setup/
│   │   └── SetupActivity.kt          # First-launch wizard
│   ├── tabs/
│   │   ├── BrowserTab.kt             # Tab data model
│   │   ├── TabAdapter.kt             # Tab switcher adapter
│   │   ├── TabManager.kt             # Multi-tab state
│   │   ├── TabPreview.kt             # Tab thumbnail model
│   │   └── TabSwitcherSheet.kt       # Bottom sheet tab switcher
│   ├── ui/
│   │   ├── ClintToast.kt             # Custom themed toast
│   │   ├── DocumentViewer.kt         # In-app document viewer
│   │   ├── FaviconCache.kt           # Favicon download and memory/disk cache
│   │   ├── ThemeRevealHolder.kt      # Shared bitmap for theme-change animation
│   │   ├── ThemeRevealOverlay.kt     # Circular reveal overlay for theme changes
│   │   └── ThemeSwatchUtils.kt       # Builds theme swatch drawables for the theme picker
│   ├── update/
│   │   └── UpdateChecker.kt          # In-app update checker
│   └── webview/
│       ├── ClintSwipeRefreshLayout.kt# Custom swipe refresh with scroll callbacks
│       ├── ClintWebChromeClient.kt   # Progress, title, fullscreen updates
│       ├── ClintWebViewClient.kt     # Request interception, tracker blocking
│       └── WebViewCookieJar.kt       # OkHttp cookie integration
├── Update/
│   ├── Stable.json                   # Stable channel update manifest
│   └── Beta.json                     # Beta channel update manifest
├── fastlane/
│   └── metadata/android/en-US/
│       ├── changelogs/
│       │   ├── 7.txt
│       │   ├── 8.txt
│       │   ├── 9.txt
│       │   └── 10.txt
│       ├── images/
│       │   ├── icon.png
│       │   └── phoneScreenshots/
│       │       ├── 1.jpg
│       │       ├── 2.jpg
│       │       ├── 3.jpg
│       │       ├── 4.jpg
│       │       ├── 5.jpg
│       │       └── 6.jpg
│       ├── full_description.txt
│       ├── short_description.txt
│       └── title.txt
├── docs/
│   └── clint_logo_backgroud.jpg
├── CHANGELOG.md
├── Contributing.md
├── Contributors.md
├── LICENSE
├── PRIVACY_POLICY.md
├── README.md
└── TERMS_OF_SERVICE.md
```

## CI/CD Secrets

In order to make the workflow work, you need the following secrets:

**Secret 1: `BASE_64_SIGNING_KEY`**

```bash
# Convert your keystore to base64
base64 -w 0 your_keystore.jks
# Copy the entire output as the secret value
```

**Secret 2: `LOCAL_PROPERTIES`**

```properties
signingConfig.storeFile=app/release_keystore.jks
signingConfig.storePassword=your_password
signingConfig.keyAlias=your_alias
signingConfig.keyPa
ssword=your_password
```

To make your `release.yml` workflow work, set up the following **secrets** in your repository:

| Secret Name               | Purpose                                                        |
|----------------------------|----------------------------------------------------------------|
| `BASE_64_SIGNING_KEY`      | Encoded release keystore for signing APKs.                    |
| `LOCAL_PROPERTIES`         | Contents of your `local.properties` for SDK path and signing configs. |
| `GIT_USERNAME`             | Your GitHub username for automated commits.                   |
| `GIT_EMAIL`                | Your GitHub email for automated commits.                      |
| `PERSONAL_GITHUB_TOKEN`    | GitHub Personal Access Token (PAT) for pushing commits/tags.  |

---

## How to create a Personal Access Token (PAT)

Your workflow needs a GitHub token to push commits and tags. Follow these steps:

1. Go to **GitHub Settings → Developer settings → Personal Access Tokens → Tokens (classic)**.
2. Click **Generate new token → Generate new token (classic)**.
3. Give the token a name (e.g., `Clint Browser CI`).
4. Set an expiration (recommended: 90 days or no expiration if you rotate it regularly).
5. Under **Scopes**, check:  
   - `repo` → Full control of private repositories
6. Click **Generate token**.
7. Copy the token immediately and add it as the secret `PERSONAL_GITHUB_TOKEN` in your repository.

## Contributing

Contributions, bug reports, and feature requests are welcome.

1. [Open an issue](https://github.com/jhaiian/Clint-Browser/issues) to report a bug or suggest a feature
2. Fork the repo and create a branch for your change
3. Submit a pull request with a clear description

To report a crash, use the built-in **Debug & Crash Reports** screen in Settings. It generates a pre-filled GitHub issue template with your device info and crash log.

---

## Building from Source

### Prerequisites
- Android Studio or JDK 17
- Android SDK (API 34)
- Gradle 8.6+

### Steps

```bash
git clone https://github.com/jhaiian/Clint-Browser.git
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
├── app/src/main/java/com/jhaiian/clint/
│   ├── MainActivity.kt           # Browser UI, tab management
│   ├── ClintWebViewClient.kt     # Request interception, tracker blocking
│   ├── ClintWebChromeClient.kt   # Progress, title updates
│   ├── TabManager.kt             # Multi-tab state
│   ├── DohManager.kt             # DNS over HTTPS
│   ├── ClintDownloadManager.kt   # Custom download engine
│   ├── UpdateChecker.kt          # In-app updates
│   ├── CrashHandler.kt           # Local crash reporting
│   ├── SetupActivity.kt          # First-launch wizard
│   └── ...
├── Update/
│   ├── Stable.json               # Stable channel update manifest
│   └── Beta.json                 # Beta channel update manifest
├── docs/
│   └── clint_logo.png
├── CHANGELOG.md
├── PRIVACY_POLICY.md
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

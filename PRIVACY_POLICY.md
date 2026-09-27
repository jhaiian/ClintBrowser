# Privacy Policy for Clint Browser

*Last updated: September 28, 2026*

## Overview

Clint Browser is an open-source Android web browser built with privacy in mind. This policy explains what data is collected, what isn't, and how the app handles your information.

---

## WebView Implementation

Clint Browser doesn't have its own rendering engine. Instead, it uses whatever Android WebView implementation is installed and set as the active provider on your device. On most phones that's Android System WebView from Google, based on Chromium. But Android lets the WebView provider be swapped out, and some devices or custom ROMs use a different one entirely, for example GrapheneOS ships its own hardened WebView called Vanadium instead of Google's. Clint Browser doesn't choose, bundle, or ship a WebView itself; it simply asks Android for "the system WebView" and renders pages through whichever one you or your device has set.

This matters for privacy because whoever provides your WebView, whether that's Google, GrapheneOS, or another vendor, handles the actual page rendering, JavaScript execution, and some networking behavior at a level below the app itself. We have no visibility into and no control over what that component does internally. If you want to know exactly how your WebView provider handles data, you'll need to check their privacy policy, not ours, since it's a separate piece of software maintained outside of Clint Browser.

A few things we do control on top of it, though:

- We explicitly disable Safe Browsing inside the WebView (`safeBrowsingEnabled = false`), so page URLs aren't sent off for a Safe Browsing check as you browse, regardless of which provider you're using.
- We turn off WebView's local storage and caching in Incognito tabs, so incognito browsing doesn't persist to disk through the engine.
- Keeping the WebView component itself updated (for security patches and new web standards) is handled by your device, typically through system updates or your WebView provider's own update channel, not by Clint Browser.

---

## Data We Do Not Collect

Clint Browser does not collect, store, transmit, or share any personal data. There are no analytics, no crash reporting services, no advertising SDKs, and no backend servers. The developer has no access to your browsing activity, search history, or any other information generated while using the app.

---

## Browsing Data

All browsing data — including history, cookies, cached content, and site storage — is stored locally on your device only. You can clear this data at any time through your device's app settings. Incognito tabs do not save cookies, cached content, or browsing history to your device.

---

## Search History

When you perform searches in the address or search bar, Clint Browser saves your search history locally to improve your experience. **All search history data is stored exclusively on your local device using SQLite**, a lightweight file-based database. This data never leaves your device — it is not uploaded to any server, shared with any third party, or transmitted anywhere else.

You can clear your search history at any time through the app's settings or by clearing the app's storage from your device settings.

---

## Search Engine

You can choose which search engine handles the searches you type in the address or search bar, in Settings:

- **DuckDuckGo** (default)
- **Brave Search**
- **Ecosia**
- **Google**
- **Custom**, a URL template you enter yourself

When you search, your query is sent as part of the request URL to whichever engine you've selected. If you configure a custom engine, your query is sent to whatever URL you entered, since the app has no way to know what that service does with it. Review that service's own privacy policy before using it.

---

## Search Suggestions

When you type in the address or search bar, Clint Browser shows real-time search suggestions to help you find what you're looking for faster.

You can choose which provider these suggestions come from in Settings:

- **DuckDuckGo** (default), fetched from `https://duckduckgo.com/ac/?q=`
- **Google**, fetched from `https://www.google.com/complete/search?client=firefox&q=`
- **Custom**, a URL template you enter yourself

**What this means for your privacy:**
- The text you type is sent only to whichever suggestion provider you've selected, and only to retrieve suggestion results
- No search history, cookies, or personal identifiers from Clint Browser are included
- Neither provider receives requests associated with your Clint Browser identity, since the app has none to send
- If you configure a custom suggestions provider, each keystroke you type may be sent to that URL as you type, so only point this at a service you trust

Your saved search history is completely separate and never sent to DuckDuckGo, Google, or any other service.

You can review DuckDuckGo's privacy policy at https://duckduckgo.com/privacy and Google's privacy policy at https://policies.google.com/privacy

---

## Favicons

Website icons (favicons) are loaded to help identify sites in tabs, history, and bookmarks.

- First, Clint Browser tries to load the favicon directly from the website  
- If unavailable, it falls back to DuckDuckGo’s favicon service  

When using the fallback service, only the domain name is sent to retrieve the icon. No personal data or browsing history is included.

You can review DuckDuckGo's privacy policy at https://duckduckgo.com/privacy

---

## Bookmarks

Bookmarks are stored locally on your device only using SQLite. They are never synced, uploaded, or shared. You can delete them at any time through the app or by clearing app storage.

---

## Backup & Restore

Clint Browser lets you back up your data, things like bookmarks, history, and settings, into a single file you keep for yourself. Here's how it actually works, in plain terms:

- **You choose where it goes.** When you create a backup, Android's own file picker opens and you pick the folder: your device storage, an SD card, wherever. Clint Browser doesn't send the file anywhere on its own, and there's no cloud sync tied to it.
- **Password protection is optional, and it's real encryption.** If you set a password, the backup is locked with AES‑256‑GCM, the same kind of encryption used by banks and messaging apps. Your password itself is never used directly. It's run through Argon2id first, which is specifically designed to make password-guessing slow and expensive, even for someone with serious hardware. If you leave the password blank, the backup is saved without encryption, which is convenient but means anyone with the file can read it, so treat it like any other unprotected file.
- **We can't help you recover a lost password.** Because the encryption key comes entirely from your password, and we never store or see that password, there's no "forgot password" option and no backdoor. If it's lost, the backup is gone. Please choose something you'll remember, or write it down somewhere safe.
- **Restoring works the same way in reverse.** You pick a backup file, enter the password if it has one, and everything gets read and restored locally on your device. Nothing is uploaded to check the password or verify the file. It either decrypts correctly or it doesn't.

In short: your backups never leave your control unless you move them somewhere yourself.

**Why we ask for your phone's password or biometric unlock first.** Before you can create or restore a backup, Clint Browser asks you to confirm it's really you, using your fingerprint, face unlock, or your device PIN/pattern/password, whichever you already use to unlock your phone. This isn't sent anywhere or stored by us; Android checks it locally and just tells the app "yes, this is the device owner." We added this step because a backup can contain sensitive stuff, like saved history and cookies that keep you logged into sites, and we didn't want someone who picks up your unlocked phone to be able to quietly export all of that or overwrite it with a restore in a few taps. It's an extra lock on top of the backup password, not a replacement for it.

---

## Downloads

Files you download are saved directly to your device. Clint Browser does not upload, scan, or transmit downloaded files.

---

## Media Capture

Media Capture detects media resources used by websites, such as video, audio, subtitles, and streaming formats like HLS/M3U8 and DASH, and lets you download them directly. For example, when a page plays a video, Media Capture inspects the page and its network requests to find available media streams and lists them as downloadable options. Some sites serve video and audio as separate streams, and where supported, Media Capture can combine them into a single file.

All of this detection happens locally on your device. Clint Browser does not intentionally upload captured media, browsing data, or any information about detected media to an external server.

That said, this privacy statement is not an absolute guarantee of privacy or security. Media Capture works by interacting with webpage content, network requests, Android, WebView, and other components outside Clint Browser's control. Websites may use their own tracking, DRM, authentication, or other technologies that affect how Media Capture behaves. Media Capture is provided as-is, and Clint Browser does not guarantee that it will detect every type of media, prevent websites from tracking you, or provide complete privacy or security while using the feature.

---

## Website Blocker

Website Blocker lets you block entire categories of sites (ads, malware, piracy, tracking, and more) at the domain level.

- **Downloading category lists requires network access.** When you enable a category, Clint Browser downloads that category's host list directly from its source on GitHub (either `blocklistproject.github.io` or `raw.githubusercontent.com`, depending on the category). This means your device's IP address is visible to GitHub for that request, the same as visiting any website. No personal data, browsing history, or identifiers from Clint Browser are sent along with it.
- **Everything else happens locally.** Once downloaded, host lists are compiled into a local blocking engine entirely on your device. Checking whether a site should be blocked never involves a network request or a third-party server.
- **Custom websites you add yourself** are stored locally on your device and are never uploaded anywhere.
- You can review exactly which sources are used in `Attribution.md` in the app's repository.

---

## Quiver Guard (Ad blocker)

All downloaded filter lists are stored and processed locally on your device using the adblock-rust library. No filter lists, browsing data, or other information are sent to any servers. All ad-blocking operations are performed entirely on your device, helping protect your privacy.

---

## User Scripts

User scripts are pieces of JavaScript you can install to change how pages look or behave. Here's how they interact with your data:

- **Script storage is local.** Script code, its enabled state, and matching rules are stored on your device using SQLite. They are never uploaded or synced anywhere by Clint Browser.
- **Installing from a URL fetches that URL.** If you add a script by pasting a link, Clint Browser downloads the script's source directly from that URL, sending the site's cookies (if any) and a standard browser user agent, the same as visiting the page normally. A script's declared dependencies (`@require` and `@resource` files) are fetched the same way from their own URLs.
- **Checking for updates repeats that fetch.** If a script was installed from a URL, checking for updates contacts that same URL again to see if the source has changed.
- **Scripts can store their own local values.** Scripts that use `GM.setValue` or `GM.getValue` have their data stored locally on your device, scoped to that script. This data is never transmitted anywhere by Clint Browser.
- **Scripts can make their own network requests.** A script that uses `GM.xmlHttpRequest` can request any URL it's written to request, including across origins. This request is made directly by the script's own code, not by Clint Browser, so what data it sends and where it goes depends entirely on that script.
- **Scripts can request notification access.** If a script uses `GM.notification`, Android's normal notification permission prompt applies the same way it would for a website.
- **Scripts run with page level access.** Once installed and matched to a site, a script can read and modify the content of the pages it runs on. The developer does not review or vet the scripts you install, so only install scripts from sources you trust.

---

## Website Permissions

Clint Browser allows websites to request access to certain device features. These permissions are fully controlled by you and can be configured in **Site Settings**.

When a website requests access, you can choose how the browser handles it:

- **Ask first** — The browser will show a prompt every time a website requests access (Default)  
- **Always deny** — All website requests for that permission will be blocked automatically without prompting  
- **Always allow** — All website requests for that permission will be granted automatically without prompting  

### Available Website Permissions

Websites may request access to:

- **Camera** — Used for taking photos or recording video directly from websites  
- **Microphone** — Used for voice input, calls, or audio recording features on websites  
- **Location** — Used for location-based services such as maps or nearby results  
- **Notifications** — Used by websites to send push notifications if you allow them  
- **Clipboard** — Used when a website wants to read text from your clipboard, for example to let you paste into a form with one tap

### Site Exceptions

You can define site-specific exceptions that override the default behavior.

- Each website can have its own permission rule  
- These overrides take priority over global settings  
- If you select “Don’t ask again” when responding to a prompt, the website is automatically added to Site Exceptions  

You can manage or remove these exceptions anytime from Site Settings.

---

## App Permissions

Clint Browser requests the following permissions:

## Internet & Network
- **INTERNET** – Lets the app load websites and download files.
- **ACCESS_NETWORK_STATE** – Checks if you're connected to the internet (Wi‑Fi or mobile data) so downloads can pause when offline and resume when reconnected.

## Storage & Downloads
- **WRITE_EXTERNAL_STORAGE** – Saves downloaded files to your Downloads folder. *(Android 9 and below only)*
- **POST_NOTIFICATIONS** – Shows download progress, completion, and failure alerts in your notification bar.
- **REQUEST_INSTALL_PACKAGES** – Installs APK files downloaded from the download screen and installing updates from GitHub.
- **SCHEDULE_EXACT_ALARM** – Download Scheduler
- **MANAGE_EXTERNAL_STORAGE** – Allow the download manager to write directly to a custom location without requiring SAF (exclusively available in the GitHub Release).

## Camera & Audio
- **CAMERA** – Used when a website asks you to upload a photo and you choose to take one with your camera, or when a website requests camera access for video calls.
- **RECORD_AUDIO** – Used for the voice search button in the browser, and when a website requests microphone access for voice or video calls.
- **MODIFY_AUDIO_SETTINGS** – Required for WebRTC voice/video calls on websites. The WebView needs this to manage audio routing during a call.

## Location
- **ACCESS_FINE_LOCATION** – Used when a website asks for your precise location, e.g., to show nearby places or get directions.
- **ACCESS_COARSE_LOCATION** – Same as above, but used as a fallback when only approximate location is available.

## App & System Integration
- **QUERY_ALL_PACKAGES** – Detects which apps on your device can handle special links, like opening a phone number in your dialer or a store link in the Play Store.
- **FOREGROUND_SERVICE** – Keeps downloading files even when you switch to another app.
- **FOREGROUND_SERVICE_DATA_SYNC** – Works with `FOREGROUND_SERVICE` to tell Android the background activity is a file download.

## Power & Background
- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS** – Prevents Android from pausing downloads when the device is in battery saver or Doze mode.
- **WAKE_LOCK** – Prevents your device from sleeping while a download is in progress so files don't get stuck halfway.
- **RECEIVE_BOOT_COMPLETED** – Checks for unfinished downloads after your device restarts and resumes them automatically.

No permission is used for tracking or data collection.

---

## Data Retention and Deletion

Since no personal data is collected by the developer, there is nothing to delete on our servers. All local data (history, bookmarks, downloads, etc.) is stored on your device. You can delete any or all of it at any time through the app’s settings or by clearing the app’s storage in your device settings.

---

## Children's Privacy

Clint Browser does not knowingly collect any personal information from anyone, including children under the age of 13. The app has no accounts, no sign‑ins, and no data transmission to the developer. If you are a parent or guardian and believe your child has used the app in a way that concerns you, you may contact us, but note that no data has been collected by us.

---

## Third-Party Services

Clint Browser does not include analytics, advertising, or tracking SDKs.

However, the search engine or services you choose (such as DuckDuckGo, Brave Search, Ecosia, Google, or a custom one you configure) may collect data according to their own privacy policies. Clint Browser has no control over those services. Additionally, the search suggestions feature uses DuckDuckGo's or Google's public suggestion API, or a custom one you configure, depending on which provider you've selected, and the favicon fallback feature uses DuckDuckGo's public API, as described above. Any user scripts you install can also communicate with third party services on their own, as described in the User Scripts section above.

---

## Changes to This Policy

Any updates to this privacy policy will be reflected in this document on the GitHub repository. The app always links to the latest version. Your continued use of the app after changes means you accept the updated policy.

---

## Open Source

Clint Browser is fully open source. You can review the source code to verify how the app works at:
https://github.com/jhaiian/ClintBrowser

---

## Contact

If you have questions about this privacy policy, you can reach the developer at `jhaiianbetter@duck.com` or through the community Discord at https://discord.gg/4kUe4yPQ32

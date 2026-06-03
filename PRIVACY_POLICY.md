# Privacy Policy for Clint Browser

*Last updated: June 4, 2026*

## Overview

Clint Browser is an open-source Android web browser built with privacy in mind. This policy explains what data is collected, what isn't, and how the app handles your information.

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

## Search Suggestions

When you type in the address or search bar, Clint Browser shows real-time search suggestions to help you find what you're looking for faster.

These suggestions are fetched from DuckDuckGo using their public suggestion API endpoint at `https://duckduckgo.com/ac/?q=`.

**What this means for your privacy:**
- The text you type is sent to DuckDuckGo only to retrieve suggestion results  
- No search history, cookies, or personal identifiers from Clint Browser are included  
- DuckDuckGo does not store or associate these requests with your identity in a personally identifiable way  

Your saved search history is completely separate and never sent to DuckDuckGo or any other service.

You can review DuckDuckGo's privacy policy at https://duckduckgo.com/privacy

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

## Downloads

Files you download are saved directly to your device. Clint Browser does not upload, scan, or transmit downloaded files.

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

However, the search engine or services you choose (such as DuckDuckGo, Brave Search, or Google) may collect data according to their own privacy policies. Clint Browser has no control over those services. Additionally, the search suggestion and favicon fallback features use DuckDuckGo’s public APIs as described above.

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

# Privacy Policy for Clint Browser

*Last updated: May 11, 2026*

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

## Permissions

Clint Browser requests the following permissions:

- **Internet** — required to browse the web  
- **Network State** — used to check connectivity  
- **Write External Storage** (Android 9 and below) — required to save downloaded files  
- **Post Notifications** (Android 13 and above) — used for download progress and website notifications  
- **Request Install Packages** — used for installing updates or APKs only when explicitly confirmed by the user  
- **Camera** — used only when uploading or capturing content initiated by the user and with website permission  
- **Record Audio** — used for voice search via the system speech service; all processing happens locally on the device  
- **MODIFY_AUDIO_SETTINGS** — used for microphone-related website permissions  
- **ACCESS_COARSE_LOCATION & ACCESS_FINE_LOCATION** — used for website location permissions  
- **Query All Packages** — used only to detect installed apps locally when opening supported links  

No permission is used for tracking or data collection.

---

## Third-Party Services

Clint Browser does not include analytics, advertising, or tracking SDKs.

However, the search engine or services you choose (such as DuckDuckGo, Brave Search, or Google) may collect data according to their own privacy policies. Clint Browser has no control over those services.

---

## Open Source

Clint Browser is fully open source. You can review the source code to verify how the app works at:
https://github.com/jhaiian/ClintBrowser

---

## Changes to This Policy

Any updates to this privacy policy will be reflected in this document on the GitHub repository. The app always links to the latest version.

---

## Contact

If you have questions about this privacy policy, you can reach the developer at `jhaiianbetter@duck.com` or through the community Discord at https://discord.gg/4kUe4yPQ32
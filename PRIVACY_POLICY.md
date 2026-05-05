# Privacy Policy for Clint Browser

*Last updated: May 5 2026*

## Overview

Clint Browser is an open-source Android web browser built with privacy in mind. This policy explains what data is collected, what isn't, and how the app handles your information.

## Data We Do Not Collect

Clint Browser does not collect, store, transmit, or share any personal data. There are no analytics, no crash reporting services, no advertising SDKs, and no backend servers. The developer has no access to your browsing activity, search history, or any other information generated while using the app.

## Browsing Data

All browsing data — including history, cookies, cached content, and site storage — is stored locally on your device only. You can clear this data at any time through your device's app settings. Incognito tabs do not save cookies, cached content, or browsing history to your device.

## Search History

When you perform searches in the address or search bar, Clint Browser saves your search history locally to improve your experience. **All search history data is stored exclusively on your local device using SQLite**, a lightweight file-based database. This data never leaves your device — it is not uploaded to any server, shared with any third party, or transmitted anywhere else. The app has no remote access to your search history.

You can clear your search history at any time through the app's settings or by clearing the app's storage from your device settings.

## Search Suggestions

When you type in the address or search bar, Clint Browser shows real‑time search suggestions to help you find what you're looking for faster.

**These suggestions are fetched from DuckDuckGo** using their public suggestion API endpoint at `https://duckduckgo.com/ac/?q=`. For each keystroke, the browser sends the partial text you have typed to this endpoint, and DuckDuckGo returns suggested search terms.

**What this means for your privacy:**
- The text you type is transmitted to DuckDuckGo solely to retrieve suggestion results.
- No search history, cookies, or personal identifiers from Clint Browser are sent along with these requests.
- DuckDuckGo does not store or log your searches in a personally identifiable way. You can review DuckDuckGo's privacy policy at [duckduckgo.com/privacy](https://duckduckgo.com/privacy).

Your saved **search history** (previous searches stored locally on your device using SQLite) is completely separate from live search suggestions and is never sent to DuckDuckGo or any other server.

## Favicons

The website icons (favicons) you see in Clint Browser — such as those displayed next to website addresses in your history, bookmarks, or open tabs — are loaded automatically to help you identify sites at a glance.

**Where favicons come from:**
- Clint Browser first attempts to load the favicon directly from the website you are visiting, using the standard `favicon.ico` file location provided by that site.
- If a website does not provide its own favicon, the browser falls back to DuckDuckGo's favicon service at `https://icons.duckduckgo.com/ip3/______.ico`, where the blank is filled with the website's domain name (for example, `https://icons.duckduckgo.com/ip3/github.com.ico`).

**What this means for your privacy:**
- When a favicon is loaded from DuckDuckGo's service, your browser requests it from their servers. DuckDuckGo does not use this request to track you or log your browsing activity in a personally identifiable way.
- The website domain is sent to DuckDuckGo only to retrieve the correct icon — no search history, cookies, or other personal data is included.

You can review DuckDuckGo's privacy policy at [duckduckgo.com/privacy](https://duckduckgo.com/privacy).

## Bookmarks

Bookmarks are stored locally on your device only. **All bookmark data is stored exclusively on your local device using SQLite**, the same lightweight file-based database used for search history. They are never synced, uploaded, or shared. No third party has access to your bookmarks.

You can remove individual bookmarks at any time from within the app, or delete all bookmark data by clearing the app's storage through your device settings.

## Downloads

Files you download are saved directly to your device's Downloads folder. Clint Browser does not upload, scan, or transmit downloaded files anywhere.

## DNS over HTTPS (DoH)

If you enable DNS over HTTPS, your DNS queries are sent to the provider you select — either Cloudflare (`dns.cloudflare.com`) or Quad9 (`dns.quad9.net`). This is a network-level privacy feature and is subject to the privacy policy of the provider you choose. DoH is opt-in and disabled by default.

## Permissions

Clint Browser requests the following permissions:

- **Internet** — required to browse the web
- **Network State** — used to check connectivity
- **Write External Storage** (Android 9 and below) — required to save downloaded files
- **Post Notifications** (Android 13 and above) — used to show download progress notifications
- **Request Install Packages** — used in two cases: to install app updates downloaded through the browser's built-in update checker, and to allow you to install APK files you have manually downloaded through the browser. In both cases, installation only happens when you explicitly confirm it. This permission is never used to install software without your knowledge or consent.
- **Camera** — used to allow you to take photos or record video when uploading content through the browser. Camera access is only activated when you initiate an upload that requires it. Clint Browser never accesses your camera in the background or without your direct action.
- **Record Audio** — used for web speech recognition to perform searches using the address bar. When you use voice input, the app accesses your device's built-in speech-to-text service. All audio data is processed locally by your device's speech recognition service — no audio is ever sent to Clint Browser's servers, and none of the audio or transcribed text leaves the app. The app does not record, store, or transmit your voice anywhere.
- **Query All Packages** — This permission allows Clint Browser to see which apps are installed on your device. It is used to detect whether an app (e.g., YouTube, Twitter, Reddit) can handle a link you try to open. That way, when you click a link that works better in a dedicated app, the browser can offer to open it directly in that app instead of the webpage. This permission is not used to track you or collect personal data — it only checks for app names locally on your device, at the moment you click a link.

No permission is used for data collection or tracking purposes.

## Uploads

Any files, photos, or recordings you upload through the browser are sent directly to the website you are uploading to. Clint Browser never receives, stores, or has any access to that content. The website or service you upload to is solely responsible for how it handles your data.

## Third-Party Services

Clint Browser does not integrate any third-party analytics, advertising, or tracking services. The search engine you choose (DuckDuckGo, Brave Search, or Google) may collect data according to their own privacy policies. Clint Browser has no affiliation with or control over those services.

## Open Source

Clint Browser is fully open source. You can review the complete source code at [github.com/jhaiian/ClintBrowser](https://github.com/jhaiian/ClintBrowser) to independently verify these claims.

## Changes to This Policy

Any updates to this privacy policy will be reflected in this document on the GitHub repository. The app always links to the latest version.

## Contact

If you have questions about this privacy policy, you can reach the developer at `jhaiianbetter@duck.com` or through the community Discord at [discord.gg/4kUe4yPQ32](https://discord.gg/4kUe4yPQ32)
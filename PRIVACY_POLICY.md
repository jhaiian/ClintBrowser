# Privacy Policy for Clint Browser

*Last updated: April 6 2026*

## Overview

Clint Browser is an open-source Android web browser built with privacy in mind. This policy explains what data is collected, what isn't, and how the app handles your information.

## Data We Do Not Collect

Clint Browser does not collect, store, transmit, or share any personal data. There are no analytics, no crash reporting services, no advertising SDKs, and no backend servers. The developer has no access to your browsing activity, search history, or any other information generated while using the app.

## Browsing Data

All browsing data — including history, cookies, cached content, and site storage — is stored locally on your device only. You can clear this data at any time through your device's app settings. Incognito tabs do not save cookies, cached content, or browsing history to your device.

## Bookmarks

Bookmarks are stored locally on your device only, in a private file within the app's data directory. They are never synced, uploaded, or shared. No third party has access to your bookmarks. You can remove individual bookmarks at any time from within the app, or delete all bookmark data by clearing the app's storage through your device settings.

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

If you have questions about this privacy policy, you can reach the developer at `jhaiianbetter@gmail.com` or through the community Discord at [discord.gg/4kUe4yPQ32](https://discord.gg/4kUe4yPQ32).

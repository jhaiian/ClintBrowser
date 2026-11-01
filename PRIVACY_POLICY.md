# Privacy Policy for Clint Browser

*Last updated: November 1, 2026*

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

All browsing data, including history, cookies, cached content, and site storage, is stored locally on your device only. You can clear this data at any time through your device's app settings. Incognito tabs do not save cookies, cached content, or browsing history to your device. Downloads you finish in Incognito tabs still show up in your Downloads list unless you turn that off, as explained in the Downloads section below. You can also have the app delete selected data automatically when you close it, as described in Privacy and Security Settings below.

**Incognito notification.** While Incognito tabs are open, Clint Browser shows a notification. Tapping it closes all of your Incognito tabs without opening the app. The notification is handled by your device and nothing is sent anywhere.

**Remember browsing mode.** This is off by default. When you turn it on, Clint Browser remembers whether you were last using Incognito or normal mode and opens in that mode next time. Only that choice is saved on your device. Your Incognito tabs are not saved.

**Copy and paste in the address bar.** When you long press the address bar, you can copy the page link or paste from your clipboard. Clint Browser only reads your clipboard when you tap Paste or Paste & Go.

**Tab groups.** Group names, colors, and which tabs belong to each group are stored locally on your device along with your open tabs. Incognito groups are kept in memory only and are gone when you close the app.

**Undo for closed tabs.** When you close tabs, Clint Browser briefly keeps them in memory so you can tap Undo and bring them back. Nothing is written to storage for this. Incognito tabs are never kept for undo.

**Delete browsing data.** The **Delete browsing data** screen (in History, or in Settings › Privacy & Security › History) lets you pick a time range and choose what to delete: browsing history, cookies and site data, cached images and files, open tabs, and site settings. The summaries it shows, such as how many history items or sites will be affected, are calculated on your device. Cookies, cached files, and site settings can only be deleted for all time. Your last choices are remembered locally so they're preselected next time. Deletion happens entirely on your device, and nothing is sent anywhere.

---

## Privacy and Security Settings

Clint Browser has a **Privacy and Security** page in Settings with the options below. These settings are stored locally on your device and are applied entirely by the app on your device. None of them send information to the developer or to any server we operate.

### Tracking Protection

- **Strip tracking parameters** (on by default): Removes known tracking parameters, such as `utm_` and `fbclid`, from links before they load. The link is cleaned locally before the page is requested.
- **Send Global Privacy Control** (on by default): Tells websites that you don't want your data sold or shared. This is only a request, and websites may choose to ignore it.
- **WebRTC leak protection** (off by default): Stops websites from discovering your real IP address through video call features. Some calls may not connect while this is on.

These options reduce some kinds of tracking but cannot stop all of it. Websites can still recognize you through other means, such as browser fingerprinting, accounts you sign in to, or your IP address. Because page loading is handled by your WebView provider, how it behaves internally is outside our control.

### Data and Device Protection

- **App lock** (off by default): Requires your fingerprint, face, or screen lock (PIN, pattern, or password) to open the app. When on, a **Lock after** option lets you choose Immediately, 1 minute (default), 5 minutes, or 15 minutes. Android performs the check locally and only tells the app whether it succeeded. Clint Browser never sees, receives, or stores your biometric data or device credentials.
- **Block screenshots** (off by default): Prevents screenshots and screen recording inside the app and hides the app preview in your recent apps list. This uses Android's secure window feature. It cannot stop someone from photographing your screen with another device, and behavior may vary on some devices.
- **Incognito keyboard** (on by default): Asks your keyboard not to learn what you type in Incognito Mode. This is a request made through Android, and a keyboard app may choose to ignore it.
- **Auto-clear clipboard** (off by default): After you copy something while using Clint Browser, clears the clipboard after 30 seconds, 1 minute, or 5 minutes, as long as the clipboard content hasn't changed in the meantime. This relies on the app still running, so if Android closes the app before the timer ends, the clipboard may not be cleared.
- **Clear data on exit** (off by default): Deletes the data you select when you close the app. A **Data to clear** option lets you choose any of **Browsing history**, **Cookies and site data**, **Cache**, and **Open tabs**. The first three are selected by default, and **Open tabs** is not. Bookmarks, downloads, and settings are not affected. If the app is closed in a way that doesn't let the cleanup finish, such as being force-stopped, the cleanup is completed the next time you open the app.

---

## Site Information

Tapping the lock icon in the address bar opens a sheet with information about the site you're on: whether the connection is secure, the site's certificate details (who it was issued to, who issued it, and how long it's valid), a note if you chose to continue past a certificate error, and the permissions you've saved for that site. The certificate details come from the connection your WebView already made to the site, so opening this sheet doesn't make any extra network request. Changing or resetting a permission here updates the same local Site Settings described in Website Permissions below.

---

## Background Playback

**Background playback** (on by default) keeps videos and music from websites playing when you switch apps or turn off the screen.

- While media is playing, Clint Browser shows a media notification with playback controls. Android may also show these controls on your lock screen.
- For normal tabs, the notification can show the page title, the site's address, and details the website provides about the media, such as its title, artist, album, and artwork. If the website provides an artwork image, Clint Browser downloads it directly from the address the website gives, without sending your cookies, and keeps it in memory only.
- For Incognito tabs, the notification shows a generic "Playing in Incognito" title instead of any page or media details.
- None of this information is stored or sent to the developer.

---

## Page Translation

Clint Browser can translate web pages using Firefox's translation engine (Bergamot), which runs **entirely on your device**.

- **Your page text never leaves your device.** Translation runs inside an isolated, offline engine within the app. The text of the pages you translate isn't sent to Mozilla, the developer, or any other service.
- **Language detection is local.** To decide whether to offer a translation, Clint Browser checks the page's language on your device.
- **Language files come from Mozilla.** To know which languages are available, Clint Browser downloads the list of translation files from Mozilla's Firefox settings service (`firefox.settings.services.mozilla.com`). This list is saved on your device and refreshed at most about once a day. It can be requested when Clint Browser detects a page in a language other than yours, when you open the translate bar, or when you open the Translations settings. When you translate between a pair of languages for the first time, or download a language in Settings, the needed language and engine files are downloaded from Mozilla's file server (`firefox-settings-attachments.cdn.mozilla.net`) and checked against their published checksums. These requests include only what's needed to fetch the files, and like any download, they reveal your IP address to Mozilla. No cookies, browsing history, page content, or identifiers from Clint Browser are sent.
- **Your translation preferences are stored locally.** The last language you translated to, your Offer / Always / Never choice for each language, and the sites you chose never to translate are stored on your device only. You can delete downloaded language files at any time from Settings › Browser › Tools › Translations.

Machine translations can be inaccurate. You can review Mozilla's privacy policy at https://www.mozilla.org/privacy/

---

## Custom Themes

The custom theme editor lets you change the colors used throughout the app, preview them live while you edit, and save, rename, edit, apply, and reuse your themes. Your themes, including their names and colors, are stored locally on your device. They are never uploaded or synced anywhere by Clint Browser.

---

## Homepage

You can customize the homepage in Settings.

- **Quick actions.** You can hide the quick actions on the homepage. This preference is stored locally on your device.
- **Custom homepage URL.** You can set your own web address as the homepage. The address is stored locally on your device. When the homepage opens, Clint Browser loads that address the same way it would load any website, so that site can see your IP address and may set cookies or collect data under its own privacy policy. Only use a homepage you trust.
- **Favorites.** The tiles and folders in the homepage's Favorites section, their order, and any custom images you pick for them are stored locally on your device. When you pick a custom image, Android's photo picker gives Clint Browser only the image you chose, and a copy is saved inside the app. Favorites are never uploaded or synced, and they are only included in a backup if you create one yourself. How tile icons are fetched is described in the Favicons section below.

---

## Search History

When you perform searches in the address or search bar, Clint Browser saves your search history locally to improve your experience. **All search history data is stored exclusively on your local device using SQLite**, a lightweight file-based database. This data never leaves your device. It is not uploaded to any server, shared with any third party, or transmitted anywhere else.

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

**Incognito search engine.** You can pick a different search engine for Incognito tabs. By default Incognito uses the same engine as normal browsing. If you pick a different one, your Incognito searches go to that engine instead.

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

You can also pick a different suggestions provider for Incognito tabs. By default Incognito uses the same provider as normal browsing. If you pick a different one, what you type in Incognito tabs goes to that provider instead.

Your saved search history is completely separate and never sent to DuckDuckGo, Google, or any other service.

**History and bookmarks in suggestions.** The address bar can also suggest matching sites and searches from your history and your bookmarks. These are found by searching the data stored on your device, and nothing is sent anywhere to find them. You can turn each one off in Settings › Browser › Search with **Show history in suggestions** and **Show bookmarks in suggestions**.

You can review DuckDuckGo's privacy policy at https://duckduckgo.com/privacy and Google's privacy policy at https://policies.google.com/privacy

---

## Favicons

Website icons (favicons) are loaded to help identify sites in tabs, history, and bookmarks.

- First, Clint Browser tries to load the favicon directly from the website  
- If unavailable, it falls back to DuckDuckGo’s favicon service  

When using the fallback service, only the domain name is sent to retrieve the icon. No personal data or browsing history is included.

**High-resolution icons.** Favicons in tabs, the tab strip, history, bookmarks, and suggestions, as well as homepage favorites and home screen shortcuts, use larger icons when available. Incognito tabs never look up high-resolution icons. To find one, Clint Browser first checks the website itself for its larger icons and web app manifest. If the site doesn't offer a large enough icon, Clint Browser asks Google's favicon service (`t3.gstatic.com`) and then DuckDuckGo's favicon service. Only the site's address without any page path (for example, `https://example.com`) is sent to Google, and only the domain name is sent to DuckDuckGo. Found icons are cached on your device.

If you turn off favicons in Data Saver, none of these requests are made.

You can review DuckDuckGo's privacy policy at https://duckduckgo.com/privacy and Google's privacy policy at https://policies.google.com/privacy

---

## Bookmarks

Bookmarks are stored locally on your device only using SQLite. They are never synced, uploaded, or shared. You can delete them at any time through the app or by clearing app storage.

---

## Backup & Restore

Clint Browser lets you back up your data, things like bookmarks, history, settings, open tabs and tab groups, and homepage favorites and their custom images, into a single file you keep for yourself. Here's how it actually works, in plain terms:

- **You choose where it goes.** When you create a backup, Android's own file picker opens and you pick the folder: your device storage, an SD card, wherever. Clint Browser doesn't send the file anywhere on its own, and there's no cloud sync tied to it.
- **Password protection is optional, and it's real encryption.** If you set a password, the backup is locked with AES‑256‑GCM, the same kind of encryption used by banks and messaging apps. Your password itself is never used directly. It's run through Argon2id first, which is specifically designed to make password-guessing slow and expensive, even for someone with serious hardware. If you leave the password blank, the backup is saved without encryption, which is convenient but means anyone with the file can read it, so treat it like any other unprotected file.
- **We can't help you recover a lost password.** Because the encryption key comes entirely from your password, and we never store or see that password, there's no "forgot password" option and no backdoor. If it's lost, the backup is gone. Please choose something you'll remember, or write it down somewhere safe.
- **Restoring works the same way in reverse.** You pick a backup file, enter the password if it has one, and everything gets read and restored locally on your device. Nothing is uploaded to check the password or verify the file. It either decrypts correctly or it doesn't.

In short: your backups never leave your control unless you move them somewhere yourself.

**Why we ask for your phone's password or biometric unlock first.** Before you can create or restore a backup, Clint Browser asks you to confirm it's really you, using your fingerprint, face unlock, or your device PIN/pattern/password, whichever you already use to unlock your phone. This isn't sent anywhere or stored by us; Android checks it locally and just tells the app "yes, this is the device owner." We added this step because a backup can contain sensitive stuff, like saved history and cookies that keep you logged into sites, and we didn't want someone who picks up your unlocked phone to be able to quietly export all of that or overwrite it with a restore in a few taps. It's an extra lock on top of the backup password, not a replacement for it.

---

## Downloads

Files you download are saved directly to your device. Clint Browser does not upload or transmit downloaded files. To give a file the right name and extension, Clint Browser may read the server's response headers and the first few bytes of the file. This check happens on your device and nothing is sent anywhere. Download details, such as progress and the segment information shown in a download's Properties dialog, are stored locally on your device and are never transmitted.

**Auto mode.** When the connection settings are set to **Auto**, Clint Browser adjusts how many connections a download uses based on its speed and on your device's state, such as whether you're on mobile data, in battery saver, or whether your phone is running hot. These checks are read from Android on your device. To start the next download from the same server at the right number, Clint Browser remembers the number of connections that worked best for each server's domain name. This is stored locally on your device, limited to a small number of recent servers, and never transmitted.

**Download history.** Finished downloads are kept in your Downloads list so you can find them later. You can turn this off for normal tabs and for Incognito tabs separately in the download settings. Both are on by default. When one is off, finished downloads from that mode aren't kept in the list. The files themselves stay on your device either way.

**Download page.** Each download remembers the page it was started from, so you can go back there later if you need a fresh link. This is saved on your device with the rest of your download history. When you update a download's link, Clint Browser checks the new link using the same browser details, Referer, and cookies the download already uses. The website sees that request the same way it saw the original download.

---

## MEGA Downloads

When you open a MEGA file or folder link, Clint Browser can offer to download it for you. This is on by default, and you can turn it off with Detect MEGA links in the download settings.

- **What MEGA sees.** To read the link, Clint Browser asks MEGA's servers (`g.api.mega.co.nz`) for the file or folder details using the ID in the link. The files are then downloaded from MEGA's storage servers. MEGA can see your IP address and which file or folder you're getting, the same as when you open the link on their website.
- **The key stays on your device.** The key at the end of a MEGA link is what unlocks the files. It's never sent to MEGA or anywhere else. File names and file contents are decrypted on your device.
- **No account and no uploads.** Clint Browser doesn't sign you in to MEGA and doesn't upload anything to it.

You can review MEGA's privacy policy at https://mega.io/privacy

---

## Media Capture

Media Capture detects media resources used by websites, such as video, audio, subtitles, and streaming formats like HLS/M3U8 and DASH, and lets you download them directly. For example, when a page plays a video, Media Capture inspects the page and its network requests to find available media streams and lists them as downloadable options. When Media Capture is turned on, it also reads the page's content, such as media players, links, and player settings, to find every available quality and subtitle, not just the one that is playing. Clint Browser may contact the website to check a found link's size and format, the same way the page itself would. Some sites serve video and audio as separate streams, and where supported, Media Capture can combine them into a single file.

**Combining video and audio.** Some sites deliver a video with no sound as one stream and the audio as a separate track. When you choose to combine them, Clint Browser downloads both streams and merges them into a single playable video file on your device. The merge is done locally using your device's media components, either through the hardware codec or the software codec depending on your codec setting, and nothing is sent to an external server.

**YouTube.** YouTube protects its videos in a way Media Capture can't save. If you open Media Capture on a YouTube page and the YTDLnis app is installed, Clint Browser sends the page link to YTDLnis so you can download the video there. Only the link is shared, and what YTDLnis does with it is covered by that app's own privacy policy. If YTDLnis isn't installed, Clint Browser explains why and can open the YTDLnis download page on GitHub.

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

## Shortcuts

Shortcuts let you add a website to your home screen. Here's how they handle your data:

- **Shortcut data is local.** A shortcut's name, URL, icon, frameless setting, and the profile it opens in are stored on your device. They are never uploaded or synced anywhere by Clint Browser.
- **Shortcut icons may be fetched in high resolution.** When you add a shortcut, Clint Browser looks for a sharper icon as described in the Favicons section above.
- **The home screen icon belongs to your launcher.** Clint Browser asks Android to place the icon, and your launcher handles it from there.
- **Opening a shortcut loads the website.** It connects to the site the same way visiting the page normally would.
- **Deleting a shortcut removes its data from the app.** Its icon may stay on your home screen until you remove it yourself.

---

## Website Permissions

Clint Browser allows websites to request access to certain device features. These permissions are fully controlled by you and can be configured in **Site Settings**.

When a website requests access, you can choose how the browser handles it:

- **Ask first.** The browser shows a prompt every time a website asks for access. This is the default.
- **Always deny.** Every request for that permission is blocked automatically without asking you.
- **Always allow.** Every request for that permission is granted automatically without asking you.

### Available Website Permissions

Websites may request access to:

- **Camera** for taking photos or recording video directly from websites
- **Microphone** for voice input, calls, or audio recording on websites
- **Location** for location based services such as maps or nearby results
- **Notifications** so websites can send you push notifications if you allow them
- **Clipboard** when a website wants to read text from your clipboard, for example to let you paste into a form with one tap

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
- **POST_NOTIFICATIONS** – Shows download progress, completion, and failure alerts, the media controls for background playback, and the notification that closes your Incognito tabs, in your notification bar.
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
- **FOREGROUND_SERVICE_MEDIA_PROCESSING** – Works with `FOREGROUND_SERVICE` to tell Android the background activity is processing a downloaded video stream, such as combining its segments into a single file or merging a video with a separate audio track.
- **FOREGROUND_SERVICE_MEDIA_PLAYBACK** – Works with `FOREGROUND_SERVICE` to keep website audio and video playing in the background when Background playback is turned on.
- **RUN_USER_INITIATED_JOBS** – Lets downloads you started keep running in the background when Android doesn't allow the app to start a foreground service.

## Power & Background
- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS** – Prevents Android from pausing downloads when the device is in battery saver or Doze mode.
- **WAKE_LOCK** – Prevents your device from sleeping while a download is in progress so files don't get stuck halfway.
- **RECEIVE_BOOT_COMPLETED** – Checks for unfinished downloads after your device restarts and resumes them automatically.

No permission is used for tracking or data collection.

---

## Data Retention and Deletion

Since no personal data is collected by the developer, there is nothing to delete on our servers. All local data (history, bookmarks, downloads, etc.) is stored on your device. You can delete any or all of it at any time through the app’s settings, with Delete browsing data, by turning on Clear data on exit, or by clearing the app’s storage in your device settings.

---

## Children's Privacy

Clint Browser does not knowingly collect any personal information from anyone, including children under the age of 13. The app has no accounts, no sign‑ins, and no data transmission to the developer. If you are a parent or guardian and believe your child has used the app in a way that concerns you, you may contact us, but note that no data has been collected by us.

---

## Third-Party Services

Clint Browser does not include analytics, advertising, or tracking SDKs.

However, the search engine or services you choose (such as DuckDuckGo, Brave Search, Ecosia, Google, or a custom one you configure) may collect data according to their own privacy policies. Clint Browser has no control over those services. Additionally, the search suggestions feature uses DuckDuckGo's or Google's public suggestion API, or a custom one you configure, depending on which provider you've selected, and the favicon fallback feature uses DuckDuckGo's public API, while high-resolution icons may also use Google's favicon service, as described above. Any user scripts you install can also communicate with third party services on their own, as described in the User Scripts section above. If you set a custom homepage, that website is also a third party and is subject to its own privacy policy. Page Translation downloads its language and engine files from Mozilla, as described in the Page Translation section above, and those requests are subject to Mozilla's privacy policy. MEGA downloads contact MEGA's servers, as described in the MEGA Downloads section above. On YouTube pages, Media Capture can pass the page link to the YTDLnis app, which has its own privacy policy.

---

## Changes to This Policy

Any updates to this privacy policy will be reflected in this document on the GitHub repository. The app always links to the latest version. Your continued use of the app after changes means you accept the updated policy.

---

## Open Source

Clint Browser is fully open source. You can review the source code to verify how the app works at:
https://github.com/jhaiian/ClintBrowser

---

## Contact

If you have questions about this privacy policy, you can reach the developer at `jhaiian@proton.me` or through the community Discord at https://discord.gg/4kUe4yPQ32

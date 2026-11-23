# Terms of Service for Clint Browser

*Last updated: November 24, 2026*

## 1. What These Terms Cover

These Terms of Service ("Terms") describe the legal relationship between you and the developer of Clint Browser ("we", "us", or "the developer"). By using Clint Browser, you agree to these Terms.

If you do not agree, simply uninstall the app.

## 2. What Clint Browser Is

Clint Browser is a **free, open‑source Android browser** built around privacy. Key facts:

- No backend servers, everything runs on your device.
- Block ads and trackers with Quiver Guard.
- Block entire categories of sites at the domain level with Website Blocker.
- Download files and folders from MEGA links.
- Set your own search engine and search suggestions provider, including fully custom ones you point at any URL.
- Run your own user scripts to customize how pages behave.
- Translate pages on your device with Page Translation.
- Play videos in Clint Browser's own video player.
- Keep separate profiles, each with its own data.
- Protect your settings with a parental password.
- Send downloads to the built-in download manager or to another download manager app you choose.
- Incognito sessions are completely isolated.
- You can view, modify, or share the source code under the GPL‑v3 license.

Your browsing data belongs to you. The developer never sees it.

## 3. Open Source License (GPL‑v3)

The source code of Clint Browser is licensed under the **GNU General Public License version 3**. You are free to:

- Use the app for any purpose.
- Study how it works.
- Modify the code.
- Share copies with anyone.
- Distribute your modified versions (as long as you also share your changes under GPL‑v3).

A copy of the license is available at [gnu.org/licenses/gpl-3.0.html](https://www.gnu.org/licenses/gpl-3.0.html) and inside the app's repository.

## 4. Trademarks and Branding (Not Open Source)

The name **"Clint Browser"**, the word "Clint", the logo, icons, screenshots, and all related branding assets are **trademarks and proprietary assets** of the developer. They are **not** covered by the GPL.

You may **not** use, copy, modify, or redistribute these assets without explicit written permission. For example, you cannot create a modified version of Clint Browser and still call it "Clint Browser" or use the original logo. Contact `jhaiian@proton.me` for permission.

## 5. Privacy: No Data Collection

Clint Browser does **not** collect any personal information, browsing history, usage statistics, crash reports, or telemetry of any kind. The app has no analytics, no tracking pixels, no backend servers.

However, websites you visit may set cookies or collect data according to their own privacy policies. Those websites are not under our control.

## 6. Backups Are Your Responsibility

Clint Browser lets you create backup files of your browsing data, optionally protected with a password you choose. Because we never see or store that password, we have no way to recover it if you forget it. If that happens, the backup cannot be unlocked by us or by anyone else. Please keep your password somewhere safe.

Backup files are saved wherever you choose to save them, using your device's own file picker. Once a backup file exists, keeping it safe (backing it up further, not losing the storage device it's on, not sharing it carelessly) is up to you. We're not responsible for backup files that are lost, deleted, corrupted, or accessed by someone else after they leave the app.

If you've set a parental password and Backup restore is protected, you'll also need that password to restore a backup. The parental password itself isn't saved in your backups.

Before you can back up or restore, the app will also ask you to unlock it with your fingerprint, face unlock, or your device's own PIN/pattern/password. This check happens entirely on your device through Android's own security system; we never see or receive it. It exists so that someone who picks up your unlocked phone can't export or overwrite your data without your say-so. If your device has no lock screen or biometrics set up at all, Backup & Restore won't be available until you set one up in your device settings, since there's simply nothing for Android to check against.

## 7. Custom Search Engines, Search Suggestions, and User Scripts

Clint Browser lets you add your own custom search engines and custom search suggestions providers, each pointed at any URL you enter. It also lets you install and run user scripts, which are pieces of JavaScript that run on the pages you visit to change how they look or behave.

These features send your data (search queries, suggestion text, or whatever a script requests) to whatever destination you configure, whether that's a built in provider or one you typed in yourself. The developer does not run, host, review, or vet any of these destinations or scripts. You are solely responsible for what you point them at and what you install.

User scripts run with access to the pages you visit and, depending on what a script does, can read or modify page content and make network requests of their own. Only install scripts from sources you trust. The developer is not responsible for anything a user script does, including data loss, privacy exposure, or harm caused by a malicious or buggy script.

## 8. Video Player

Clint Browser's video player plays videos from websites with its own controls. It relies on what each website and your WebView allow, and doesn't work with every video, for example videos protected with DRM. The developer doesn't guarantee that it will work on every website or keep working when a website changes. You are responsible for the content you play and for making sure your use follows the terms of the websites you visit and any applicable laws.

## 9. Media Capture

Clint Browser includes Media Capture, a feature that detects media resources used by websites, such as video, audio, subtitles, and streaming formats like HLS/M3U8 and DASH, and lets you download them directly. Where a website provides video and audio as separate streams, Media Capture can, where supported, combine them.

Media Capture works by inspecting webpage content and network requests, and by interacting with Android, WebView, and other components outside the developer's control. Websites may use their own tracking, DRM, authentication, or other technologies that affect how Media Capture behaves. Media Capture is provided **"as is"**, and the developer does not guarantee that it will detect every type of media, prevent websites from tracking you, or provide complete privacy or security while using the feature.

You are responsible for making sure your use of Media Capture, including any content you download with it, complies with the terms of the websites you visit and any applicable copyright or other laws.

Media Capture can't save YouTube videos. On YouTube pages it can send the page link to YTDLnis, a separate app made by someone else. The developer of Clint Browser doesn't make, control, or support YTDLnis, and your use of it is covered by its own terms.

## 10. MEGA Downloads

Clint Browser can download files and folders from MEGA links. MEGA is a separate service, and Clint Browser isn't affiliated with it. Using MEGA links also means following MEGA's own terms. The developer doesn't control MEGA's servers and doesn't guarantee that MEGA downloads will always work, for example if MEGA changes how its service works or limits how much you can download. You are responsible for making sure you have the right to download what you get from MEGA.

## 11. Third-Party Download Managers

Clint Browser can hand downloads to another download manager app you choose, including any app you pick with Custom app. When you do, Clint Browser passes that app the link along with the website's cookies, your browser's User-Agent, and the Referer, so downloads that need your login keep working. Cookies can give whoever has them full access to your account on that website.

These apps are made by third parties. The developer doesn't make, review, or control them, and isn't responsible for what they do with that information or for downloads that fail in them. Only choose a download manager you trust.

## 12. Parental Controls

Clint Browser lets you set a parental password that has to be entered before Website Blocker, history, or backup restore can be changed in ways that weaken your protection. Without a password, turning off Website Blocker asks for a short wait and a typed code instead.

These features make it harder to undo your protection on a whim, but they can't make it impossible. Someone with access to the device can still, for example, clear the app's data, uninstall the app, or use another browser. They are not a replacement for your device's own parental control or security tools.

The parental password can't be recovered. If you forget it, the developer can't reset it for you, and the only way to remove it is to clear Clint Browser's data, which also deletes everything else in the app.

## 13. Network Recovery and Website Theme

When Recover after network changes is on, Clint Browser restarts its browser engine after a VPN or network switch. Your tabs are restored, but anything a page hadn't saved yet, such as text you typed into a form, may be lost. You can turn this off in Settings.

Letting websites use their own appearance instead of the app theme can make some pages look different than you expect, for example bright pages while the app is in dark mode. The developer doesn't control how each website looks.

## 14. Page Translation

Clint Browser can translate web pages using Firefox's translation engine, which runs on your device. The language files it needs are downloaded from Mozilla, a third party the developer doesn't control.

Translations are produced automatically by machine translation and **can be inaccurate, incomplete, or misleading**. They may change the meaning of the original text. Don't rely on a translation for anything important, such as legal, medical, financial, or safety information, without checking the original page. The developer is not responsible for errors in translations or for any decisions you make based on them.

## 15. No Warranty

Clint Browser is provided **"as is"** and **"as available"**. To the maximum extent permitted by law, the developer disclaims all warranties, express or implied, including:

- Merchantability
- Fitness for a particular purpose
- Non‑infringement
- Uninterrupted or error‑free operation

You use the app entirely at your own risk.

## 16. Limitation of Liability

In no event shall the developer be liable for any damages arising from your use of Clint Browser, including but not limited to:

- Data loss or corruption
- Device damage
- Security breaches caused by websites you visit
- Losing access to settings or data because a parental password was forgotten
- Issues with third‑party services or apps (such as search engines, search suggestions providers, user script sources, translation file downloads, MEGA, YTDLnis, or download manager apps)
- Inaccurate machine translations
- Any indirect, incidental, or consequential damages

Some jurisdictions do not allow certain liability exclusions. In those cases, this limitation applies to the fullest extent permitted by law.

## 17. Third‑Party Content

Clint Browser is a **browser**, it renders content from websites, search engines, and online services that are completely outside the developer's control. We do not:

- Endorse or guarantee the accuracy, legality, or safety of any website you visit
- Take responsibility for what you find on the internet

You are responsible for your own browsing choices.

## 18. Changes to These Terms

These Terms may be updated occasionally. Changes will be posted on the GitHub repository. The app will always link to the latest version. If you continue using Clint Browser after an update, you accept the new Terms.

## 19. Governing Law

These Terms are governed by the laws of the Philippines. If you live elsewhere, your local consumer rights may also apply.

## 20. Contact

For questions or permission requests:

- Email: `jhaiian@proton.me`
- Discord: [discord.gg/4kUe4yPQ32](https://discord.gg/4kUe4yPQ32)

package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.MainActivity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import com.jhaiian.clint.settings.sitepermissions.SitePermissionActivity

internal const val WEB_NOTIFICATION_CHANNEL_ID = "clint_web_notifications"

internal fun MainActivity.createWebNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            WEB_NOTIFICATION_CHANNEL_ID,
            getString(R.string.web_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.web_notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

internal fun MainActivity.showWebNotificationPermissionFromBridge(
    webView: WebView,
    callbackId: String,
    rawOrigin: String,
    isIncognito: Boolean = false
) {
    val safeId = callbackId.replace("'", "")

    if (!isIncognito) {
        val stored = SitePermissionManager.getState(this, rawOrigin, SitePermissionDatabase.TYPE_NOTIFICATION)
        if (stored == SitePermissionDatabase.STATE_ALLOW) {
            webView.evaluateJavascript("window._ClintResolvePermission('$safeId','granted')", null)
            return
        }
        if (stored == SitePermissionDatabase.STATE_DENY) {
            webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
            return
        }

        val globalDefault = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("site_perm_default_${SitePermissionDatabase.TYPE_NOTIFICATION}", SitePermissionActivity.PREF_VALUE_ASK) ?: SitePermissionActivity.PREF_VALUE_ASK
        when (globalDefault) {
            SitePermissionActivity.PREF_VALUE_ALLOW -> {
                webView.evaluateJavascript("window._ClintResolvePermission('$safeId','granted')", null)
                return
            }
            SitePermissionActivity.PREF_VALUE_DENY -> {
                webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                return
            }
            else -> {}
        }
    }

    val displayOrigin = rawOrigin.ifEmpty { getString(R.string.notification_web_request_origin_unknown) }
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_web_permission, null)
    view.findViewById<TextView>(R.id.tvWebPermissionMessage).text =
        getString(R.string.notification_web_request_message, displayOrigin)
    val checkRemember = view.findViewById<CheckBox>(R.id.checkWebPermissionRemember)
    if (isIncognito) checkRemember.visibility = View.GONE
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.notification_web_request_title))
        .setView(view)
        .setNegativeButton(getString(R.string.action_deny)) { _, _ ->
            if (checkRemember.isChecked && !isIncognito) SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_NOTIFICATION, SitePermissionDatabase.STATE_DENY)
            webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
        }
        .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
            if (checkRemember.isChecked && !isIncognito) SitePermissionManager.setState(this, rawOrigin, SitePermissionDatabase.TYPE_NOTIFICATION, SitePermissionDatabase.STATE_ALLOW)
            webView.evaluateJavascript("window._ClintResolvePermission('$safeId','granted')", null)
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

internal fun MainActivity.postWebNotification(title: String, body: String, tag: String, origin: String) {
    val displayTitle = if (origin.isNotEmpty()) "$origin: $title" else title
    val builder = NotificationCompat.Builder(this, WEB_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_24)
        .setContentTitle(displayTitle)
        .setAutoCancel(true)
    if (body.isNotEmpty()) builder.setContentText(body)
    val notifId = if (tag.isNotEmpty()) (origin + tag).hashCode() else System.currentTimeMillis().toInt()
    getSystemService(NotificationManager::class.java).notify(notifId, builder.build())
}

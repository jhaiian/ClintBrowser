package com.jhaiian.clint.browser.delegates

import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.ui.ClintToast

internal fun MainActivity.showRefreshLinkDownloadDialog(
    url: String,
    filename: String,
    userAgent: String,
    referer: String,
    cookies: String,
    session: MainActivity.RefreshLinkSession
) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_refresh_link_detected, null)
    val tvMessage = dialogView.findViewById<TextView>(R.id.tv_refresh_link_message)
    val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radio_group_refresh)
    val radioUpdate = dialogView.findViewById<RadioButton>(R.id.radio_update_existing)

    tvMessage.text = getString(R.string.refresh_link_dialog_message, session.filename)

    val dialog = MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.refresh_link_dialog_title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.action_close), null)
        .setPositiveButton(getString(R.string.action_ok), null)
        .create()
        .also { applyStatusBarFlagToDialog(it) }

    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val updateExisting = radioGroup.checkedRadioButtonId == radioUpdate.id
            if (updateExisting) {
                ClintDownloadManager.updateDownloadUrl(session.downloadId, url)
                ClintToast.show(
                    this,
                    getString(R.string.refresh_link_updated_toast, session.filename),
                    R.drawable.ic_link_24
                )
                refreshLinkSession = null
                dialog.dismiss()
            } else {
                dialog.dismiss()
                showDownloadDialog(url, filename, userAgent, referer, cookies)
            }
        }
    }

    dialog.show()
}

package com.jhaiian.clint.profiles

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.ConcurrentHashMap

enum class ProfileDeleteResult { DELETED, IN_USE }

@SuppressLint("RequiresFeature")
object WebProfiles {

    const val DEFAULT_ID = "default"
    const val PROFILE_HEADER = "X-Clint-Profile"

    private val tabProfiles = ConcurrentHashMap<String, String>()
    private val cookieManagers = ConcurrentHashMap<String, CookieManager>()

    fun isSupported(): Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

    fun resolve(context: Context, requestedId: String?): String {
        if (requestedId.isNullOrEmpty() || requestedId == DEFAULT_ID) return DEFAULT_ID
        if (!isSupported()) return DEFAULT_ID
        return if (ProfileRepository.get(context, requestedId) != null) requestedId else DEFAULT_ID
    }

    fun bind(webView: WebView, profileId: String) {
        if (profileId == DEFAULT_ID || !isSupported()) return
        runCatching {
            val profile = ProfileStore.getInstance().getOrCreateProfile(profileId)
            cookieManagers[profileId] = profile.cookieManager
            WebViewCompat.setProfile(webView, profileId)
        }
    }

    fun profileOf(webView: WebView): String {
        if (!isSupported()) return DEFAULT_ID
        return runCatching {
            val name = WebViewCompat.getProfile(webView).name
            if (name == Profile.DEFAULT_PROFILE_NAME) DEFAULT_ID else name
        }.getOrDefault(DEFAULT_ID)
    }

    fun registerTab(tabId: String, profileId: String) {
        if (profileId == DEFAULT_ID) tabProfiles.remove(tabId) else tabProfiles[tabId] = profileId
    }

    fun unregisterTab(tabId: String) {
        tabProfiles.remove(tabId)
    }

    fun profileOfTab(tabId: String): String = tabProfiles[tabId] ?: DEFAULT_ID

    fun cookieManager(profileId: String?): CookieManager? =
        if (profileId.isNullOrEmpty() || profileId == DEFAULT_ID) CookieManager.getInstance() else cookieManagers[profileId]

    fun cookieManagerFor(webView: WebView): CookieManager =
        cookieManager(profileOf(webView)) ?: CookieManager.getInstance()

    fun cookieFor(profileId: String?, url: String): String =
        runCatching { cookieManager(profileId)?.getCookie(url) }.getOrNull().orEmpty()

    fun cookieForHeaders(headers: Map<String, String>?, url: String): String =
        cookieFor(headers?.get(PROFILE_HEADER), url)

    fun withMarker(headers: Map<String, String>?, profileId: String): Map<String, String> {
        val base = headers.orEmpty()
        return if (profileId == DEFAULT_ID) base else base + (PROFILE_HEADER to profileId)
    }

    fun flushAll() {
        cookieManagers.values.forEach { runCatching { it.flush() } }
    }

    fun clearData(profileId: String) {
        if (!isSupported() || profileId == DEFAULT_ID) return
        runCatching {
            val profile = ProfileStore.getInstance().getOrCreateProfile(profileId)
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
            profile.webStorage.deleteAllData()
        }
    }

    fun deleteProfileData(profileId: String): ProfileDeleteResult {
        if (!isSupported()) return ProfileDeleteResult.DELETED
        return try {
            ProfileStore.getInstance().deleteProfile(profileId)
            cookieManagers.remove(profileId)
            ProfileDeleteResult.DELETED
        } catch (_: IllegalStateException) {
            ProfileDeleteResult.IN_USE
        }
    }
}

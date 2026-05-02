package com.jhaiian.clint.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.jhaiian.clint.R
import com.jhaiian.clint.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateVersionInfo()
        setupLinks()
    }

    private fun populateVersionInfo() {
        val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val versionName = pInfo.versionName
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        binding.tvVersionInfo.text = getString(R.string.about_version_info, versionName, versionCode, arch)

        val webViewPackage = WebView.getCurrentWebViewPackage()
        if (webViewPackage != null) {
            val appName = webViewPackage.applicationInfo
                ?.let { requireContext().packageManager.getApplicationLabel(it).toString() }
                ?: webViewPackage.packageName
            val packageName = webViewPackage.packageName
            val version = webViewPackage.versionName ?: getString(R.string.about_webview_unavailable)
            binding.tvWebViewInfo.text = getString(R.string.about_webview_info, appName, packageName, version)
        } else {
            binding.tvWebViewInfo.text = getString(R.string.about_webview_unavailable)
        }
    }

    private fun setupLinks() {
        makeClickable(binding.tvAuthorLink, "https://linktr.ee/jhaiian")
        makeClickable(binding.tvGithubLink, "https://github.com/jhaiian/ClintBrowser")
        binding.tvPrivacyPolicyLink.setOnClickListener {
            com.jhaiian.clint.ui.DocumentViewer.show(requireContext(), getString(R.string.document_viewer_privacy_policy_title), com.jhaiian.clint.ui.DocumentViewer.PRIVACY_POLICY_URL)
        }
        binding.tvTermsLink.setOnClickListener {
            com.jhaiian.clint.ui.DocumentViewer.show(requireContext(), getString(R.string.document_viewer_terms_title), com.jhaiian.clint.ui.DocumentViewer.TERMS_URL)
        }
        makeClickable(binding.tvDiscordLink, "https://discord.gg/4kUe4yPQ32")
        makeClickable(binding.tvRedditLink, "https://www.reddit.com/r/ClintBrowser")
        makeClickable(binding.tvPatreonLink, "https://www.patreon.com/Jhaiian")
        makeClickable(binding.tvKofiLink, "https://ko-fi.com/jhaiian")
        makeClickable(binding.tvPaypalLink, "https://www.paypal.me/jhaiian")
        makeClickable(binding.tvLicenseLink, "https://www.gnu.org/licenses/gpl-3.0.html")
        makeClickable(binding.tvContactEmail, "mailto:jhaiianbetter@duck.com")
        makeClickable(binding.tvContributorsLink, "https://github.com/jhaiian/ClintBrowser/blob/main/Contributors.md")
        makeClickable(binding.tvMarkwonLink, "https://github.com/noties/Markwon")
        makeClickable(binding.tvMarkwonLicenseLink, "https://www.apache.org/licenses/LICENSE-2.0.txt")
        makeClickable(binding.tvAndroidXLink, "https://developer.android.com/jetpack/androidx")
        makeClickable(binding.tvAndroidXLicenseLink, "https://www.apache.org/licenses/LICENSE-2.0.txt")
        makeClickable(binding.tvMaterialLink, "https://github.com/material-components/material-components-android")
        makeClickable(binding.tvMaterialLicenseLink, "https://www.apache.org/licenses/LICENSE-2.0.txt")
        makeClickable(binding.tvOkHttpLink, "https://github.com/square/okhttp")
        makeClickable(binding.tvOkHttpLicenseLink, "https://www.apache.org/licenses/LICENSE-2.0.txt")
        makeClickable(binding.tvSimpleMagicLink, "https://github.com/j256/simplemagic")
        makeClickable(binding.tvSimpleMagicLicenseLink, "https://opensource.org/licenses/ISC")
    }

    private fun makeClickable(view: TextView, url: String) {
        view.setOnClickListener {
            val intent = if (url.startsWith("mailto:")) {
                Intent(Intent.ACTION_SENDTO, Uri.parse(url)).apply {
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            runCatching { startActivity(intent) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

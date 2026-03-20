package com.jhaiian.clint

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
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
        val versionCode = pInfo.longVersionCode
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        binding.tvVersionInfo.text = getString(R.string.about_version_info, versionName, versionCode, arch)
    }

    private fun setupLinks() {
        makeClickable(binding.tvAuthorLink, "https://github.com/jhaiian")
        makeClickable(binding.tvGithubLink, "https://github.com/jhaiian/Clint-Browser")
        makeClickable(binding.tvDiscordLink, "https://discord.gg/4kUe4yPQ32")
        makeClickable(binding.tvKofiLink, "https://ko-fi.com/jhaiian")
        makeClickable(binding.tvPaypalLink, "mailto:jhaiianbetter@gmail.com")
        makeClickable(binding.tvLicenseLink, "https://www.gnu.org/licenses/gpl-3.0.html")
    }

    private fun makeClickable(view: TextView, url: String) {
        view.setOnClickListener {
            val intent = if (url.startsWith("mailto:")) {
                Intent(Intent.ACTION_SENDTO, Uri.parse(url)).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "Clint Browser Support")
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

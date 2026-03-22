package com.jhaiian.clint

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private var selectedEngine = "duckduckgo"
    private var selectedDohMode = DohManager.MODE_OFF
    private var selectedProvider = DohManager.PROVIDER_CLOUDFLARE
    private var currentPage = 0

    companion object {
        const val PRIVACY_POLICY_URL = "https://github.com/jhaiian/Clint-Browser/blob/main/PRIVACY_POLICY.md"
        const val TERMS_URL = "https://github.com/jhaiian/Clint-Browser/blob/main/TERMS_OF_SERVICE.md"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("setup_complete", false)) { startMainActivity(); return }
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupPage0()
        setupPage1()
        setupPage2()
        showPage(0)
    }

    private fun setupPage0() {
        val checkbox = binding.root.findViewById<CheckBox>(R.id.checkboxConsent)
        val btnContinue = binding.root.findViewById<MaterialButton>(R.id.btnAgreeAndContinue)
        val tvPrivacy = binding.root.findViewById<TextView>(R.id.tvConsentPrivacyLink)
        val tvTerms = binding.root.findViewById<TextView>(R.id.tvConsentTermsLink)

        checkbox.setOnCheckedChangeListener { _, isChecked ->
            btnContinue.isEnabled = isChecked
            btnContinue.alpha = if (isChecked) 1.0f else 0.5f
        }
        btnContinue.alpha = 0.5f
        btnContinue.setOnClickListener { showPage(1) }

        tvPrivacy.setOnClickListener { openUrl(PRIVACY_POLICY_URL) }
        tvTerms.setOnClickListener { openUrl(TERMS_URL) }
    }

    private fun setupPage1() {
        selectEngine("duckduckgo")
        binding.cardDuckduckgo.setOnClickListener { selectEngine("duckduckgo") }
        binding.cardBrave.setOnClickListener { selectEngine("brave") }
        binding.cardGoogle.setOnClickListener { selectEngine("google") }
        binding.btnNext.setOnClickListener { onNextFromPage1() }
    }

    private fun setupPage2() {
        selectDohMode(DohManager.MODE_OFF)
        selectProvider(DohManager.PROVIDER_CLOUDFLARE)
        listOf(
            binding.cardDohOff to DohManager.MODE_OFF,
            binding.cardDohDefault to DohManager.MODE_DEFAULT,
            binding.cardDohIncreased to DohManager.MODE_INCREASED,
            binding.cardDohMax to DohManager.MODE_MAX
        ).forEach { (card, mode) ->
            card.setOnClickListener { selectDohMode(mode) }
        }
        listOf(
            binding.cardSetupCloudflare to DohManager.PROVIDER_CLOUDFLARE,
            binding.cardSetupQuad9 to DohManager.PROVIDER_QUAD9
        ).forEach { (card, provider) ->
            card.setOnClickListener { selectProvider(provider) }
        }
        binding.btnGetStarted.setOnClickListener { saveAndProceed() }
    }

    private fun showPage(page: Int) {
        currentPage = page
        binding.viewFlipper.displayedChild = page
    }

    private fun onNextFromPage1() {
        if (selectedEngine == "google") {
            MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_ClintBrowser_Dialog)
                .setTitle(getString(R.string.google_warning_title))
                .setMessage(getString(R.string.google_warning_message))
                .setNegativeButton(getString(R.string.choose_another), null)
                .setPositiveButton(getString(R.string.use_google_anyway)) { _, _ -> showPage(2) }
                .show()
        } else {
            showPage(2)
        }
    }

    private fun selectEngine(engine: String) {
        selectedEngine = engine
        listOf(
            Triple(binding.cardDuckduckgo, binding.radioDuckduckgo, "duckduckgo"),
            Triple(binding.cardBrave, binding.radioBrave, "brave"),
            Triple(binding.cardGoogle, binding.radioGoogle, "google")
        ).forEach { (card, radio, key) ->
            val sel = key == engine
            card.alpha = if (sel) 1.0f else 0.45f
            card.strokeWidth = if (sel) 3 else 0
            radio.isChecked = sel
        }
    }

    private fun selectDohMode(mode: String) {
        selectedDohMode = mode
        listOf(
            Triple(binding.cardDohOff, binding.radioDohSetupOff, DohManager.MODE_OFF),
            Triple(binding.cardDohDefault, binding.radioDohSetupDefault, DohManager.MODE_DEFAULT),
            Triple(binding.cardDohIncreased, binding.radioDohSetupIncreased, DohManager.MODE_INCREASED),
            Triple(binding.cardDohMax, binding.radioDohSetupMax, DohManager.MODE_MAX)
        ).forEach { (card, radio, key) ->
            val sel = key == mode
            card.alpha = if (sel) 1.0f else 0.45f
            card.strokeWidth = if (sel) 3 else 0
            radio.isChecked = sel
        }
        binding.setupProviderSection.visibility =
            if (mode == DohManager.MODE_OFF) View.GONE else View.VISIBLE
    }

    private fun selectProvider(provider: String) {
        selectedProvider = provider
        listOf(
            Triple(binding.cardSetupCloudflare, binding.radioSetupCloudflare, DohManager.PROVIDER_CLOUDFLARE),
            Triple(binding.cardSetupQuad9, binding.radioSetupQuad9, DohManager.PROVIDER_QUAD9)
        ).forEach { (card, radio, key) ->
            val sel = key == provider
            card.alpha = if (sel) 1.0f else 0.45f
            card.strokeWidth = if (sel) 3 else 0
            radio.isChecked = sel
        }
    }

    private fun saveAndProceed() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit()
            .putString("search_engine", selectedEngine)
            .putString("doh_mode", selectedDohMode)
            .putString("doh_provider", selectedProvider)
            .putBoolean("setup_complete", true)
            .apply()
        startMainActivity()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun onBackPressed() {
        if (currentPage > 0) showPage(currentPage - 1)
        else super.onBackPressed()
    }
}

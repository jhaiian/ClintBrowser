package com.jhaiian.clint

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.jhaiian.clint.databinding.ActivitySetupBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private var selectedEngine = "duckduckgo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("setup_complete", false)) {
            startMainActivity()
            return
        }
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEngineCards()
        binding.btnGetStarted.setOnClickListener { onGetStarted() }
    }

    private fun setupEngineCards() {
        selectCard("duckduckgo")
        binding.cardDuckduckgo.setOnClickListener { selectCard("duckduckgo") }
        binding.cardBrave.setOnClickListener { selectCard("brave") }
        binding.cardGoogle.setOnClickListener { selectCard("google") }
    }

    private fun selectCard(engine: String) {
        selectedEngine = engine
        val selectedAlpha = 1.0f
        val unselectedAlpha = 0.45f
        val selectedStroke = 3
        val unselectedStroke = 0
        listOf(
            Triple(binding.cardDuckduckgo, binding.radioDuckduckgo, "duckduckgo"),
            Triple(binding.cardBrave, binding.radioBrave, "brave"),
            Triple(binding.cardGoogle, binding.radioGoogle, "google")
        ).forEach { (card, radio, key) ->
            val selected = key == engine
            card.alpha = if (selected) selectedAlpha else unselectedAlpha
            card.strokeWidth = if (selected) selectedStroke else unselectedStroke
            radio.isChecked = selected
        }
    }

    private fun onGetStarted() {
        if (selectedEngine == "google") {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.google_warning_title))
                .setMessage(getString(R.string.google_warning_message))
                .setNegativeButton(getString(R.string.choose_another), null)
                .setPositiveButton(getString(R.string.use_google_anyway)) { _, _ ->
                    saveAndProceed()
                }
                .show()
        } else {
            saveAndProceed()
        }
    }

    private fun saveAndProceed() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit()
            .putString("search_engine", selectedEngine)
            .putBoolean("setup_complete", true)
            .apply()
        startMainActivity()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

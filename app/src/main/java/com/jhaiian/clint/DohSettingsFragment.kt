package com.jhaiian.clint

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.jhaiian.clint.databinding.FragmentDohSettingsBinding

class DohSettingsFragment : Fragment() {

    private var _binding: FragmentDohSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDohSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val currentMode = prefs.getString("doh_mode", DohManager.MODE_OFF) ?: DohManager.MODE_OFF
        val currentProvider = prefs.getString("doh_provider", DohManager.PROVIDER_CLOUDFLARE) ?: DohManager.PROVIDER_CLOUDFLARE

        applyModeSelection(currentMode)
        applyProviderSelection(currentProvider)
        updateProviderVisibility(currentMode)

        listOf(
            binding.cardDohOff to DohManager.MODE_OFF,
            binding.cardDohDefault to DohManager.MODE_DEFAULT,
            binding.cardDohIncreased to DohManager.MODE_INCREASED,
            binding.cardDohMax to DohManager.MODE_MAX
        ).forEach { (card, mode) ->
            card.setOnClickListener {
                applyModeSelection(mode)
                updateProviderVisibility(mode)
                prefs.edit().putString("doh_mode", mode).apply()
                DohManager.invalidate()
            }
        }

        listOf(
            binding.cardProviderCloudflare to DohManager.PROVIDER_CLOUDFLARE,
            binding.cardProviderQuad9 to DohManager.PROVIDER_QUAD9
        ).forEach { (card, provider) ->
            card.setOnClickListener {
                applyProviderSelection(provider)
                prefs.edit().putString("doh_provider", provider).apply()
                DohManager.invalidate()
            }
        }
    }

    private fun applyModeSelection(mode: String) {
        listOf(
            Triple(binding.cardDohOff, binding.radioDohOff, DohManager.MODE_OFF),
            Triple(binding.cardDohDefault, binding.radioDohDefault, DohManager.MODE_DEFAULT),
            Triple(binding.cardDohIncreased, binding.radioDohIncreased, DohManager.MODE_INCREASED),
            Triple(binding.cardDohMax, binding.radioDohMax, DohManager.MODE_MAX)
        ).forEach { (card, radio, key) ->
            val sel = key == mode
            card.alpha = if (sel) 1.0f else 0.55f
            card.strokeWidth = if (sel) 3 else 0
            radio.isChecked = sel
        }
    }

    private fun applyProviderSelection(provider: String) {
        listOf(
            Triple(binding.cardProviderCloudflare, binding.radioProviderCloudflare, DohManager.PROVIDER_CLOUDFLARE),
            Triple(binding.cardProviderQuad9, binding.radioProviderQuad9, DohManager.PROVIDER_QUAD9)
        ).forEach { (card, radio, key) ->
            val sel = key == provider
            card.alpha = if (sel) 1.0f else 0.55f
            card.strokeWidth = if (sel) 3 else 0
            radio.isChecked = sel
        }
    }

    private fun updateProviderVisibility(mode: String) {
        binding.providerSection.visibility = if (mode == DohManager.MODE_OFF) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

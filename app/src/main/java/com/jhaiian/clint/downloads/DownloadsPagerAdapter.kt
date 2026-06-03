package com.jhaiian.clint.downloads

import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class DownloadsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val tabTypes: Array<DownloadsTabType> = DownloadsTabType.values()

    override fun getItemCount(): Int = tabTypes.size

    override fun createFragment(position: Int): DownloadsTabFragment =
        DownloadsTabFragment.newInstance(tabTypes[position])
}

package org.fossify.gallery.adapters

import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.models.Medium

class MyPagerAdapter(val activity: ViewPagerActivity, fm: FragmentManager, val media: MutableList<Medium>) : FragmentStatePagerAdapter(fm) {
    // Keyed by medium path so the map stays correct when items move to new positions.
    private val fragments = LinkedHashMap<String, ViewPagerFragment>()
    // Snapshot of paths before the last updateMedia call, used by getItemPosition to
    // determine whether a fragment is at the same position (and can be preserved) or
    // has moved (and must be destroyed/recreated — FragmentStatePagerAdapter tags
    // fragments by position, so moving is not possible without recreation).
    private var oldMediaPaths: List<String> = emptyList()
    // Path-to-index map of the current media list, rebuilt on each updateMedia call
    // so getItemPosition can do O(1) lookups instead of O(n) indexOfFirst per fragment.
    private var pathToIndexMap: Map<String, Int> = emptyMap()
    var shouldInitFragment = true

    override fun getCount() = media.size

    override fun getItem(position: Int): Fragment {
        val medium = media[position]
        val bundle = Bundle()
        bundle.putSerializable(MEDIUM, medium)
        bundle.putBoolean(SHOULD_INIT_FRAGMENT, shouldInitFragment)
        val fragment = if (medium.isVideo()) {
            VideoFragment()
        } else {
            PhotoFragment()
        }

        fragment.arguments = bundle
        return fragment
    }

    override fun getItemPosition(item: Any): Int {
        val medium = (item as? Fragment)?.arguments?.getSerializable(MEDIUM) as? Medium
            ?: return PagerAdapter.POSITION_NONE
        val newIndex = pathToIndexMap[medium.path] ?: return PagerAdapter.POSITION_NONE
        // FragmentStatePagerAdapter tags fragments by position, so a fragment can only
        // be kept when it stays at the same index. If the item moved, return POSITION_NONE
        // so the pager destroys the old fragment and creates a fresh one at the new index.
        val oldIndex = oldMediaPaths.indexOf(medium.path)
        return if (oldIndex == newIndex) PagerAdapter.POSITION_UNCHANGED else PagerAdapter.POSITION_NONE
    }

    /**
     * Replaces the media list in-place and notifies the ViewPager. Existing fragments
     * that remain at the same position survive (preserving user state such as zoom),
     * and are given the fresh [Medium] metadata via [updateMedium]. Fragments whose
     * path is no longer in the list, or that moved to a different position, are
     * destroyed by the pager and recreated with the new Medium.
     *
     * This avoids recreating the entire adapter — and all fragments — when a background
     * re-scan returns essentially the same set of files with refreshed metadata.
     */
    fun updateMedia(newMedia: MutableList<Medium>) {
        oldMediaPaths = media.map { it.path }
        media.clear()
        media.addAll(newMedia)
        pathToIndexMap = media.withIndex().associate { (i, m) -> m.path to i }
        notifyDataSetChanged()
        // Push fresh Medium metadata to surviving fragments so they use up-to-date
        // signatures, sizes, etc. without losing their state.
        for ((path, fragment) in fragments.toList()) {
            val freshMedium = newMedia.find { it.path == path } ?: continue
            fragment.updateMedium(freshMedium)
        }
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as ViewPagerFragment

        // getItem() might not be called if the activity is recreated, so the listener must be set here
        fragment.listener = activity

        fragments[media[position].path] = fragment
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
        val medium = (any as? Fragment)?.arguments?.getSerializable(MEDIUM) as? Medium
        if (medium != null) {
            fragments.remove(medium.path)
        }
        super.destroyItem(container, position, any)
    }

    fun getCurrentFragment(position: Int): ViewPagerFragment? {
        if (position !in media.indices) return null
        return fragments[media[position].path]
    }

    fun toggleFullscreen(isFullscreen: Boolean) {
        for ((_, fragment) in fragments) {
            fragment.fullscreenToggled(isFullscreen)
        }
    }

    // try fixing TransactionTooLargeException crash on Android Nougat, tip from https://stackoverflow.com/a/43193425/1967672
    override fun saveState(): Parcelable? {
        val bundle = super.saveState() as Bundle?
        bundle?.putParcelableArray("states", null)
        return bundle
    }
}

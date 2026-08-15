package org.fossify.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.logMediaDebug
import org.fossify.gallery.extensions.logPerf
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.util.Locale

class GetMediaAsynctask(
    val context: Context, val mPath: String, val isPickImage: Boolean = false, val isPickVideo: Boolean = false,
    val showAll: Boolean, val callback: (media: ArrayList<ThumbnailItem>) -> Unit
) :
    AsyncTask<Void, Void, ArrayList<ThumbnailItem>>() {
    private val mediaFetcher = MediaFetcher(context)

    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        try {
            return doFetchMedia()
        } catch (e: Throwable) {
            // Catch Throwable (not Exception) so OutOfMemoryError is caught. The MediaStore
            // cursor for 143k+ items can exhaust the heap during moveToNext(). Return whatever
            // we have (empty list if the OOM happened early) — the caller's gotMedia will keep
            // the existing cached data if the new result is empty, and the next load will retry.
            context.logPerf("GetMediaAsynctask: ${e.javaClass.simpleName}: ${e.message}")
            context.logMediaDebug("GetMediaAsynctask OOM/ERROR: ${e.javaClass.simpleName}: ${e.message}, returning empty list")
            return ArrayList()
        }
    }

    private fun doFetchMedia(): ArrayList<ThumbnailItem> {
        val pathToUse = if (showAll) SHOW_ALL else mPath
        val folderGrouping = context.config.getFolderGrouping(pathToUse)
        val folderSorting = context.config.getFolderSorting(pathToUse)
        val getProperDateTaken = folderSorting and SORT_BY_DATE_TAKEN != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

        val getProperLastModified = folderSorting and SORT_BY_DATE_MODIFIED != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

        val getProperFileSize = folderSorting and SORT_BY_SIZE != 0
        val favoritePaths = context.getFavoritePaths()
        val getVideoDurations = context.config.showThumbnailVideoDuration
        val lastModifieds = if (getProperLastModified) mediaFetcher.getLastModifieds() else HashMap()
        val dateTakens = if (getProperDateTaken) mediaFetcher.getDateTakens() else HashMap()

        val media = if (showAll) {
            val allMedia = if (isRPlus()) {
                // Single batched MediaStore query — reuses the same getMediaStoreFolderInfo
                // primitive as the folder view. knownFolders=emptySet forces the single-query
                // path that collects all media in one cursor scan.
                //
                // Used regardless of isExternalStorageManager(): MediaStore is always available
                // on R+ and reads from the OS's pre-built SQLite index, which is dramatically
                // faster than a per-folder filesystem walk (File.listFiles + per-file stat()).
                // The filesystem loop took 40-60s for 100k files across 789 folders on an SD
                // card with ESM; the single MediaStore query takes seconds.
                val info = mediaFetcher.getMediaStoreFolderInfo(
                    knownFolders = emptySet(),
                    collectMedia = true,
                    isPickImage = isPickImage,
                    isPickVideo = isPickVideo,
                    favoritePaths = favoritePaths
                )
                // mediaByFolder is already visibility-filtered by shouldFolderBeVisible inside
                // getMediaStoreFolderInfo. Filter out special/protected folders.
                val recycleBinLower = RECYCLE_BIN.lowercase(Locale.getDefault())
                val favoritesLower = FAVORITES.lowercase(Locale.getDefault())
                val result = ArrayList<Medium>()
                info.mediaByFolder?.forEach { (folderLower, items) ->
                    if (folderLower == recycleBinLower || folderLower == favoritesLower) return@forEach
                    if (context.config.isFolderProtected(folderLower)) return@forEach
                    result.addAll(items)
                }
                result
            } else {
                // Pre-Android-11: filesystem loop with full folder list
                val foldersToScan = mediaFetcher.getFoldersToScan().filter { it != RECYCLE_BIN && it != FAVORITES && !context.config.isFolderProtected(it) }
                val result = ArrayList<Medium>()
                foldersToScan.forEach {
                    if (mediaFetcher.shouldStop) return@forEach
                    result.addAll(
                        mediaFetcher.getFilesFrom(
                            it, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize,
                            favoritePaths, getVideoDurations, lastModifieds, dateTakens.clone() as HashMap<String, Long>, null
                        )
                    )
                }
                result
            }
            mediaFetcher.sortMedia(allMedia, context.config.getFolderSorting(SHOW_ALL))
            allMedia
        } else {
            mediaFetcher.getFilesFrom(
                mPath, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize, favoritePaths,
                getVideoDurations, lastModifieds, dateTakens, null
            )
        }

        return mediaFetcher.groupMedia(media, pathToUse)
    }

    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        callback(media)
    }

    fun stopFetching() {
        mediaFetcher.shouldStop = true
        cancel(true)
    }
}

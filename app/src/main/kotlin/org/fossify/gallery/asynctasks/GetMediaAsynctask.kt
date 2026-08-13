package org.fossify.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
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
            val allMedia = if (isRPlus() && !isExternalStorageManager()) {
                // Single batched MediaStore query — reuses the same getMediaStoreFolderInfo
                // primitive as the folder view. knownFolders=emptySet forces the single-query
                // path that collects all media in one cursor scan.
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
                // Pre-Android-11 / ESM: filesystem loop with full folder list
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

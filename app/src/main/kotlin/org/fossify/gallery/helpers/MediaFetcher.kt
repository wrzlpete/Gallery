package org.fossify.gallery.helpers

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.text.format.DateFormat
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import java.io.File
import java.util.Calendar
import java.util.Locale

class MediaFetcher(val context: Context) {
    var shouldStop = false

    companion object {
        const val MEDIA_STORE_CHANGED_SENTINEL = "__media_store_changed__"
    }

    data class MediaStoreFolderInfo(
        val allParentPaths: HashSet<String>,
        val newFolders: ArrayList<String>,
        val mediaByFolder: HashMap<String, ArrayList<Medium>>?
    )

    // on Android 11 we fetch all files at once from MediaStore and have it split by folder, use it if available
    fun getFilesFrom(
        curPath: String, isPickImage: Boolean, isPickVideo: Boolean, getProperDateTaken: Boolean, getProperLastModified: Boolean,
        getProperFileSize: Boolean, favoritePaths: ArrayList<String>, getVideoDurations: Boolean,
        lastModifieds: HashMap<String, Long>, dateTakens: HashMap<String, Long>, android11Files: HashMap<String, ArrayList<Medium>>?
    ): ArrayList<Medium> {
        val filterMedia = context.config.filterMedia
        if (filterMedia == 0) {
            return ArrayList()
        }

        val curMedia = ArrayList<Medium>()
        if (context.isPathOnOTG(curPath)) {
            if (context.hasOTGConnected()) {
                val newMedia = getMediaOnOTG(curPath, isPickImage, isPickVideo, filterMedia, favoritePaths, getVideoDurations)
                curMedia.addAll(newMedia)
            }
        } else {
            if (curPath != FAVORITES && curPath != RECYCLE_BIN && isRPlus() && !isExternalStorageManager()) {
                if (android11Files?.containsKey(curPath.lowercase(Locale.getDefault())) == true) {
                    curMedia.addAll(android11Files[curPath.lowercase(Locale.getDefault())]!!)
                } else if (android11Files == null) {
                    val files = getAndroid11FolderMedia(isPickImage, isPickVideo, favoritePaths, false, getProperDateTaken, dateTakens)
                    if (files.containsKey(curPath.lowercase(Locale.getDefault()))) {
                        curMedia.addAll(files[curPath.lowercase(Locale.getDefault())]!!)
                    }
                }
            }

            if (curMedia.isEmpty()) {
                val newMedia = getMediaInFolder(
                    curPath, isPickImage, isPickVideo, filterMedia, getProperDateTaken, getProperLastModified, getProperFileSize,
                    favoritePaths, getVideoDurations, lastModifieds.clone() as HashMap<String, Long>, dateTakens.clone() as HashMap<String, Long>
                )

                if (curPath == FAVORITES && isRPlus() && !isExternalStorageManager()) {
                    val files =
                        getAndroid11FolderMedia(isPickImage, isPickVideo, favoritePaths, true, getProperDateTaken, dateTakens.clone() as HashMap<String, Long>)
                    newMedia.forEach { newMedium ->
                        for ((folder, media) in files) {
                            media.forEach { medium ->
                                if (medium.path == newMedium.path) {
                                    newMedium.size = medium.size
                                }
                            }
                        }
                    }
                }
                curMedia.addAll(newMedia)
            }
        }

        sortMedia(curMedia, context.config.getFolderSorting(curPath))
        return curMedia
    }

    fun getFoldersToScan(forceFullScan: Boolean = false): ArrayList<String> {
        return try {
            val OTGPath = context.config.OTGPath
            val folders = getLatestFileFolders()
            folders.addAll(arrayListOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString(),
                "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/Camera",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
            ).filter { context.getDoesFilePathExist(it, OTGPath) })

            val filterMedia = context.config.filterMedia
            if (filterMedia != 0) {
                val uri = Files.getContentUri("external")
                val projection = arrayOf(Images.Media.DATA)
                val baseSelection = getSelectionQuery(filterMedia)
                val baseArgs = getSelectionArgsQuery(filterMedia)

                val lastScanTs = context.config.lastFolderScanTimestamp

                val mediaStoreChanged = if (isRPlus()) {
                    val currentVersion = try {
                        MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL)
                    } catch (e: Exception) {
                        null
                    }
                    val savedVersion = context.config.mediaStoreVersion
                    if (currentVersion != null && currentVersion != savedVersion) {
                        context.config.mediaStoreVersion = currentVersion
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }

                val (selection, selectionArgs) = if (forceFullScan || mediaStoreChanged) {
                    baseSelection to baseArgs.toTypedArray()
                } else {
                    "($baseSelection) AND ${MediaStore.MediaColumns.DATE_ADDED} > ?" to
                        (baseArgs + lastScanTs.toString()).toTypedArray()
                }

                val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                folders.addAll(parseCursor(cursor!!))
            }

            val config = context.config
            val shouldShowHidden = config.shouldShowHidden
            val excludedPaths = if (config.temporarilyShowExcluded) {
                HashSet()
            } else {
                config.excludedFolders
            }

            val includedPaths = config.includedFolders

            val folderNoMediaStatuses = HashMap<String, Boolean>()
            val distinctPathsMap = HashMap<String, String>()
            val distinctPaths = folders.distinctBy {
                when {
                    distinctPathsMap.containsKey(it) -> distinctPathsMap[it]
                    else -> {
                        val distinct = it.getDistinctPath()
                        distinctPathsMap[it.getParentPath()] = distinct.getParentPath()
                        distinct
                    }
                }
            }

            val noMediaFolders = context.getNoMediaFoldersSync()
            noMediaFolders.forEach { folder ->
                folderNoMediaStatuses["$folder/$NOMEDIA"] = true
            }

            distinctPaths.filter {
                it.shouldFolderBeVisible(excludedPaths, includedPaths, shouldShowHidden, folderNoMediaStatuses) { path, hasNoMedia ->
                    folderNoMediaStatuses[path] = hasNoMedia
                }
            }.toMutableList() as ArrayList<String>
        } catch (e: Exception) {
            ArrayList()
        }
    }

    fun getFoldersWithRecentMedia(): HashSet<String> {
        val result = HashSet<String>()
        try {
            val filterMedia = context.config.filterMedia
            if (filterMedia == 0) return result

            val lastScanTs = context.config.lastFolderScanTimestamp

            val uri = Files.getContentUri("external")
            val projection = arrayOf(Images.Media.DATA)
            val baseSelection = getSelectionQuery(filterMedia)
            val baseArgs = getSelectionArgsQuery(filterMedia)

            val selection = "($baseSelection) AND ${MediaStore.MediaColumns.DATE_ADDED} > ?"
            val selectionArgs = (baseArgs + lastScanTs.toString()).toTypedArray()

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                val path = cursor.getStringValue(Images.Media.DATA) ?: return@queryCursor
                result.add(path.getParentPath())
            }

            if (result.isEmpty() && isRPlus()) {
                val currentVersion = try {
                    MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL)
                } catch (e: Exception) {
                    null
                }
                val savedVersion = context.config.mediaStoreVersion
                if (currentVersion != null && currentVersion != savedVersion) {
                    context.config.mediaStoreVersion = currentVersion
                    result.add(MEDIA_STORE_CHANGED_SENTINEL)
                }
            }
        } catch (ignored: Exception) {
        }
        return result
    }

    fun getNewFoldersViaFilesystem(knownFolders: Set<String>): ArrayList<String> {
        val result = ArrayList<String>()
        val overallStart = SystemClock.elapsedRealtime()
        try {
            val everShownFolders = context.config.everShownFolders
            val knownLower = knownFolders.map { it.lowercase(Locale.getDefault()) }.toHashSet()
            val excludedPaths = if (context.config.temporarilyShowExcluded) HashSet() else context.config.excludedFolders
            val includedPaths = context.config.includedFolders
            val shouldShowHidden = context.config.shouldShowHidden
            val filterMedia = context.config.filterMedia

            val parentDirs = everShownFolders
                .mapNotNull { it.getParentPath() }
                .filter { it.isNotEmpty() && it != "/" }
                .distinct()
                .filter { parentDir ->
                    val parentParent = parentDir.getParentPath()
                    parentParent.isEmpty() || parentParent == "/" ||
                        parentParent == context.internalStoragePath ||
                        parentParent == context.sdCardPath ||
                        parentParent == context.otgPath
                }
                .toMutableSet()

            // Always include storage roots as parent dirs so that new top-level folders (e.g. a
            // folder copied to the SD card root via USB) are discovered even if no everShownFolder
            // exists under them yet. listFiles() on a storage root is cheap (few top-level entries).
            if (context.internalStoragePath.isNotEmpty()) {
                parentDirs.add(context.internalStoragePath)
            }
            if (context.sdCardPath.isNotEmpty()) {
                parentDirs.add(context.sdCardPath)
            }

            context.logPerf("getNewFoldersViaFilesystem: ${parentDirs.size} parent dirs from ${everShownFolders.size} everShownFolders")

            var listFilesCount = 0
            var folderHasMediaCount = 0
            var slowListFiles = ArrayList<Pair<String, Long>>()

            for (parentDir in parentDirs) {
                if (shouldStop) break
                val parentFile = File(parentDir)
                val listStart = SystemClock.elapsedRealtime()
                val children = parentFile.listFiles() ?: continue
                val listTime = SystemClock.elapsedRealtime() - listStart
                listFilesCount++
                if (listTime > 500) {
                    slowListFiles.add(parentDir to listTime)
                }
                for (child in children) {
                    if (shouldStop) break
                    if (!child.isDirectory) continue
                    val childPath = child.absolutePath
                    if (childPath.lowercase(Locale.getDefault()) in knownLower) continue
                    if (!shouldShowHidden && child.name.startsWith('.')) continue
                    if (excludedPaths.any { childPath.startsWith(it) }) continue
                    if (result.any { it.lowercase(Locale.getDefault()) == childPath.lowercase(Locale.getDefault()) }) continue
                    val fhmStart = SystemClock.elapsedRealtime()
                    if (!folderHasMedia(child, filterMedia, shouldShowHidden)) continue
                    folderHasMediaCount++
                    val fhmTime = SystemClock.elapsedRealtime() - fhmStart
                    if (fhmTime > 500) {
                        slowListFiles.add("$childPath (folderHasMedia)" to fhmTime)
                    }
                    result.add(childPath)
                }
            }

            for (includedPath in includedPaths) {
                if (shouldStop) break
                val incFile = File(includedPath)
                if (incFile.isDirectory) {
                    val incLower = includedPath.lowercase(Locale.getDefault())
                    if (incLower !in knownLower && !result.any { it.lowercase(Locale.getDefault()) == incLower }) {
                        if (folderHasMedia(incFile, filterMedia, shouldShowHidden)) {
                            result.add(includedPath)
                        }
                    }
                    val listStart = SystemClock.elapsedRealtime()
                    val children = incFile.listFiles() ?: continue
                    val listTime = SystemClock.elapsedRealtime() - listStart
                    listFilesCount++
                    if (listTime > 500) {
                        slowListFiles.add("$includedPath (included)" to listTime)
                    }
                    for (child in children) {
                        if (shouldStop) break
                        if (!child.isDirectory) continue
                        val childPath = child.absolutePath
                        if (childPath.lowercase(Locale.getDefault()) in knownLower) continue
                        if (!shouldShowHidden && child.name.startsWith('.')) continue
                        if (excludedPaths.any { childPath.startsWith(it) }) continue
                        if (result.any { it.lowercase(Locale.getDefault()) == childPath.lowercase(Locale.getDefault()) }) continue
                        val fhmStart = SystemClock.elapsedRealtime()
                        if (!folderHasMedia(child, filterMedia, shouldShowHidden)) continue
                        folderHasMediaCount++
                        val fhmTime = SystemClock.elapsedRealtime() - fhmStart
                        if (fhmTime > 500) {
                            slowListFiles.add("$childPath (included folderHasMedia)" to fhmTime)
                        }
                        result.add(childPath)
                    }
                }
            }

            context.logPerf("getNewFoldersViaFilesystem: $listFilesCount listFiles() calls, $folderHasMediaCount folderHasMedia() calls, ${slowListFiles.size} slow (>500ms)")
            slowListFiles.sortedByDescending { it.second }.take(10).forEach {
                context.logPerf("getNewFoldersViaFilesystem: SLOW ${it.first} took ${it.second} ms")
            }
        } catch (ignored: Exception) {
        }
        context.logPerf("getNewFoldersViaFilesystem: total took ${SystemClock.elapsedRealtime() - overallStart} ms, found ${result.size} new folders")
        return result
    }

    private fun folderHasMedia(folder: File, filterMedia: Int, showHidden: Boolean): Boolean {
        val files = folder.listFiles() ?: return false
        return files.any { file ->
            if (!showHidden && file.name.startsWith('.')) return@any false
            val path = file.absolutePath
            val isImage = path.isImageFast()
            val isVideo = if (isImage) false else path.isVideoFast()
            val isGif = if (isImage || isVideo) false else path.isGif()
            val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
            val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()
            (isImage && filterMedia and TYPE_IMAGES != 0) ||
                (isVideo && filterMedia and TYPE_VIDEOS != 0) ||
                (isGif && filterMedia and TYPE_GIFS != 0) ||
                (isRaw && filterMedia and TYPE_RAWS != 0) ||
                (isSvg && filterMedia and TYPE_SVGS != 0)
        }
    }

    fun getMediaStoreFolderInfo(
        knownFolders: Set<String>,
        collectMedia: Boolean = false,
        isPickImage: Boolean = false,
        isPickVideo: Boolean = false,
        favoritePaths: ArrayList<String> = ArrayList()
    ): MediaStoreFolderInfo {
        val allParentPaths = HashSet<String>()
        val newFolders = ArrayList<String>()
        val mediaByFolder = if (collectMedia) HashMap<String, ArrayList<Medium>>() else null
        val start = SystemClock.elapsedRealtime()
        val showHidden = context.config.shouldShowHidden
        try {
            val filterMedia = context.config.filterMedia
            if (filterMedia == 0) {
                context.logPerf("getMediaStoreFolderInfo: filterMedia is 0, returning empty")
                return MediaStoreFolderInfo(allParentPaths, newFolders, mediaByFolder)
            }

            val knownLower = knownFolders.map { it.lowercase(Locale.getDefault()) }.toHashSet()
            val uri = Files.getContentUri("external")
            val baseSelection = getSelectionQuery(filterMedia)
            val baseArgs = getSelectionArgsQuery(filterMedia)

            // Split-query optimization: when knownFolders is non-empty, use a fast DATA-only query
            // to discover all parent paths and new folders, then a targeted 7-column query for just
            // the new folders' media. When knownFolders is empty (first launch/DB reset), use the
            // original single 7-column query since all folders are "new".
            val useSplitQuery = collectMedia && knownFolders.isNotEmpty()

            if (useSplitQuery) {
                // Query 1: DATA-only to find parent paths and new folders (fast)
                val dataProjection = arrayOf(Images.Media.DATA)
                context.logPerf("getMediaStoreFolderInfo: split query 1 (DATA-only), selection length=${baseSelection.length}, args=${baseArgs.size}")
                val cursor1 = context.contentResolver.query(uri, dataProjection, "($baseSelection)", baseArgs.toTypedArray(), null)
                cursor1?.use {
                    while (it.moveToNext()) {
                        if (shouldStop) break
                        val path = it.getStringValue(Images.Media.DATA) ?: continue
                        val parent = path.getParentPath()
                        val parentLower = parent.lowercase(Locale.getDefault())
                        allParentPaths.add(parentLower)
                        if (parentLower !in knownLower && parent !in newFolders) {
                            newFolders.add(parent)
                        }
                    }
                }
                if (shouldStop) {
                    context.logPerf("getMediaStoreFolderInfo: query 1 interrupted by shouldStop, returning empty to avoid partial results")
                    return MediaStoreFolderInfo(HashSet(), ArrayList(), mediaByFolder)
                }
                context.logPerf("getMediaStoreFolderInfo: query 1 took ${SystemClock.elapsedRealtime() - start} ms, found ${allParentPaths.size} total, ${newFolders.size} new")

                // Query 2: targeted 7-column query for just the new folders
                if (newFolders.isNotEmpty() && mediaByFolder != null) {
                    val mediaProjection = arrayOf(
                        Images.Media._ID,
                        Images.Media.DISPLAY_NAME,
                        Images.Media.DATA,
                        Images.Media.DATE_MODIFIED,
                        Images.Media.DATE_TAKEN,
                        Images.Media.SIZE,
                        MediaStore.MediaColumns.DURATION
                    )
                    val newFolderLowers = newFolders.map { it.lowercase(Locale.getDefault()) }
                    val likeSelection = newFolderLowers.joinToString(" OR ") { "${Images.Media.DATA} LIKE ?" }
                    val likeArgs = newFolderLowers.map { "$it/%" }.toTypedArray()
                    val fullSelection = "($baseSelection) AND ($likeSelection)"
                    val fullArgs = baseArgs + likeArgs.toList()

                    val query2Start = SystemClock.elapsedRealtime()
                    context.logPerf("getMediaStoreFolderInfo: split query 2 (targeted), folders=${newFolders.size}")
                    val cursor2 = context.contentResolver.query(uri, mediaProjection, fullSelection, fullArgs.toTypedArray(), null)
                    cursor2?.use {
                        while (it.moveToNext()) {
                            if (shouldStop) break
                            val path = it.getStringValue(Images.Media.DATA) ?: continue
                            val parent = path.getParentPath()
                            val parentLower = parent.lowercase(Locale.getDefault())
                            if (parentLower !in newFolderLowers) continue

                            val filename = it.getStringValue(Images.Media.DISPLAY_NAME) ?: continue
                            if (!showHidden && filename.startsWith('.')) continue

                            val isImage = path.isImageFast()
                            val isVideo = if (isImage) false else path.isVideoFast()
                            val isGif = if (isImage || isVideo) false else path.isGif()
                            val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                            val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

                            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) continue
                            if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0)) continue
                            if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0)) continue
                            if (isGif && filterMedia and TYPE_GIFS == 0) continue
                            if (isRaw && filterMedia and TYPE_RAWS == 0) continue
                            if (isSvg && filterMedia and TYPE_SVGS == 0) continue

                            val size = it.getLongValue(Images.Media.SIZE)
                            if (size <= 0L) continue

                            val type = when {
                                isVideo -> TYPE_VIDEOS
                                isGif -> TYPE_GIFS
                                isRaw -> TYPE_RAWS
                                isSvg -> TYPE_SVGS
                                else -> TYPE_IMAGES
                            }

                            val mediaStoreId = it.getLongValue(Images.Media._ID)
                            val lastModified = it.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                            var dateTaken = it.getLongValue(Images.Media.DATE_TAKEN)
                            if (dateTaken == 0L) dateTaken = lastModified
                            val videoDuration = Math.round(it.getIntValue(MediaStore.MediaColumns.DURATION) / 1000.toDouble()).toInt()
                            val isFavorite = favoritePaths.contains(path)
                            val medium = Medium(null, filename, path, parent, lastModified, dateTaken, size, type, videoDuration, isFavorite, 0L, mediaStoreId)

                            val existing = mediaByFolder[parentLower]
                            if (existing == null) {
                                mediaByFolder[parentLower] = ArrayList<Medium>()
                            }
                            mediaByFolder[parentLower]!!.add(medium)
                        }
                    }
                    context.logPerf("getMediaStoreFolderInfo: query 2 took ${SystemClock.elapsedRealtime() - query2Start} ms, media folders=${mediaByFolder.size}")
                }
            } else {
                // Single query: either no collectMedia, or first launch (knownFolders empty)
                val projection = if (collectMedia) {
                    arrayOf(
                        Images.Media._ID,
                        Images.Media.DISPLAY_NAME,
                        Images.Media.DATA,
                        Images.Media.DATE_MODIFIED,
                        Images.Media.DATE_TAKEN,
                        Images.Media.SIZE,
                        MediaStore.MediaColumns.DURATION
                    )
                } else {
                    arrayOf(Images.Media.DATA)
                }

                context.logPerf("getMediaStoreFolderInfo: single query, selection length=${baseSelection.length}, args=${baseArgs.size}, collectMedia=$collectMedia")
                val cursor = context.contentResolver.query(uri, projection, "($baseSelection)", baseArgs.toTypedArray(), null)
                cursor?.use {
                    while (it.moveToNext()) {
                        if (shouldStop) break
                        val path = it.getStringValue(Images.Media.DATA) ?: continue
                        val parent = path.getParentPath()
                        val parentLower = parent.lowercase(Locale.getDefault())
                        allParentPaths.add(parentLower)
                        if (parentLower !in knownLower && parent !in newFolders) {
                            newFolders.add(parent)
                        }

                        if (collectMedia && parentLower !in knownLower) {
                            val filename = it.getStringValue(Images.Media.DISPLAY_NAME) ?: continue
                            if (!showHidden && filename.startsWith('.')) continue

                            val isImage = path.isImageFast()
                            val isVideo = if (isImage) false else path.isVideoFast()
                            val isGif = if (isImage || isVideo) false else path.isGif()
                            val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                            val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

                            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) continue
                            if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0)) continue
                            if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0)) continue
                            if (isGif && filterMedia and TYPE_GIFS == 0) continue
                            if (isRaw && filterMedia and TYPE_RAWS == 0) continue
                            if (isSvg && filterMedia and TYPE_SVGS == 0) continue

                            val size = it.getLongValue(Images.Media.SIZE)
                            if (size <= 0L) continue

                            val type = when {
                                isVideo -> TYPE_VIDEOS
                                isGif -> TYPE_GIFS
                                isRaw -> TYPE_RAWS
                                isSvg -> TYPE_SVGS
                                else -> TYPE_IMAGES
                            }

                            val mediaStoreId = it.getLongValue(Images.Media._ID)
                            val lastModified = it.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                            var dateTaken = it.getLongValue(Images.Media.DATE_TAKEN)
                            if (dateTaken == 0L) dateTaken = lastModified
                            val videoDuration = Math.round(it.getIntValue(MediaStore.MediaColumns.DURATION) / 1000.toDouble()).toInt()
                            val isFavorite = favoritePaths.contains(path)
                            val medium = Medium(null, filename, path, parent, lastModified, dateTaken, size, type, videoDuration, isFavorite, 0L, mediaStoreId)

                            val existing = mediaByFolder!![parentLower]
                            if (existing == null) {
                                mediaByFolder[parentLower] = ArrayList<Medium>()
                            }
                            mediaByFolder[parentLower]!!.add(medium)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            context.logPerf("getMediaStoreFolderInfo: EXCEPTION ${e.message}")
        }
        if (shouldStop) {
            context.logPerf("getMediaStoreFolderInfo: interrupted by shouldStop, returning empty to avoid partial results")
            return MediaStoreFolderInfo(HashSet(), ArrayList(), mediaByFolder)
        }
        context.logPerf("getMediaStoreFolderInfo: took ${SystemClock.elapsedRealtime() - start} ms, found ${allParentPaths.size} total, ${newFolders.size} new, ${mediaByFolder?.size ?: 0} media folders")
        if (newFolders.isNotEmpty()) {
            // Filter new folders through shouldFolderBeVisible to exclude hidden folders
            // (.nomedia, dot folders) when showHidden is false — same as getFoldersToScan does
            val excludedPaths = if (context.config.temporarilyShowExcluded) HashSet() else context.config.excludedFolders
            val includedPaths = context.config.includedFolders
            val folderNoMediaStatuses = HashMap<String, Boolean>()
            val noMediaFolders = context.getNoMediaFoldersSync()
            noMediaFolders.forEach { folder ->
                folderNoMediaStatuses["$folder/$NOMEDIA"] = true
            }
            val beforeCount = newFolders.size
            newFolders.retainAll { folder ->
                folder.shouldFolderBeVisible(excludedPaths, includedPaths, showHidden, folderNoMediaStatuses, skipFileCheck = true) { path, hasNoMedia ->
                    folderNoMediaStatuses[path] = hasNoMedia
                }
            }
            if (beforeCount != newFolders.size) {
                context.logPerf("getMediaStoreFolderInfo: shouldFolderBeVisible filtered $beforeCount -> ${newFolders.size} new folders")
                // Remove media entries for filtered-out folders
                if (mediaByFolder != null) {
                    val newFolderLowers = newFolders.map { it.lowercase(Locale.getDefault()) }.toHashSet()
                    mediaByFolder.keys.retainAll(newFolderLowers)
                }
            }
            context.logPerf("getMediaStoreFolderInfo: ${newFolders.size} new folders")
        }
        return MediaStoreFolderInfo(allParentPaths, newFolders, mediaByFolder)
    }

    fun getLatestFileFolders(): LinkedHashSet<String> {
        val uri = Files.getContentUri("external")
        val projection = arrayOf(Images.ImageColumns.DATA)
        val parents = LinkedHashSet<String>()
        var cursor: Cursor? = null
        try {
            if (isRPlus()) {
                val bundle = Bundle().apply {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 10)
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(BaseColumns._ID))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                }

                cursor = context.contentResolver.query(uri, projection, bundle, null)
                if (cursor?.moveToFirst() == true) {
                    do {
                        val path = cursor.getStringValue(Images.ImageColumns.DATA) ?: continue
                        parents.add(path.getParentPath())
                    } while (cursor.moveToNext())
                }
            } else {
                val sorting = "${BaseColumns._ID} DESC LIMIT 10"
                cursor = context.contentResolver.query(uri, projection, null, null, sorting)
                if (cursor?.moveToFirst() == true) {
                    do {
                        val path = cursor.getStringValue(Images.ImageColumns.DATA) ?: continue
                        parents.add(path.getParentPath())
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return parents
    }

    private fun getSelectionQuery(filterMedia: Int): String {
        val query = StringBuilder()
        if (filterMedia and TYPE_IMAGES != 0) {
            photoExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_PORTRAITS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        if (filterMedia and TYPE_VIDEOS != 0) {
            videoExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_GIFS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        if (filterMedia and TYPE_RAWS != 0) {
            rawExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_SVGS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        return query.toString().trim().removeSuffix("OR")
    }

    private fun getSelectionArgsQuery(filterMedia: Int): ArrayList<String> {
        val args = ArrayList<String>()
        if (filterMedia and TYPE_IMAGES != 0) {
            photoExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_PORTRAITS != 0) {
            args.add("%.jpg")
            args.add("%.jpeg")
        }

        if (filterMedia and TYPE_VIDEOS != 0) {
            videoExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_GIFS != 0) {
            args.add("%.gif")
        }

        if (filterMedia and TYPE_RAWS != 0) {
            rawExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_SVGS != 0) {
            args.add("%.svg")
        }

        return args
    }

    private fun parseCursor(cursor: Cursor): LinkedHashSet<String> {
        val foldersToIgnore = arrayListOf("/storage/emulated/legacy")
        val config = context.config
        val includedFolders = config.includedFolders
        val OTGPath = config.OTGPath
        val foldersToScan = config.everShownFolders.filter { it == FAVORITES || it == RECYCLE_BIN || context.getDoesFilePathExist(it, OTGPath) }.toHashSet()

        cursor.use {
            if (cursor.moveToFirst()) {
                do {
                    val path = cursor.getStringValue(Images.Media.DATA)
                    val parentPath = File(path).parent ?: continue
                    if (!includedFolders.contains(parentPath) && !foldersToIgnore.contains(parentPath)) {
                        foldersToScan.add(parentPath)
                    }
                } while (cursor.moveToNext())
            }
        }

        includedFolders.forEach {
            addFolder(foldersToScan, it)
        }

        return foldersToScan.toMutableSet() as LinkedHashSet<String>
    }

    private fun addFolder(curFolders: HashSet<String>, folder: String) {
        curFolders.add(folder)
        val files = File(folder).listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                addFolder(curFolders, file.absolutePath)
            }
        }
    }

    private fun getMediaInFolder(
        folder: String, isPickImage: Boolean, isPickVideo: Boolean, filterMedia: Int, getProperDateTaken: Boolean,
        getProperLastModified: Boolean, getProperFileSize: Boolean, favoritePaths: ArrayList<String>,
        getVideoDurations: Boolean, lastModifieds: HashMap<String, Long>, dateTakens: HashMap<String, Long>
    ): ArrayList<Medium> {
        val media = ArrayList<Medium>()
        val isRecycleBin = folder == RECYCLE_BIN
        val deletedMedia = if (isRecycleBin) {
            context.getUpdatedDeletedMedia()
        } else {
            ArrayList()
        }

        val config = context.config
        val checkProperFileSize = getProperFileSize || config.fileLoadingPriority == PRIORITY_COMPROMISE
        val checkFileExistence = config.fileLoadingPriority == PRIORITY_VALIDITY
        val showHidden = config.shouldShowHidden
        val showPortraits = filterMedia and TYPE_PORTRAITS != 0
        val fileSizes = if (checkProperFileSize || checkFileExistence) getFolderSizes(folder) else HashMap()

        val files = when (folder) {
            FAVORITES -> favoritePaths.filter { showHidden || !it.contains("/.") }.map { File(it) }.toMutableList() as ArrayList<File>
            RECYCLE_BIN -> deletedMedia.map { File(it.path) }.toMutableList() as ArrayList<File>
            else -> File(folder).listFiles()?.toMutableList() ?: return media
        }

        for (curFile in files) {
            var file = curFile
            if (shouldStop) {
                break
            }

            var path = file.absolutePath
            var isPortrait = false
            val isImage = path.isImageFast()
            val isVideo = if (isImage) false else path.isVideoFast()
            val isGif = if (isImage || isVideo) false else path.isGif()
            val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
            val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
                if (showPortraits && file.name.startsWith("img_", true) && file.isDirectory) {
                    val portraitFiles = file.listFiles() ?: continue
                    val cover = portraitFiles.firstOrNull { it.name.contains("cover", true) } ?: portraitFiles.firstOrNull()
                    if (cover != null && !files.contains(cover)) {
                        file = cover
                        path = cover.absolutePath
                        isPortrait = true
                    } else {
                        continue
                    }
                } else {
                    continue
                }
            }

            if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                continue

            if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                continue

            if (isGif && filterMedia and TYPE_GIFS == 0)
                continue

            if (isRaw && filterMedia and TYPE_RAWS == 0)
                continue

            if (isSvg && filterMedia and TYPE_SVGS == 0)
                continue

            val filename = file.name
            if (!showHidden && filename.startsWith('.'))
                continue

            var size = 0L
            if (checkProperFileSize || checkFileExistence) {
                var newSize = fileSizes.remove(path)
                if (newSize == null) {
                    newSize = file.length()
                }
                size = newSize
            }

            if ((checkProperFileSize || checkFileExistence) && size <= 0L) {
                continue
            }

            if (checkFileExistence && (!file.exists() || !file.isFile)) {
                continue
            }

            if (isRecycleBin) {
                deletedMedia.firstOrNull { it.path == path }?.apply {
                    media.add(this)
                }
            } else {
                var lastModified: Long
                var newLastModified = lastModifieds.remove(path)
                if (newLastModified == null) {
                    newLastModified = if (getProperLastModified) {
                        file.lastModified()
                    } else {
                        0L
                    }
                }
                lastModified = newLastModified

                var dateTaken = lastModified
                val videoDuration = if (getVideoDurations && isVideo) context.getDuration(path) ?: 0 else 0

                if (getProperDateTaken) {
                    var newDateTaken = dateTakens.remove(path)
                    if (newDateTaken == null) {
                        newDateTaken = if (getProperLastModified) {
                            lastModified
                        } else {
                            file.lastModified()
                        }
                    }
                    dateTaken = newDateTaken
                }

                val type = when {
                    isVideo -> TYPE_VIDEOS
                    isGif -> TYPE_GIFS
                    isRaw -> TYPE_RAWS
                    isSvg -> TYPE_SVGS
                    isPortrait -> TYPE_PORTRAITS
                    else -> TYPE_IMAGES
                }

                val isFavorite = favoritePaths.contains(path)
                val medium = Medium(null, filename, path, file.parent, lastModified, dateTaken, size, type, videoDuration, isFavorite, 0L, 0L)
                media.add(medium)
            }
        }

        return media
    }

    fun getAndroid11FolderMedia(
        isPickImage: Boolean,
        isPickVideo: Boolean,
        favoritePaths: ArrayList<String>,
        getFavoritePathsOnly: Boolean,
        getProperDateTaken: Boolean,
        dateTakens: HashMap<String, Long>
    ): HashMap<String, ArrayList<Medium>> {
        val media = HashMap<String, ArrayList<Medium>>()
        if (!isRPlus() || Environment.isExternalStorageManager()) {
            return media
        }

        val filterMedia = context.config.filterMedia
        val showHidden = context.config.shouldShowHidden

        val projection = arrayOf(
            Images.Media._ID,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATA,
            Images.Media.DATE_MODIFIED,
            Images.Media.DATE_TAKEN,
            Images.Media.SIZE,
            MediaStore.MediaColumns.DURATION
        )

        val uri = Files.getContentUri("external")

        context.queryCursor(uri, projection) { cursor ->
            if (shouldStop) {
                return@queryCursor
            }

            try {
                val mediaStoreId = cursor.getLongValue(Images.Media._ID)
                val filename = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                val path = cursor.getStringValue(Images.Media.DATA)
                if (getFavoritePathsOnly && !favoritePaths.contains(path)) {
                    return@queryCursor
                }

                val isPortrait = false
                val isImage = path.isImageFast()
                val isVideo = if (isImage) false else path.isVideoFast()
                val isGif = if (isImage || isVideo) false else path.isGif()
                val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

                if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
                    return@queryCursor
                }

                if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                    return@queryCursor

                if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                    return@queryCursor

                if (isGif && filterMedia and TYPE_GIFS == 0)
                    return@queryCursor

                if (isRaw && filterMedia and TYPE_RAWS == 0)
                    return@queryCursor

                if (isSvg && filterMedia and TYPE_SVGS == 0)
                    return@queryCursor

                if (!showHidden && filename.startsWith('.'))
                    return@queryCursor

                val size = cursor.getLongValue(Images.Media.SIZE)
                if (size <= 0L) {
                    return@queryCursor
                }

                val type = when {
                    isVideo -> TYPE_VIDEOS
                    isGif -> TYPE_GIFS
                    isRaw -> TYPE_RAWS
                    isSvg -> TYPE_SVGS
                    isPortrait -> TYPE_PORTRAITS
                    else -> TYPE_IMAGES
                }

                val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                var dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)

                if (getProperDateTaken) {
                    dateTaken = dateTakens.remove(path) ?: lastModified
                }

                if (dateTaken == 0L) {
                    dateTaken = lastModified
                }

                val videoDuration = Math.round(cursor.getIntValue(MediaStore.MediaColumns.DURATION) / 1000.toDouble()).toInt()
                val isFavorite = favoritePaths.contains(path)
                val medium =
                    Medium(null, filename, path, path.getParentPath(), lastModified, dateTaken, size, type, videoDuration, isFavorite, 0L, mediaStoreId)
                val parent = medium.parentPath.lowercase(Locale.getDefault())
                val currentFolderMedia = media[parent]
                if (currentFolderMedia == null) {
                    media[parent] = ArrayList<Medium>()
                }

                media[parent]?.add(medium)
            } catch (e: Exception) {
            }
        }

        return media
    }

    private fun getMediaOnOTG(
        folder: String, isPickImage: Boolean, isPickVideo: Boolean, filterMedia: Int, favoritePaths: ArrayList<String>,
        getVideoDurations: Boolean
    ): ArrayList<Medium> {
        val media = ArrayList<Medium>()
        val files = context.getDocumentFile(folder)?.listFiles() ?: return media
        val checkFileExistence = context.config.fileLoadingPriority == PRIORITY_VALIDITY
        val showHidden = context.config.shouldShowHidden
        val OTGPath = context.config.OTGPath

        for (file in files) {
            if (shouldStop) {
                break
            }

            val filename = file.name ?: continue
            val isImage = filename.isImageFast()
            val isVideo = if (isImage) false else filename.isVideoFast()
            val isGif = if (isImage || isVideo) false else filename.isGif()
            val isRaw = if (isImage || isVideo || isGif) false else filename.isRawFast()
            val isSvg = if (isImage || isVideo || isGif || isRaw) false else filename.isSvg()

            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg)
                continue

            if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                continue

            if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                continue

            if (isGif && filterMedia and TYPE_GIFS == 0)
                continue

            if (isRaw && filterMedia and TYPE_RAWS == 0)
                continue

            if (isSvg && filterMedia and TYPE_SVGS == 0)
                continue

            if (!showHidden && filename.startsWith('.'))
                continue

            val size = file.length()
            if (size <= 0L || (checkFileExistence && !context.getDoesFilePathExist(file.uri.toString(), OTGPath)))
                continue

            val dateTaken = file.lastModified()
            val dateModified = file.lastModified()

            val type = when {
                isVideo -> TYPE_VIDEOS
                isGif -> TYPE_GIFS
                isRaw -> TYPE_RAWS
                isSvg -> TYPE_SVGS
                else -> TYPE_IMAGES
            }

            val path = Uri.decode(
                file.uri.toString().replaceFirst("${context.config.OTGTreeUri}/document/${context.config.OTGPartition}%3A", "${context.config.OTGPath}/")
            )
            val videoDuration = if (getVideoDurations) context.getDuration(path) ?: 0 else 0
            val isFavorite = favoritePaths.contains(path)
            val medium = Medium(null, filename, path, folder, dateModified, dateTaken, size, type, videoDuration, isFavorite, 0L, 0L)
            media.add(medium)
        }

        return media
    }

    fun getFolderDateTakens(folder: String): HashMap<String, Long> {
        val dateTakens = HashMap<String, Long>()
        if (folder != FAVORITES) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.DATE_TAKEN
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
                    if (dateTaken != 0L) {
                        val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                        dateTakens["$folder/$name"] = dateTaken
                    }
                } catch (e: Exception) {
                }
            }
        }

        val dateTakenValues = try {
            if (folder == FAVORITES) {
                context.dateTakensDB.getAllDateTakens()
            } else {
                context.dateTakensDB.getDateTakensFromPath(folder)
            }
        } catch (e: Exception) {
            return dateTakens
        }

        dateTakenValues.forEach {
            dateTakens[it.fullPath] = it.taken
        }

        return dateTakens
    }

    fun getDateTakens(): HashMap<String, Long> {
        val dateTakens = HashMap<String, Long>()
        val projection = arrayOf(
            Images.Media.DATA,
            Images.Media.DATE_TAKEN
        )

        val uri = Files.getContentUri("external")

        try {
            context.queryCursor(uri, projection) { cursor ->
                try {
                    val dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
                    if (dateTaken != 0L) {
                        val path = cursor.getStringValue(Images.Media.DATA)
                        dateTakens[path] = dateTaken
                    }
                } catch (e: Exception) {
                }
            }

            val dateTakenValues = context.dateTakensDB.getAllDateTakens()

            dateTakenValues.forEach {
                dateTakens[it.fullPath] = it.taken
            }
        } catch (e: Exception) {
        }

        return dateTakens
    }

    fun getFolderLastModifieds(folder: String): HashMap<String, Long> {
        val lastModifieds = HashMap<String, Long>()
        if (folder != FAVORITES) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.DATE_MODIFIED
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                    if (lastModified != 0L) {
                        val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                        lastModifieds["$folder/$name"] = lastModified
                    }
                } catch (e: Exception) {
                }
            }
        }

        return lastModifieds
    }

    fun getLastModifieds(): HashMap<String, Long> {
        val lastModifieds = HashMap<String, Long>()
        val projection = arrayOf(
            Images.Media.DATA,
            Images.Media.DATE_MODIFIED
        )

        val uri = Files.getContentUri("external")

        try {
            context.queryCursor(uri, projection) { cursor ->
                try {
                    val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                    if (lastModified != 0L) {
                        val path = cursor.getStringValue(Images.Media.DATA)
                        lastModifieds[path] = lastModified
                    }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }

        return lastModifieds
    }

    private fun getFolderSizes(folder: String): HashMap<String, Long> {
        val sizes = HashMap<String, Long>()
        if (folder != FAVORITES) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.SIZE
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val size = cursor.getLongValue(Images.Media.SIZE)
                    if (size != 0L) {
                        val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                        sizes["$folder/$name"] = size
                    }
                } catch (e: Exception) {
                }
            }
        }

        return sizes
    }

    fun sortMedia(media: ArrayList<Medium>, sorting: Int) {
        if (sorting and SORT_BY_RANDOM != 0) {
            media.shuffle()
            return
        }

        media.sortWith { o1, o2 ->
            o1 as Medium
            o2 as Medium
            var result = when {
                sorting and SORT_BY_NAME != 0 -> {
                    if (sorting and SORT_USE_NUMERIC_VALUE != 0) {
                        AlphanumericComparator().compare(o1.name.normalizeString().lowercase(Locale.getDefault()), o2.name.normalizeString().lowercase(Locale.getDefault()))
                    } else {
                        o1.name.normalizeString().lowercase(Locale.getDefault()).compareTo(o2.name.normalizeString().lowercase(Locale.getDefault()))
                    }
                }

                sorting and SORT_BY_PATH != 0 -> {
                    if (sorting and SORT_USE_NUMERIC_VALUE != 0) {
                        AlphanumericComparator().compare(o1.path.lowercase(Locale.getDefault()), o2.path.lowercase(Locale.getDefault()))
                    } else {
                        o1.path.lowercase(Locale.getDefault()).compareTo(o2.path.lowercase(Locale.getDefault()))
                    }
                }

                sorting and SORT_BY_SIZE != 0 -> o1.size.compareTo(o2.size)
                sorting and SORT_BY_DATE_MODIFIED != 0 -> o1.modified.compareTo(o2.modified)
                else -> o1.taken.compareTo(o2.taken)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }
            result
        }
    }

    fun groupMedia(media: ArrayList<Medium>, path: String): ArrayList<ThumbnailItem> {
        val pathToCheck = if (path.isEmpty()) SHOW_ALL else path
        val currentGrouping = context.config.getFolderGrouping(pathToCheck)
        if (currentGrouping and GROUP_BY_NONE != 0) {
            return media as ArrayList<ThumbnailItem>
        }

        val thumbnailItems = ArrayList<ThumbnailItem>()
        if (context.config.scrollHorizontally) {
            media.mapTo(thumbnailItems) { it }
            return thumbnailItems
        }

        val mediumGroups = LinkedHashMap<String, ArrayList<Medium>>()
        media.forEach {
            val key = it.getGroupingKey(currentGrouping)
            if (!mediumGroups.containsKey(key)) {
                mediumGroups[key] = ArrayList()
            }
            mediumGroups[key]!!.add(it)
        }

        val sortDescending = currentGrouping and GROUP_DESCENDING != 0
        val sorted = if (currentGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 || currentGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 ||
            currentGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 || currentGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0
        ) {
            mediumGroups.toSortedMap(if (sortDescending) compareByDescending {
                it.toLongOrNull() ?: 0L
            } else {
                compareBy { it.toLongOrNull() ?: 0L }
            })
        } else {
            mediumGroups.toSortedMap(if (sortDescending) compareByDescending { it } else compareBy { it })
        }

        mediumGroups.clear()
        for ((key, value) in sorted) {
            mediumGroups[key] = value
        }

        val today = formatDate(System.currentTimeMillis().toString(), true)
        val yesterday = formatDate((System.currentTimeMillis() - DAY_SECONDS * 1000).toString(), true)
        for ((key, value) in mediumGroups) {
            var currentGridPosition = 0
            val sectionKey = getFormattedKey(key, currentGrouping, today, yesterday, value.size)
            thumbnailItems.add(ThumbnailSection(sectionKey))

            value.forEach {
                it.gridPosition = currentGridPosition++
            }

            thumbnailItems.addAll(value)
        }

        return thumbnailItems
    }

    private fun getFormattedKey(key: String, grouping: Int, today: String, yesterday: String, count: Int): String {
        var result = when {
            grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0 -> getFinalDate(
                formatDate(key, true),
                today,
                yesterday
            )

            grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0 -> formatDate(key, false)
            grouping and GROUP_BY_FILE_TYPE != 0 -> getFileTypeString(key)
            grouping and GROUP_BY_EXTENSION != 0 -> key.uppercase(Locale.getDefault())
            grouping and GROUP_BY_FOLDER != 0 -> context.humanizePath(key)
            else -> key
        }

        if (result.isEmpty()) {
            result = context.getString(org.fossify.commons.R.string.unknown)
        }

        return if (grouping and GROUP_SHOW_FILE_COUNT != 0) {
            "$result ($count)"
        } else {
            result
        }
    }

    private fun getFinalDate(date: String, today: String, yesterday: String): String {
        return when (date) {
            today -> context.getString(org.fossify.commons.R.string.today)
            yesterday -> context.getString(org.fossify.commons.R.string.yesterday)
            else -> date
        }
    }

    private fun formatDate(timestamp: String, showDay: Boolean): String {
        return if (timestamp.areDigitsOnly()) {
            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.timeInMillis = timestamp.toLong()
            val format = if (showDay) context.config.dateFormat else "MMMM yyyy"
            DateFormat.format(format, cal).toString()
        } else {
            ""
        }
    }

    private fun getFileTypeString(key: String): String {
        val stringId = when (key.toInt()) {
            TYPE_IMAGES -> R.string.images
            TYPE_VIDEOS -> R.string.videos
            TYPE_GIFS -> R.string.gifs
            TYPE_RAWS -> R.string.raw_images
            TYPE_SVGS -> R.string.svgs
            else -> R.string.portraits
        }
        return context.getString(stringId)
    }
}

package org.fossify.gallery.activities

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.deleteFiles
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileCount
import org.fossify.commons.extensions.getFilePublicUri
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperSize
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getStorageDirectories
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hasAllPermissions
import org.fossify.commons.extensions.hasOTGConnected
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.DAY_SECONDS
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.PERMISSION_READ_STORAGE
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.databinding.ActivityMainBinding
import org.fossify.gallery.dialogs.ChangeSortingDialog
import org.fossify.gallery.dialogs.ChangeViewTypeDialog
import org.fossify.gallery.dialogs.FilterMediaDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.addTempFolderIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.createDirectoryFromMedia
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.getCachedDirectories
import org.fossify.gallery.extensions.getCachedMedia
import org.fossify.gallery.extensions.getDirectorySortingValue
import org.fossify.gallery.extensions.getDirsToShow
import org.fossify.gallery.extensions.getDistinctPath
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import org.fossify.gallery.extensions.getOTGFolderChildrenNames
import org.fossify.gallery.extensions.getSortedDirectories
import org.fossify.gallery.extensions.shouldFolderBeVisible
import org.fossify.gallery.extensions.handleExcludedFolderPasswordProtection
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.isThisOrParentIncluded
import org.fossify.gallery.extensions.launchAbout
import org.fossify.gallery.extensions.launchCamera
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.logPerf
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.movePinnedDirectoriesToFront
import org.fossify.gallery.extensions.openRecycleBin
import org.fossify.gallery.extensions.removeInvalidDBDirectories
import org.fossify.gallery.extensions.storeDirectoryItems
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.extensions.updateDBDirectory
import org.fossify.gallery.extensions.updateWidgets
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.GET_ANY_INTENT
import org.fossify.gallery.helpers.GET_IMAGE_INTENT
import org.fossify.gallery.helpers.GET_VIDEO_INTENT
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import org.fossify.gallery.helpers.GROUP_DESCENDING
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.MAX_COLUMN_COUNT
import org.fossify.gallery.helpers.MONTH_MILLISECONDS
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.PICKED_PATHS
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.getDefaultFileFilter
import org.fossify.gallery.helpers.getPermissionToRequest
import org.fossify.gallery.helpers.getPermissionsToRequest
import org.fossify.gallery.interfaces.DirectoryOperationsListener
import org.fossify.gallery.jobs.NewPhotoFetcher
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

class MainActivity : SimpleActivity(), DirectoryOperationsListener {
    override var isSearchBarEnabled = true
    
    companion object {
        private const val PICK_MEDIA = 2
        private const val PICK_WALLPAPER = 3
        private const val LAST_MEDIA_CHECK_PERIOD = 3000L
        private const val MAX_DIRS_LOAD_TIME_MS = 300000L
        var pendingFullScan = false
    }

    private var mIsPickImageIntent = false
    private var mIsPickVideoIntent = false
    private var mIsGetImageContentIntent = false
    private var mIsGetVideoContentIntent = false
    private var mIsGetAnyContentIntent = false
    private var mIsSetWallpaperIntent = false
    private var mAllowPickingMultiple = false
    private var mIsThirdPartyIntent = false
    private var mIsGettingDirs = false
    private var mLoadedInitialPhotos = false
    private var mShouldStopFetching = false
    private var mScanGeneration = 1L
    private var mDirsLoadStartTime = 0L
    private var mPendingRestart = false
    private var mPendingFullVolumeScan = false
    private var mPendingFilesystemScan = true
    private var mMediaScanInProgress = false
    @Volatile private var mMediaScanCompleted = false
    private var mWasDefaultFolderChecked = false
    private var mWasMediaManagementPromptShown = false
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L

    // used at "Group direct subfolders" for navigation
    private var mCurrentPathPrefix = ""

    // used at "Group direct subfolders" for navigating Up with the back button
    private var mOpenedSubfolders = arrayListOf("")

    private var mDateFormat = ""
    private var mTimeFormat = ""
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null
    private var mLastMediaFetcher: MediaFetcher? = null
    private var mDirs = ArrayList<Directory>()
    private var mDirsIgnoringSearch = ArrayList<Directory>()

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredStyleString = ""
    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (savedInstanceState == null) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            removeTempFolder()
            checkRecycleBinItems()
            startNewPhotoFetcher()
        }

        mIsPickImageIntent = isPickImageIntent(intent)
        mIsPickVideoIntent = isPickVideoIntent(intent)
        mIsGetImageContentIntent = isGetImageContentIntent(intent)
        mIsGetVideoContentIntent = isGetVideoContentIntent(intent)
        mIsGetAnyContentIntent = isGetAnyContentIntent(intent)
        mIsSetWallpaperIntent = isSetWallpaperIntent(intent)
        mAllowPickingMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        mIsThirdPartyIntent = mIsPickImageIntent
                || mIsPickVideoIntent
                || mIsGetImageContentIntent
                || mIsGetVideoContentIntent
                || mIsGetAnyContentIntent
                || mIsSetWallpaperIntent

        setupOptionsMenu()
        refreshMenuItems()

        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.directoriesGrid)
        )

        binding.directoriesRefreshLayout.setOnRefreshListener { getDirectories(true, source = "pull-to-refresh") }
        storeStateVariables()
        checkWhatsNewDialog()
        setupLatestMediaId()

        if (!config.wereFavoritesPinned) {
            config.addPinnedFolders(hashSetOf(FAVORITES))
            config.wereFavoritesPinned = true
        }

        if (!config.wasRecycleBinPinned) {
            config.addPinnedFolders(hashSetOf(RECYCLE_BIN))
            config.wasRecycleBinPinned = true
            config.saveFolderGrouping(SHOW_ALL, GROUP_BY_DATE_TAKEN_DAILY or GROUP_DESCENDING)
        }

        if (!config.wasSVGShowingHandled) {
            config.wasSVGShowingHandled = true
            if (config.filterMedia and TYPE_SVGS == 0) {
                config.filterMedia += TYPE_SVGS
            }
        }

        if (!config.wasSortingByNumericValueAdded) {
            config.wasSortingByNumericValueAdded = true
            config.sorting = config.sorting or SORT_USE_NUMERIC_VALUE
        }

        updateWidgets()
        registerFileUpdateListener()

        binding.directoriesSwitchSearching.setOnClickListener {
            launchSearchActivity()
        }

        // just request the permission, tryLoadGallery will then trigger in onResume
        handleMediaPermissions()
    }

    private fun handleMediaPermissions(callback: (() -> Unit)? = null) {
        requestMediaPermissions(enableRationale = true) {
            callback?.invoke()
            if (isRPlus() && !mWasMediaManagementPromptShown) {
                mWasMediaManagementPromptShown = true
                handleMediaManagementPrompt { }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        config.isThirdPartyIntent = false
        mDateFormat = config.dateFormat
        mTimeFormat = getTimeFormat()

        refreshMenuItems()

        if (mStoredAnimateGifs != config.animateGifs) {
            getRecyclerAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getRecyclerAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            binding.directoriesGrid.adapter = null
            getDirectories(true, source = "scrollHorizontally changed")
        }

        if (mStoredTextColor != getProperTextColor()) {
            getRecyclerAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getRecyclerAdapter()?.updatePrimaryColor()
        }

        val styleString =
            "${config.folderStyle}${config.showFolderMediaCount}${config.limitFolderTitle}"
        if (mStoredStyleString != styleString) {
            setupAdapter(mDirs, forceRecreate = true)
        }

        binding.directoriesFastscroller.updateColors(primaryColor)
        binding.directoriesRefreshLayout.isEnabled = config.enablePullToRefresh
        getRecyclerAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.directoriesEmptyPlaceholder.setTextColor(getProperTextColor())
        binding.directoriesEmptyPlaceholder2.setTextColor(primaryColor)
        binding.directoriesSwitchSearching.setTextColor(primaryColor)
        binding.directoriesSwitchSearching.underlineText()
        binding.directoriesEmptyPlaceholder2.bringToFront()

        if (!binding.mainMenu.isSearchOpen) {
            refreshMenuItems()
            if (pendingFullScan) {
                pendingFullScan = false
                config.lastFolderScanTimestamp = 0L
                binding.directoriesGrid.adapter = null
                getDirectories(true, fullVolumeScan = true, source = "pendingFullScan (Level 1 menu)")
            } else {
                tryLoadGallery()
            }
        }

        if (config.searchAllFilesByDefault) {
            binding.mainMenu.updateHintText(getString(org.fossify.commons.R.string.search_files))
        } else {
            binding.mainMenu.updateHintText(getString(org.fossify.commons.R.string.search_folders))
        }
    }

    override fun onPause() {
        super.onPause()
        binding.directoriesRefreshLayout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation || config.temporarilyShowExcluded) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.temporarilyShowExcluded = false
                config.tempSkipDeleteConfirmation = false
                config.tempSkipRecycleBin = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
            removeTempFolder()
            unregisterFileUpdateListener()

            if (!config.showAll) {
                mLastMediaFetcher?.shouldStop = true
                GalleryDatabase.destroyInstance()
            }
        }
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else if (config.groupDirectSubfolders) {
            if (mCurrentPathPrefix.isEmpty()) {
                appLockManager.lock()
                false
            } else {
                mOpenedSubfolders.removeAt(mOpenedSubfolders.lastIndex)
                mCurrentPathPrefix = mOpenedSubfolders.last()
                setupAdapter(mDirs)
                true
            }
        } else {
            appLockManager.lock()
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_MEDIA && resultData != null) {
                val resultIntent = Intent()
                var resultUri: Uri? = null
                if (mIsThirdPartyIntent) {
                    when {
                        intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true
                                && intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 -> {
                            resultUri = fillExtraOutput(resultData)
                        }

                        resultData.extras?.containsKey(PICKED_PATHS) == true -> {
                            fillPickedPaths(resultData, resultIntent)
                        }

                        else -> fillIntentPath(resultData, resultIntent)
                    }
                }

                if (resultUri != null) {
                    resultIntent.data = resultUri
                    resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                setResult(RESULT_OK, resultIntent)
                finish()
            } else if (requestCode == PICK_WALLPAPER) {
                setResult(RESULT_OK)
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun refreshMenuItems() {
        if (!mIsThirdPartyIntent) {
            binding.mainMenu.requireToolbar().menu.apply {
                findItem(R.id.column_count).isVisible = config.viewTypeFolders == VIEW_TYPE_GRID
                findItem(R.id.set_as_default_folder).isVisible = !config.defaultFolder.isEmpty()
                findItem(R.id.open_recycle_bin).isVisible =
                    config.useRecycleBin && !config.showRecycleBinAtFolders
                findItem(R.id.more_apps_from_us).isVisible =
                    !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
            }
        }

        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible =
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            findItem(R.id.temporarily_show_excluded).isVisible = !config.temporarilyShowExcluded
            findItem(R.id.stop_showing_excluded).isVisible = config.temporarilyShowExcluded
        }
    }

    private fun setupOptionsMenu() {
        val menuId = if (mIsThirdPartyIntent) {
            R.menu.menu_main_intent
        } else {
            R.menu.menu_main
        }

        binding.mainMenu.requireToolbar().inflateMenu(menuId)
        binding.mainMenu.toggleHideOnScroll(!config.scrollHorizontally)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchOpenListener = {
            if (config.searchAllFilesByDefault) {
                launchSearchActivity()
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            setupAdapter(mDirsIgnoringSearch, text)
            binding.directoriesRefreshLayout.isEnabled =
                text.isEmpty() && config.enablePullToRefresh
            binding.directoriesSwitchSearching.beVisibleIf(text.isNotEmpty())
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.open_camera -> launchCamera()
                R.id.show_all -> showAllMedia()
                R.id.change_view_type -> changeViewType()
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.temporarily_show_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.stop_showing_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.create_new_folder -> createNewFolder()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.column_count -> changeColumnCount()
                R.id.set_as_default_folder -> setAsDefaultFolder()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun getRecyclerAdapter() = binding.directoriesGrid.adapter as? DirectoryAdapter

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredStyleString = "$folderStyle$showFolderMediaCount$limitFolderTitle"
        }
    }

    private fun startNewPhotoFetcher() {
        val photoFetcher = NewPhotoFetcher()
        if (!photoFetcher.isScheduled(applicationContext)) {
            photoFetcher.scheduleJob(applicationContext)
        }
    }

    private fun removeTempFolder() {
        if (config.tempFolderPath.isNotEmpty()) {
            val newFolder = File(config.tempFolderPath)
            if (getDoesFilePathExist(newFolder.absolutePath) && newFolder.isDirectory) {
                if (
                    newFolder.getProperSize(true) == 0L
                    && newFolder.getFileCount(true) == 0
                    && newFolder.list()?.isEmpty() == true
                ) {
                    toast(
                        String.format(
                            getString(org.fossify.commons.R.string.deleting_folder),
                            config.tempFolderPath
                        ), Toast.LENGTH_LONG
                    )
                    tryDeleteFileDirItem(newFolder.toFileDirItem(applicationContext), true, true)
                }
            }
            config.tempFolderPath = ""
        }
    }

    private fun checkOTGPath() {
        ensureBackgroundThread {
            if (!config.wasOTGHandled && hasPermission(getPermissionToRequest()) && hasOTGConnected() && config.OTGPath.isEmpty()) {
                getStorageDirectories().firstOrNull {
                    it.trimEnd('/') != internalStoragePath
                            && it.trimEnd('/') != sdCardPath
                }?.apply {
                    config.wasOTGHandled = true
                    val otgPath = trimEnd('/')
                    config.OTGPath = otgPath
                    config.addIncludedFolder(otgPath)
                }
            }
        }
    }

    private fun checkDefaultSpamFolders() {
        if (!config.spamFoldersChecked) {
            val spamFolders = arrayListOf(
                "/storage/emulated/0/Android/data/com.facebook.orca/files/stickers"
            )

            val OTGPath = config.OTGPath
            spamFolders.forEach {
                if (getDoesFilePathExist(it, OTGPath)) {
                    config.addExcludedFolder(it)
                }
            }
            config.spamFoldersChecked = true
        }
    }

    private fun tryLoadGallery() {
        logPerf("")
        logPerf("========== tryLoadGallery: started ==========")
        // avoid calling anything right after granting the permission, it will be called from onResume()
        val wasMissingPermission =
            config.appRunCount == 1 && !hasAllPermissions(getPermissionsToRequest())
        handleMediaPermissions {
            if (wasMissingPermission) {
                return@handleMediaPermissions
            }

            if (!mWasDefaultFolderChecked) {
                openDefaultFolder()
                mWasDefaultFolderChecked = true
            }

            checkOTGPath()
            checkDefaultSpamFolders()

            if (config.showAll) {
                showAllMedia()
            } else {
                getDirectories()
            }

            setupLayoutManager()
        }
    }

    private fun getDirectories(forceRestart: Boolean = false, fullVolumeScan: Boolean = false, filesystemScan: Boolean = true, source: String = "") {
        logPerf("getDirectories: called forceRestart=$forceRestart fullVolumeScan=$fullVolumeScan filesystemScan=$filesystemScan source='$source'")
        if (mIsGettingDirs) {
            val elapsed = SystemClock.elapsedRealtime() - mDirsLoadStartTime
            if (elapsed > MAX_DIRS_LOAD_TIME_MS) {
                logPerf("getDirectories: scan stuck for ${elapsed} ms, forcing restart")
                mShouldStopFetching = true
                mIsGettingDirs = false
                mDirsLoadStartTime = 0L
                // Fall through to start a new scan
            } else if (forceRestart && source != "pull-to-refresh") {
                logPerf("getDirectories: scan already running for ${elapsed} ms, marking pending restart")
                mShouldStopFetching = true
                mPendingRestart = true
                mPendingFullVolumeScan = fullVolumeScan
                mPendingFilesystemScan = filesystemScan
                return
            } else {
                // For pull-to-refresh and non-forced calls, let the current scan finish.
                // The running scan already queries MediaStore and will pick up new media.
                // Interrupting it and starting a new scan just doubles the work.
                logPerf("getDirectories: scan already running for ${elapsed} ms, letting it finish")
                return
            }
        }

        mShouldStopFetching = false
        mIsGettingDirs = true
        mPendingRestart = false
        mDirsLoadStartTime = SystemClock.elapsedRealtime()
        mScanGeneration++
        val generation = mScanGeneration
        val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent

        getCachedDirectories(getVideos && !getImages, getImages && !getVideos) {
            gotDirectories(addTempFolderIfNeeded(it), generation, forceRestart, fullVolumeScan, filesystemScan)
        }
    }

    private fun launchSearchActivity() {
        hideKeyboard()
        Intent(this, SearchActivity::class.java).apply {
            startActivity(this)
        }

        binding.mainMenu.postDelayed({
            binding.mainMenu.closeSearch()
        }, 500)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, true, false) {
            binding.directoriesGrid.adapter = null
            if (config.directorySorting and SORT_BY_DATE_MODIFIED != 0 || config.directorySorting and SORT_BY_DATE_TAKEN != 0) {
                getDirectories(true, source = "sorting changed")
            } else {
                ensureBackgroundThread {
                    gotDirectories(getCurrentlyDisplayedDirs())
                }
            }

            getRecyclerAdapter()?.directorySorting = config.directorySorting
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mShouldStopFetching = true
            binding.directoriesRefreshLayout.isRefreshing = true
            binding.directoriesGrid.adapter = null
            getDirectories(true, source = "filterMedia changed")
        }
    }

    private fun showAllMedia() {
        config.showAll = true
        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, "")

            if (mIsThirdPartyIntent) {
                handleMediaIntent(this)
            } else {
                hideKeyboard()
                startActivity(this)
                finish()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, true) {
            refreshMenuItems()
            setupLayoutManager()
            binding.directoriesGrid.adapter = null
            setupAdapter(getRecyclerAdapter()?.dirs ?: mDirs)
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleTemporarilyShowHidden(true)
                }
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowHidden = show
        binding.directoriesGrid.adapter = null
        getDirectories(true, source = "temporarilyShowHidden")
        refreshMenuItems()
    }

    private fun tryToggleTemporarilyShowExcluded() {
        if (config.temporarilyShowExcluded) {
            toggleTemporarilyShowExcluded(false)
        } else {
            handleExcludedFolderPasswordProtection {
                toggleTemporarilyShowExcluded(true)
            }
        }
    }

    private fun toggleTemporarilyShowExcluded(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowExcluded = show
        binding.directoriesGrid.adapter = null
        getDirectories(true, source = "temporarilyShowExcluded")
        refreshMenuItems()
    }

    override fun deleteFolders(folders: ArrayList<File>) {
        val fileDirItems = folders
            .asSequence()
            .filter { it.isDirectory }
            .map { FileDirItem(it.absolutePath, it.name, true) }
            .toMutableList() as ArrayList<FileDirItem>

        when {
            fileDirItems.isEmpty() -> return
            fileDirItems.size == 1 -> {
                try {
                    toast(
                        String.format(
                            getString(org.fossify.commons.R.string.deleting_folder),
                            fileDirItems.first().name
                        )
                    )
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> {
                val baseString = if (config.useRecycleBin && !config.tempSkipRecycleBin) {
                    org.fossify.commons.R.plurals.moving_items_into_bin
                } else {
                    org.fossify.commons.R.plurals.delete_items
                }

                toast(
                    msg = resources.getQuantityString(
                        baseString, fileDirItems.size, fileDirItems.size
                    )
                )
            }
        }

        val itemsToDelete = ArrayList<FileDirItem>()
        val filter = config.filterMedia
        val showHidden = config.shouldShowHidden
        fileDirItems.filter { it.isDirectory }.forEach {
            val files = File(it.path).listFiles()
            files?.filter {
                it.absolutePath.isMediaFile()
                        && (showHidden || !it.name.startsWith('.'))
                        && ((it.isImageFast() && filter and TYPE_IMAGES != 0)
                        || (it.isVideoFast() && filter and TYPE_VIDEOS != 0)
                        || (it.isGif() && filter and TYPE_GIFS != 0)
                        || (it.isRawFast() && filter and TYPE_RAWS != 0)
                        || (it.isSvg() && filter and TYPE_SVGS != 0))
            }?.mapTo(itemsToDelete) { it.toFileDirItem(applicationContext) }
        }

        if (config.useRecycleBin && !config.tempSkipRecycleBin) {
            val pathsToDelete = ArrayList<String>()
            itemsToDelete.mapTo(pathsToDelete) { it.path }

            movePathsInRecycleBin(pathsToDelete) {
                if (it) {
                    deleteFilteredFileDirItems(itemsToDelete, folders)
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        } else {
            deleteFilteredFileDirItems(itemsToDelete, folders)
        }
    }

    private fun deleteFilteredFileDirItems(
        fileDirItems: ArrayList<FileDirItem>,
        folders: ArrayList<File>
    ) {
        val OTGPath = config.OTGPath
        deleteFiles(fileDirItems) {
            runOnUiThread {
                refreshItems()
            }

            ensureBackgroundThread {
                folders.filter { !getDoesFilePathExist(it.absolutePath, OTGPath) }.forEach {
                    directoryDB.deleteDirPath(it.absolutePath)
                }

                if (config.deleteEmptyFolders) {
                    folders.filter {
                        !it.absolutePath.isDownloadsFolder()
                                && it.isDirectory
                                && it.toFileDirItem(this).getProperFileCount(this, true) == 0
                    }
                        .forEach {
                            tryDeleteFileDirItem(it.toFileDirItem(this), true, true)
                        }
                }
            }
        }
    }

    private fun setupLayoutManager() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }

        (binding.directoriesRefreshLayout.layoutParams as RelativeLayout.LayoutParams)
            .addRule(RelativeLayout.BELOW, R.id.directories_switch_searching)
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.directoriesRefreshLayout.layoutParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.directoriesRefreshLayout.layoutParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }

        layoutManager.spanCount = config.dirColumnCnt
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.directoriesRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mZoomListener = null
    }

    private fun initZoomListener() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun createNewFolder() {
        FilePickerDialog(this, internalStoragePath, false, config.shouldShowHidden, false, true) {
            CreateNewFolderDialog(this, it) {
                config.tempFolderPath = it
                ensureBackgroundThread {
                    gotDirectories(addTempFolderIfNeeded(getCurrentlyDisplayedDirs()))
                }
            }
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..MAX_COLUMN_COUNT) {
            items.add(
                RadioItem(
                    id = i,
                    title = resources.getQuantityString(
                        org.fossify.commons.R.plurals.column_counts, i, i
                    )
                )
            )
        }

        val currentColumnCount =
            (binding.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.dirColumnCnt = newColumnCount
                columnCountChanged()
            }
        }
    }

    private fun increaseColumnCount() {
        config.dirColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.dirColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount =
            config.dirColumnCnt
        refreshMenuItems()
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, dirs.size)
        }
    }

    private fun isPickImageIntent(intent: Intent): Boolean {
        return isPickIntent(intent) && (hasImageContentData(intent) || isImageType(intent))
    }

    private fun isPickVideoIntent(intent: Intent): Boolean {
        return isPickIntent(intent) && (hasVideoContentData(intent) || isVideoType(intent))
    }

    private fun isPickIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_PICK
    }

    private fun isGetContentIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_GET_CONTENT && intent.type != null
    }

    private fun anyExtraMimeTypeStartingWith(intent: Intent, mimeTypePrefix: String): Boolean {
        return intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
            ?.any { it.startsWith(mimeTypePrefix) } == true
    }

    private fun isGetImageContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent)
                && (intent.type!!.startsWith("image/")
                || intent.type == Images.Media.CONTENT_TYPE
                || anyExtraMimeTypeStartingWith(intent, "image/"))
    }

    private fun isGetVideoContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent)
                && (intent.type!!.startsWith("video/")
                || intent.type == Video.Media.CONTENT_TYPE
                || anyExtraMimeTypeStartingWith(intent, "video/"))
    }

    private fun isGetAnyContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent) && intent.type == "*/*"
    }

    private fun isSetWallpaperIntent(intent: Intent?): Boolean {
        return intent?.action == Intent.ACTION_SET_WALLPAPER
    }

    private fun hasImageContentData(intent: Intent): Boolean {
        return intent.data == Images.Media.EXTERNAL_CONTENT_URI
                || intent.data == Images.Media.INTERNAL_CONTENT_URI
    }

    private fun hasVideoContentData(intent: Intent): Boolean {
        return intent.data == Video.Media.EXTERNAL_CONTENT_URI
                || intent.data == Video.Media.INTERNAL_CONTENT_URI
    }

    private fun isImageType(intent: Intent): Boolean {
        return (intent.type?.startsWith("image/") == true
                || intent.type == Images.Media.CONTENT_TYPE)
    }

    private fun isVideoType(intent: Intent): Boolean {
        return (intent.type?.startsWith("video/") == true
                || intent.type == Video.Media.CONTENT_TYPE)
    }

    private fun fillExtraOutput(resultData: Intent): Uri? {
        val file = File(resultData.data!!.path!!)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val output = intent.extras!!.get(MediaStore.EXTRA_OUTPUT) as Uri
            inputStream = FileInputStream(file)
            outputStream = contentResolver.openOutputStream(output)
            inputStream.copyTo(outputStream!!)
        } catch (e: SecurityException) {
            showErrorToast(e)
        } catch (ignored: FileNotFoundException) {
            return getFilePublicUri(file, BuildConfig.APPLICATION_ID)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return null
    }

    private fun fillPickedPaths(resultData: Intent, resultIntent: Intent) {
        val paths = resultData.extras!!.getStringArrayList(PICKED_PATHS)
        val uris = paths!!
            .map { getFilePublicUri(File(it), BuildConfig.APPLICATION_ID) } as ArrayList
        val clipData = ClipData(
            "Attachment",
            arrayOf("image/*", "video/*"),
            ClipData.Item(uris.removeAt(0))
        )

        uris.forEach {
            clipData.addItem(ClipData.Item(it))
        }

        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resultIntent.clipData = clipData
    }

    private fun fillIntentPath(resultData: Intent, resultIntent: Intent) {
        val data = resultData.data
        val path = if (data.toString().startsWith("/")) data.toString() else data!!.path
        val uri = getFilePublicUri(File(path!!), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        resultIntent.setDataAndTypeAndNormalize(uri, type)
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun itemClicked(path: String) {
        handleLockedFolderOpening(path) { success ->
            if (success) {
                Intent(this, MediaActivity::class.java).apply {
                    putExtra(SKIP_AUTHENTICATION, true)
                    putExtra(DIRECTORY, path)
                    handleMediaIntent(this)
                }
            }
        }
    }

    private fun handleMediaIntent(intent: Intent) {
        hideKeyboard()
        intent.apply {
            if (mIsSetWallpaperIntent) {
                putExtra(SET_WALLPAPER_INTENT, true)
                startActivityForResult(this, PICK_WALLPAPER)
            } else {
                putExtra(GET_IMAGE_INTENT, mIsPickImageIntent || mIsGetImageContentIntent)
                putExtra(GET_VIDEO_INTENT, mIsPickVideoIntent || mIsGetVideoContentIntent)
                putExtra(GET_ANY_INTENT, mIsGetAnyContentIntent)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mAllowPickingMultiple)
                startActivityForResult(this, PICK_MEDIA)
            }
        }
    }

    private fun gotDirectories(newDirs: ArrayList<Directory>, generation: Long = 0, forceFullScan: Boolean = false, fullVolumeScan: Boolean = false, filesystemScan: Boolean = true) {
        val gotDirectoriesStart = SystemClock.elapsedRealtime()
        logPerf("gotDirectories: started with ${newDirs.size} cached dirs (generation=$generation, forceFullScan=$forceFullScan, fullVolumeScan=$fullVolumeScan, filesystemScan=$filesystemScan)")
        logPerf("gotDirectories: isRPlus=${isRPlus()}, isExternalStorageManager=${isExternalStorageManager()}, SDK=${Build.VERSION.SDK_INT}")

        try {
            // if hidden item showing is disabled but all Favorite items are hidden, hide the Favorites folder
            if (!config.shouldShowHidden) {
                val favoritesFolder = newDirs.firstOrNull { it.areFavorites() }
                if (
                    favoritesFolder != null
                    && favoritesFolder.tmb.getFilenameFromPath().startsWith('.')
                ) {
                    newDirs.remove(favoritesFolder)
                }
            }

            val dirs = getSortedDirectories(newDirs)
            if (config.groupDirectSubfolders) {
                mDirs = dirs.clone() as ArrayList<Directory>
            }

            var isPlaceholderVisible = dirs.isEmpty()

            runOnUiThread {
                logPerf("gotDirectories: posting initial ${dirs.size} dirs to UI")
                checkPlaceholderVisibility(dirs)
                setupAdapter(dirs.clone() as ArrayList<Directory>)
            }

            // cached folders have been loaded, recheck folders one by one starting with the first displayed
            mLastMediaFetcher?.shouldStop = true
            mLastMediaFetcher = MediaFetcher(applicationContext)

            // MediaProvider scan trigger: uses hidden @SystemApi methods accessible via
            // ContentResolver.call(). These are implemented as ContentProvider call() methods
            // with NO permission check, so any app can invoke them directly.
            //
            // Two modes:
            // 1. fullVolumeScan=true (Level 1 menu "Full Media Scan"): calls scanVolume() for each
            //    mounted storage volume. This scans the ENTIRE volume recursively using
            //    ModernMediaScanner (FileVisitor-based tree walk in native code). It's thorough but
            //    slow (minutes for 100k+ files on SD card). Used only for the explicit menu action.
            //
            // 2. fullVolumeScan=false (pull-to-refresh): no scanVolume here. Instead, the filesystem
            //    walk (getNewFoldersViaFilesystem) finds new directories, and targeted scanFile()
            //    calls index only those specific directories. This is fast (seconds, not minutes).
            //    See the folder discovery section below.
            if (forceFullScan && fullVolumeScan && isRPlus() && !mMediaScanInProgress && !mMediaScanCompleted) {
                mMediaScanInProgress = true
                ensureBackgroundThread {
                    val mediaIdBefore = getLatestMediaId()
                    logPerf("gotDirectories: triggering full volume scan, latestMediaId before=$mediaIdBefore")

                    // Collect all storage volume names from StorageManager — this is the reliable way
                    // to get the exact volume names MediaProvider expects (lowercase hex UUIDs for
                    // removable storage, "external_primary" for internal storage).
                    val storageManager = getSystemService(android.content.Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                    val volumeNames = ArrayList<String>()
                    for (sv in storageManager.storageVolumes) {
                        val volName = sv.mediaStoreVolumeName
                        val state = sv.state
                        if (volName != null && (state == android.os.Environment.MEDIA_MOUNTED || state == android.os.Environment.MEDIA_MOUNTED_READ_ONLY)) {
                            volumeNames.add(volName)
                        }
                    }

                    // scanVolume for each mounted volume — scans the entire volume recursively.
                    // Note: scanVolume blocks MediaProvider's database while scanning, so MediaStore
                    // queries from other threads will block until the scan completes.
                    for (volName in volumeNames) {
                        val volStart = SystemClock.elapsedRealtime()
                        logPerf("gotDirectories: scanVolume('$volName')")
                        try {
                            contentResolver.call(MediaStore.AUTHORITY, "scan_volume", volName, null)
                            val volTime = SystemClock.elapsedRealtime() - volStart
                            logPerf("gotDirectories: scanVolume('$volName') returned in ${volTime} ms")
                        } catch (e: Exception) {
                            logPerf("gotDirectories: scanVolume('$volName') FAILED: ${e.message}")
                        }
                    }

                    val mediaIdAfter = getLatestMediaId()
                    logPerf("gotDirectories: full volume scan done, latestMediaId after=$mediaIdAfter (changed=${mediaIdAfter != mediaIdBefore})")

                    mMediaScanInProgress = false
                    // The scan blocked MediaProvider for a long time, causing the current gotDirectories
                    // flow to be interrupted (mShouldStopFetching was set). Trigger a fresh full scan
                    // to query MediaStore (which now has the newly indexed files) and discover new folders.
                    // The mMediaScanCompleted flag prevents the new scan from triggering another scanVolume.
                    mMediaScanCompleted = true
                    runOnUiThread {
                        logPerf("gotDirectories: triggering post-scan refresh")
                        getDirectories(true, fullVolumeScan = false, source = "post-scan refresh")
                    }
                }
            }

            val getImages = mIsPickImageIntent || mIsGetImageContentIntent
            val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent
            val getImagesOnly = getImages && !getVideos
            val getVideosOnly = getVideos && !getImages
            val favoritePaths = getFavoritePaths()
            val hiddenString = getString(R.string.hidden)
            val albumCovers = config.parseAlbumCovers()
            val includedFolders = config.includedFolders
            val noMediaFolders = getNoMediaFoldersSync()
            // Pre-compute a HashSet of lowercase noMedia folder paths for O(1) lookup in the
            // refresh loop. This replaces per-directory File.exists() calls (106s on cold SD
            // card) with a single MediaStore query (~5-151ms) + instant HashSet lookups.
            val noMediaFoldersLower = noMediaFolders.mapTo(HashSet()) {
                it.lowercase(Locale.getDefault())
            }
            val tempFolderPath = config.tempFolderPath
            val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0
            val dirPathsToRemove = ArrayList<String>()
            val hiddenDirPaths = ArrayList<String>()

            // Check for recent media changes first - this determines whether we need a full scan
            var stepStart = SystemClock.elapsedRealtime()
            val foldersWithRecentMedia = mLastMediaFetcher!!.getFoldersWithRecentMedia()
            logPerf("gotDirectories: getFoldersWithRecentMedia() took ${SystemClock.elapsedRealtime() - stepStart} ms, found ${foldersWithRecentMedia.size} folders")

            val onlyVersionChanged = foldersWithRecentMedia.size == 1 &&
                foldersWithRecentMedia.contains(MediaFetcher.MEDIA_STORE_CHANGED_SENTINEL)
            // Real folders with recent media (excluding the MediaStore version-changed sentinel).
            // When non-empty, MediaStore has already indexed new media, so we can skip the
            // expensive filesystem walk and full MediaStore folder query.
            val realFoldersWithRecentMedia = foldersWithRecentMedia.filter {
                it != MediaFetcher.MEDIA_STORE_CHANGED_SENTINEL
            }

            // Early exit only for non-forced scans (initial load) with no MediaStore changes at all.
            //
            // This early exit is intentionally NARROW to avoid a bug where folders copied via adb
            // (bypassing MediaStore) and later scanned by an external scan_file command become
            // permanently invisible in the folder list. The previous condition used
            // `foldersWithRecentMedia.isEmpty() || onlyVersionChanged`, which was too aggressive:
            //
            // 1. onlyVersionChanged early-exit: When MediaStore's version increments (meaning
            //    MediaStore WAS modified) but no files have DATE_ADDED > lastScanTs, the sentinel
            //    was returned and onlyVersionChanged=true caused an early exit. This skipped all
            //    folder discovery AND advanced lastFolderScanTimestamp, making the folder
            //    permanently invisible — future refreshes would never find it because lastScanTs
            //    was now past the files' DATE_ADDED.
            //
            // 2. forceFullScan early-exit: Pull-to-refresh (forceFullScan=true) would early-exit
            //    when getFoldersWithRecentMedia returned empty, skipping the filesystem walk
            //    (getNewFoldersViaFilesystem) which is the reliable way to discover folders that
            //    MediaStore doesn't know about or whose DATE_ADDED is older than lastScanTs.
            //    This made the filesystem walk effectively dead code for pull-to-refresh.
            //
            // The fix: only early-exit for non-forced scans (initial load) with truly empty
            // getFoldersWithRecentMedia (no sentinel, no recent folders). For pull-to-refresh
            // and poll-triggered scans, always proceed to folder discovery. The filesystem walk
            // and MediaStore query will find any new folders. The performance cost is acceptable
            // because pull-to-refresh is user-initiated and the poll only fires when
            // getLatestMediaId changes.
            //
            // Exception: fullVolumeScan=true (menu-based "Full Media Scan" / endboss scan). The user
            // explicitly asked for a thorough scan, so we always continue to the refresh loop and
            // folder discovery even if MediaStore reports no recent changes. The scanVolume() call
            // at the top of gotDirectories may still index files that getFoldersWithRecentMedia
            // hasn't seen yet, and the post-scan refresh will pick those up.
            if (!fullVolumeScan && !forceFullScan && foldersWithRecentMedia.isEmpty()) {
                logPerf("gotDirectories: no media changes detected (non-forced scan), skipping full scan")
                mLoadedInitialPhotos = true
                if (config.appRunCount > 1) {
                    checkLastMediaChanged()
                }
                config.lastFolderScanTimestamp = System.currentTimeMillis() / 1000
                mDirs = dirs.clone() as ArrayList<Directory>
                val totalTime = SystemClock.elapsedRealtime() - gotDirectoriesStart
                logPerf("gotDirectories: finished in ${totalTime} ms (early exit)")
                runOnUiThread {
                    binding.directoriesRefreshLayout.isRefreshing = false
                    checkPlaceholderVisibility(dirs)
                }
                if (generation > 0 && generation == mScanGeneration) {
                    mIsGettingDirs = false
                    mShouldStopFetching = false
                    mDirsLoadStartTime = 0L
                    if (mMediaScanCompleted) {
                        mMediaScanCompleted = false
                    }
                    if (mPendingRestart) {
                        logPerf("gotDirectories: pending restart, starting new scan")
                        mPendingRestart = false
                        val pendingVolScan = mPendingFullVolumeScan
                        val pendingFsScan = mPendingFilesystemScan
                        mPendingFullVolumeScan = false
                        getDirectories(true, fullVolumeScan = pendingVolScan, filesystemScan = pendingFsScan, source = "pending restart (early exit)")
                    }
                }
                return
            }

            val lastModifieds: HashMap<String, Long>
            val dateTakens: HashMap<String, Long>
            val android11Files: HashMap<String, ArrayList<Medium>>?

            if (realFoldersWithRecentMedia.isEmpty()) {
                // No real folders to refresh (only version-only change or fullVolumeScan with
                // no recent media). Skip the heavy lastModifieds/dateTakens queries — they're
                // only used by getFilesFrom() for folders in foldersWithRecentMedia, and if
                // there are none, the queries are wasted (2s+ for 155k rows).
                logPerf("gotDirectories: skipping heavy queries (${if (onlyVersionChanged) "version-only change" else "no real recent media"})")
                lastModifieds = HashMap()
                dateTakens = HashMap()
                android11Files = null
            } else {
                stepStart = SystemClock.elapsedRealtime()
                lastModifieds = mLastMediaFetcher!!.getLastModifieds()
                logPerf("gotDirectories: getLastModifieds() took ${SystemClock.elapsedRealtime() - stepStart} ms, size=${lastModifieds.size}")

                stepStart = SystemClock.elapsedRealtime()
                dateTakens = mLastMediaFetcher!!.getDateTakens()
                logPerf("gotDirectories: getDateTakens() took ${SystemClock.elapsedRealtime() - stepStart} ms, size=${dateTakens.size}")

                if (
                    config.showRecycleBinAtFolders
                    && !config.showRecycleBinLast
                    && !dirs.map { it.path }.contains(RECYCLE_BIN)
                ) {
                    try {
                        if (mediaDB.getDeletedMediaCount() > 0) {
                            val recycleBin = Directory().apply {
                                path = RECYCLE_BIN
                                name = getString(org.fossify.commons.R.string.recycle_bin)
                                location = LOCATION_INTERNAL
                            }

                            dirs.add(0, recycleBin)
                        }
                    } catch (ignored: Exception) {
                    }
                }

                if (dirs.map { it.path }.contains(FAVORITES)) {
                    if (mediaDB.getFavoritesCount() > 0) {
                        val favorites = Directory().apply {
                            path = FAVORITES
                            name = getString(org.fossify.commons.R.string.favorites)
                            location = LOCATION_INTERNAL
                        }

                        dirs.add(0, favorites)
                    }
                }

                // fetch files from MediaStore only, unless the app has the MANAGE_EXTERNAL_STORAGE permission on Android 11+
                stepStart = SystemClock.elapsedRealtime()
                android11Files = mLastMediaFetcher?.getAndroid11FolderMedia(
                    isPickImage = getImagesOnly,
                    isPickVideo = getVideosOnly,
                    favoritePaths = favoritePaths,
                    getFavoritePathsOnly = false,
                    getProperDateTaken = true,
                    dateTakens = dateTakens
                )
                logPerf("gotDirectories: getAndroid11FolderMedia() took ${SystemClock.elapsedRealtime() - stepStart} ms, folders=${android11Files?.size ?: 0}")
            }

            var cachedDirectoriesChanged = false
            var cachedDirsSkipped = 0
            stepStart = SystemClock.elapsedRealtime()
            logPerf("gotDirectories: starting cached directory refresh loop for ${dirs.size} dirs")
            try {
                for (directory in dirs) {
                    if (mShouldStopFetching || isDestroyed || isFinishing) {
                        return
                    }

                    val folderPath = directory.path
                    if (folderPath != FAVORITES && folderPath != RECYCLE_BIN && folderPath != tempFolderPath) {
                        if (folderPath !in foldersWithRecentMedia) {
                            // Check .nomedia status via MediaStore data (noMediaFoldersLower) instead of
                            // File.exists(). This is a single MediaStore query (~5-151ms) + O(1) HashSet
                            // lookups, vs 785 File.exists() calls that took 106s on a cold SD card.
                            //
                            // MediaStore data is fresh on internal storage (inotify watchers) but can
                            // be stale on SD cards (no inotify — only scanFile/scanVolume indexes them).
                            // For the endboss scan (fullVolumeScan=true), scanVolume() already ran and
                            // indexed everything, so we also do File.exists() as a ground-truth check
                            // to catch any .nomedia files that scanVolume might have missed. The SD card
                            // is warm at this point so File.exists() is fast (~2ms per dir).
                            val folderHasNoMedia = if (fullVolumeScan) {
                                File(folderPath, NOMEDIA).exists()
                            } else {
                                folderPath.lowercase(Locale.getDefault()) in noMediaFoldersLower
                            }
                            if (folderHasNoMedia != directory.hasNoMedia) {
                                directory.hasNoMedia = folderHasNoMedia
                                updateDBDirectory(directory)
                                cachedDirectoriesChanged = true
                                if (folderHasNoMedia && !config.shouldShowHidden
                                    && !folderPath.isThisOrParentIncluded(includedFolders)) {
                                    hiddenDirPaths.add(folderPath)
                                }
                            }
                            cachedDirsSkipped++
                            continue
                        }
                    }

                    val folderStart = SystemClock.elapsedRealtime()
                    val sorting = config.getFolderSorting(directory.path)
                    val grouping = config.getFolderGrouping(directory.path)
                    val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                            || sorting and SORT_BY_DATE_TAKEN != 0
                            || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
                            || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

                    val getProperLastModified =
                        config.directorySorting and SORT_BY_DATE_MODIFIED != 0
                                || sorting and SORT_BY_DATE_MODIFIED != 0
                                || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
                                || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

                    val curMedia = mLastMediaFetcher!!.getFilesFrom(
                        curPath = directory.path,
                        isPickImage = getImagesOnly,
                        isPickVideo = getVideosOnly,
                        getProperDateTaken = getProperDateTaken,
                        getProperLastModified = getProperLastModified,
                        getProperFileSize = getProperFileSize,
                        favoritePaths = favoritePaths,
                        getVideoDurations = false,
                        lastModifieds = lastModifieds,
                        dateTakens = dateTakens,
                        android11Files = android11Files
                    )

                    val newDir = if (curMedia.isEmpty()) {
                        if (directory.path != tempFolderPath) {
                            dirPathsToRemove.add(directory.path)
                        }
                        directory
                    } else {
                        createDirectoryFromMedia(
                            path = directory.path,
                            curMedia = curMedia,
                            albumCovers = albumCovers,
                            hiddenString = hiddenString,
                            includedFolders = includedFolders,
                            getProperFileSize = getProperFileSize,
                            noMediaFolders = noMediaFolders
                        )
                    }

                    // we are looping through the already displayed folders looking for changes, do not do anything if nothing changed
                    if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) == newDir) {
                        continue
                    }

                    directory.apply {
                        tmb = newDir.tmb
                        name = newDir.name
                        mediaCnt = newDir.mediaCnt
                        modified = newDir.modified
                        taken = newDir.taken
                        this@apply.size = newDir.size
                        types = newDir.types
                        sortValue = getDirectorySortingValue(curMedia, path, name, size, mediaCnt)
                        hasNoMedia = newDir.hasNoMedia
                    }

                    cachedDirectoriesChanged = true

                    // update directories and media files in the local db, delete invalid items. Intentionally creating a new thread
                    updateDBDirectory(directory)
                    if (!directory.isRecycleBin() && !directory.areFavorites()) {
                        Thread {
                            try {
                                mediaDB.insertAll(curMedia)
                            } catch (ignored: Exception) {
                            }
                        }.start()
                    }

                    if (!directory.isRecycleBin()) {
                        getCachedMedia(directory.path, getVideosOnly, getImagesOnly) {
                            val mediaToDelete = ArrayList<Medium>()
                            it.forEach {
                                if (!curMedia.contains(it)) {
                                    val medium = it as? Medium
                                    val path = medium?.path
                                    if (path != null) {
                                        mediaToDelete.add(medium)
                                    }
                                }
                            }
                            mediaDB.deleteMedia(*mediaToDelete.toTypedArray())
                        }
                    }

                    val folderTime = SystemClock.elapsedRealtime() - folderStart
                    if (folderTime > 100) {
                        logPerf("gotDirectories: refresh folder '${directory.path}' took ${folderTime} ms, media=${curMedia.size}")
                    }
                }

                if (dirPathsToRemove.isNotEmpty()) {
                    val dirsToRemove = dirs.filter { dirPathsToRemove.contains(it.path) }
                    dirsToRemove.forEach {
                        directoryDB.deleteDirPath(it.path)
                    }
                    dirs.removeAll(dirsToRemove)
                    cachedDirectoriesChanged = true
                }

                if (hiddenDirPaths.isNotEmpty()) {
                    val hiddenDirs = dirs.filter { hiddenDirPaths.contains(it.path) }
                    dirs.removeAll(hiddenDirs)
                    cachedDirectoriesChanged = true
                }

                if (cachedDirectoriesChanged) {
                    setupAdapter(dirs)
                }
            } catch (ignored: Exception) {
            }
            logPerf("gotDirectories: cached directory refresh loop took ${SystemClock.elapsedRealtime() - stepStart} ms, skipped $cachedDirsSkipped/${dirs.size} dirs")

            stepStart = SystemClock.elapsedRealtime()
            val knownFolderPaths = directoryDB.getAll().map { it.path }.toSet()
            val mediaStoreFolderInfo = if (forceFullScan || foldersWithRecentMedia.isNotEmpty()) {
                if (isRPlus()) {
                    // For pull-to-refresh (filesystemScan=true, !fullVolumeScan): use filesystem walk
                    // to find new directories, then call scanFile() on each new directory to index
                    // it in MediaStore. This is fast (seconds) compared to scanVolume (minutes).
                    // For Level 0 poll (filesystemScan=false): MediaStore already detected the change,
                    // so skip the filesystem walk and just query MediaStore.
                    // For Level 2 (fullVolumeScan): scanVolume already indexed everything, so we
                    // skip the filesystem walk and just query MediaStore.
                    //
                    // Optimization: when realFoldersWithRecentMedia is non-empty, MediaStore has
                    // already indexed the new media. The filesystem walk is redundant — the MediaStore
                    // query will find any new folders. The filesystem walk is extremely slow on SD
                    // cards (49s observed for 8 listFiles() calls on a slow SD card). Skip it when
                    // MediaStore already knows about the changes.
                    val shouldDoFilesystemWalk = forceFullScan && filesystemScan && !fullVolumeScan &&
                        !mMediaScanCompleted && Environment.isExternalStorageManager() &&
                        realFoldersWithRecentMedia.isEmpty()
                    if (shouldDoFilesystemWalk) {
                        // Step 1: Filesystem walk to find new directories (fast - directory listing only)
                        val fsStart = SystemClock.elapsedRealtime()
                        val fsNewFolders = mLastMediaFetcher!!.getNewFoldersViaFilesystem(knownFolderPaths)
                        logPerf("gotDirectories: getNewFoldersViaFilesystem took ${SystemClock.elapsedRealtime() - fsStart} ms, found ${fsNewFolders.size} FS new folders")

                        // Step 2: Call scanFile() on each new directory to index it in MediaStore.
                        // scanFile with a directory path triggers ModernMediaScanner.Scan which walks
                        // only that directory subtree. This is fast (seconds for a small folder).
                        if (fsNewFolders.isNotEmpty()) {
                            val scanStart = SystemClock.elapsedRealtime()
                            for (folder in fsNewFolders) {
                                val fileStart = SystemClock.elapsedRealtime()
                                try {
                                    contentResolver.call(MediaStore.AUTHORITY, "scan_file", folder, null)
                                    val fileTime = SystemClock.elapsedRealtime() - fileStart
                                    logPerf("gotDirectories: scanFile('$folder') done in ${fileTime} ms")
                                } catch (e: Exception) {
                                    logPerf("gotDirectories: scanFile('$folder') FAILED: ${e.message}")
                                }
                            }
                            logPerf("gotDirectories: targeted scanFile on ${fsNewFolders.size} folders took ${SystemClock.elapsedRealtime() - scanStart} ms")
                        }

                        // Step 3: Query MediaStore (which now has the newly indexed files)
                        val msInfo = mLastMediaFetcher!!.getMediaStoreFolderInfo(
                            knownFolders = knownFolderPaths,
                            collectMedia = true,
                            isPickImage = getImagesOnly,
                            isPickVideo = getVideosOnly,
                            favoritePaths = favoritePaths
                        )
                        // Merge filesystem-discovered folders into the MediaStore results
                        val mergedNewFolders = ArrayList(msInfo.newFolders)
                        val existingLower = msInfo.newFolders.map { it.lowercase(Locale.getDefault()) }.toHashSet()
                        fsNewFolders.forEach { folder ->
                            if (folder.lowercase(Locale.getDefault()) !in existingLower) {
                                mergedNewFolders.add(folder)
                                existingLower.add(folder.lowercase(Locale.getDefault()))
                            }
                        }
                        MediaFetcher.MediaStoreFolderInfo(msInfo.allParentPaths, mergedNewFolders, msInfo.mediaByFolder)
                    } else {
                        // Level 1 (fullVolumeScan), post-scan refresh, Level 0 poll, or
                        // pull-to-refresh with realFoldersWithRecentMedia non-empty: just query
                        // MediaStore. The filesystem walk is skipped because:
                        // - fullVolumeScan: scanVolume already indexed everything
                        // - mMediaScanCompleted: scanVolume already ran (post-scan refresh)
                        // - realFoldersWithRecentMedia non-empty: MediaStore already indexed the
                        //   new media, so the filesystem walk is redundant (and very slow on SD cards)
                        mLastMediaFetcher!!.getMediaStoreFolderInfo(
                            knownFolders = knownFolderPaths,
                            collectMedia = true,
                            isPickImage = getImagesOnly,
                            isPickVideo = getVideosOnly,
                            favoritePaths = favoritePaths
                        )
                    }
                } else {
                    MediaFetcher.MediaStoreFolderInfo(HashSet(), mLastMediaFetcher!!.getNewFoldersViaFilesystem(knownFolderPaths), null)
                }
            } else {
                MediaFetcher.MediaStoreFolderInfo(HashSet(), ArrayList(), null)
            }
            val mediaStoreAllPaths = mediaStoreFolderInfo.allParentPaths
            val newFoldersFromFS = mediaStoreFolderInfo.newFolders
            val mediaByFolder = mediaStoreFolderInfo.mediaByFolder
            logPerf("gotDirectories: new folder discovery took ${SystemClock.elapsedRealtime() - stepStart} ms, found ${newFoldersFromFS.size} new folders")

            stepStart = SystemClock.elapsedRealtime()
            val foldersToScan = ArrayList<String>()
            foldersToScan.addAll(newFoldersFromFS)
            foldersWithRecentMedia.forEach { folder ->
                if (folder != MediaFetcher.MEDIA_STORE_CHANGED_SENTINEL && folder !in knownFolderPaths && folder !in foldersToScan) {
                    foldersToScan.add(folder)
                }
            }
            mLastMediaFetcher!!.getLatestFileFolders().forEach { folder ->
                if (folder !in knownFolderPaths && folder !in foldersToScan) {
                    foldersToScan.add(folder)
                }
            }
            logPerf("gotDirectories: folder discovery (FS + recent + latest) took ${SystemClock.elapsedRealtime() - stepStart} ms, found ${foldersToScan.size} new folders")

            // Remove invalid directories BEFORE the new folder loop to avoid transient state
            // where both old (pre-rename) and new folders exist simultaneously, causing position shifts
            stepStart = SystemClock.elapsedRealtime()
            if (forceFullScan || foldersWithRecentMedia.isNotEmpty() || newFoldersFromFS.isNotEmpty()) {
                checkInvalidDirectories(dirs, if (isRPlus()) mediaStoreAllPaths else null)
            } else {
                logPerf("gotDirectories: skipping checkInvalidDirectories() - no media changes detected")
            }
            logPerf("gotDirectories: checkInvalidDirectories() took ${SystemClock.elapsedRealtime() - stepStart} ms")

            foldersToScan.remove(FAVORITES)
            foldersToScan.add(0, FAVORITES)
            if (config.showRecycleBinAtFolders) {
                if (foldersToScan.contains(RECYCLE_BIN)) {
                    foldersToScan.remove(RECYCLE_BIN)
                    foldersToScan.add(0, RECYCLE_BIN)
                } else {
                    foldersToScan.add(0, RECYCLE_BIN)
                }
            } else {
                foldersToScan.remove(RECYCLE_BIN)
            }

            dirs.filterNot { it.path == RECYCLE_BIN || it.path == FAVORITES }.forEach {
                foldersToScan.remove(it.path)
            }

            // Final safety filter: remove hidden/excluded folders from foldersToScan
            // This catches folders from getLatestFileFolders() and foldersWithRecentMedia
            // that weren't filtered through shouldFolderBeVisible
            val shouldShowHidden = config.shouldShowHidden
            if (!shouldShowHidden) {
                val excludedPaths = if (config.temporarilyShowExcluded) HashSet() else config.excludedFolders
                val includedPaths = config.includedFolders
                val folderNoMediaStatuses = HashMap<String, Boolean>()
                noMediaFolders.forEach { folder ->
                    folderNoMediaStatuses["$folder/$NOMEDIA"] = true
                }
                val beforeFilter = foldersToScan.size
                foldersToScan.retainAll { folder ->
                    folder == FAVORITES || folder == RECYCLE_BIN ||
                    folder.shouldFolderBeVisible(excludedPaths, includedPaths, shouldShowHidden, folderNoMediaStatuses, skipFileCheck = true) { path, hasNoMedia ->
                        folderNoMediaStatuses[path] = hasNoMedia
                    }
                }
                if (beforeFilter != foldersToScan.size) {
                    logPerf("gotDirectories: shouldFolderBeVisible filtered foldersToScan $beforeFilter -> ${foldersToScan.size}")
                }
            }

            // check the remaining folders which were not cached at all yet
            var newDirectoriesAdded = false
            var newDirsSinceAdapterUpdate = 0
            stepStart = SystemClock.elapsedRealtime()
            logPerf("gotDirectories: starting new folder discovery loop for ${foldersToScan.size} folders")
            for (folder in foldersToScan) {
                if (mShouldStopFetching || isDestroyed || isFinishing) {
                    return
                }

                val folderStart = SystemClock.elapsedRealtime()
                val sorting = config.getFolderSorting(folder)
                val grouping = config.getFolderGrouping(folder)
                val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                        || sorting and SORT_BY_DATE_TAKEN != 0
                        || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
                        || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

                val getProperLastModified = config.directorySorting and SORT_BY_DATE_MODIFIED != 0
                        || sorting and SORT_BY_DATE_MODIFIED != 0
                        || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
                        || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

                // Use pre-computed media from getMediaStoreFolderInfo when available (Android 11+).
                // This avoids per-folder getFilesFrom() calls that do FUSE listFiles() or per-folder MediaStore queries.
                //
                // Fallback: if mediaByFolder has no media for this folder (e.g., the folder was
                // discovered by the filesystem walk but MediaStore hasn't indexed its files yet,
                // or scanFile failed), fall back to getFilesFrom which reads files directly from
                // the filesystem via getMediaInFolder. This handles folders copied via adb without
                // a MediaStore scan — the filesystem walk finds them, and this fallback reads
                // their media without requiring MediaStore indexing.
                val newMedia = if (mediaByFolder != null) {
                    val folderLower = folder.lowercase(Locale.getDefault())
                    val media = mediaByFolder[folderLower]
                    if (media != null && media.isNotEmpty()) {
                        mLastMediaFetcher!!.sortMedia(media, sorting)
                        media
                    } else {
                        mLastMediaFetcher!!.getFilesFrom(
                            curPath = folder,
                            isPickImage = getImagesOnly,
                            isPickVideo = getVideosOnly,
                            getProperDateTaken = getProperDateTaken,
                            getProperLastModified = getProperLastModified,
                            getProperFileSize = getProperFileSize,
                            favoritePaths = favoritePaths,
                            getVideoDurations = false,
                            lastModifieds = lastModifieds,
                            dateTakens = dateTakens,
                            android11Files = android11Files
                        )
                    }
                } else {
                    mLastMediaFetcher!!.getFilesFrom(
                        curPath = folder,
                        isPickImage = getImagesOnly,
                        isPickVideo = getVideosOnly,
                        getProperDateTaken = getProperDateTaken,
                        getProperLastModified = getProperLastModified,
                        getProperFileSize = getProperFileSize,
                        favoritePaths = favoritePaths,
                        getVideoDurations = false,
                        lastModifieds = lastModifieds,
                        dateTakens = dateTakens,
                        android11Files = android11Files
                    )
                }

                if (newMedia.isEmpty()) {
                    logPerf("gotDirectories: new folder '$folder' has 0 media, skipping")
                    continue
                }

                logPerf("gotDirectories: new folder '$folder' has ${newMedia.size} media")

                if (isPlaceholderVisible) {
                    isPlaceholderVisible = false
                    runOnUiThread {
                        binding.directoriesEmptyPlaceholder.beGone()
                        binding.directoriesEmptyPlaceholder2.beGone()
                        binding.directoriesFastscroller.beVisible()
                    }
                }

                val newDir = createDirectoryFromMedia(
                    path = folder,
                    curMedia = newMedia,
                    albumCovers = albumCovers,
                    hiddenString = hiddenString,
                    includedFolders = includedFolders,
                    getProperFileSize = getProperFileSize,
                    noMediaFolders = noMediaFolders
                )

                // When hidden is off, skip folders that checkAppendingHidden detected as having .nomedia
                if (!config.shouldShowHidden && newDir.hasNoMedia
                    && !folder.isThisOrParentIncluded(includedFolders)) {
                    logPerf("gotDirectories: skipping hidden folder '$folder' (hasNoMedia from DB)")
                    continue
                }

                dirs.add(newDir)
                newDirectoriesAdded = true
                newDirsSinceAdapterUpdate++

                if (newDirsSinceAdapterUpdate >= 10) {
                    setupAdapter(dirs)
                    newDirsSinceAdapterUpdate = 0
                }

                // make sure to create a new thread for these operations, dont just use the common bg thread
                Thread {
                    try {
                        directoryDB.insert(newDir)
                        if (folder != RECYCLE_BIN && folder != FAVORITES) {
                            mediaDB.insertAll(newMedia)
                        }
                    } catch (ignored: Exception) {
                    }
                }.start()

                val folderTime = SystemClock.elapsedRealtime() - folderStart
                if (folderTime > 100) {
                    logPerf("gotDirectories: scan new folder '${folder}' took ${folderTime} ms, media=${newMedia.size}")
                }
            }

            if (newDirectoriesAdded) {
                setupAdapter(dirs)
            }
            logPerf("gotDirectories: new folder discovery loop took ${SystemClock.elapsedRealtime() - stepStart} ms")

            mLoadedInitialPhotos = true
            if (config.appRunCount > 1) {
                checkLastMediaChanged()
            }

            if (mDirs.size > 50) {
                excludeSpamFolders()
            }

            val excludedFolders = config.excludedFolders
            val everShownFolders = config.everShownFolders.toMutableSet() as HashSet<String>

            // do not add excluded folders and their subfolders at everShownFolders
            dirs.filter { dir ->
                return@filter !excludedFolders.any { dir.path.startsWith(it) }
            }.mapTo(everShownFolders) { it.path }

            try {
                config.everShownFolders = everShownFolders
            } catch (e: Exception) {
                config.everShownFolders = HashSet()
            }

            config.lastFolderScanTimestamp = System.currentTimeMillis() / 1000

            mDirs = dirs.clone() as ArrayList<Directory>
            val totalTime = SystemClock.elapsedRealtime() - gotDirectoriesStart
            if (totalTime > 10000) {
                logPerf("SLOW START: gotDirectories finished in ${totalTime} ms")
            } else {
                logPerf("gotDirectories: finished in ${totalTime} ms")
            }

            runOnUiThread {
                binding.directoriesRefreshLayout.isRefreshing = false
                checkPlaceholderVisibility(dirs)
            }
        } finally {
            if (generation > 0 && generation == mScanGeneration) {
                mIsGettingDirs = false
                mShouldStopFetching = false
                mDirsLoadStartTime = 0L
                // Reset the post-scan flag so future pull-to-refresh can trigger a new scan
                if (mMediaScanCompleted) {
                    mMediaScanCompleted = false
                }
                if (mPendingRestart) {
                    logPerf("gotDirectories: pending restart, starting new scan")
                    mPendingRestart = false
                    val pendingVolScan = mPendingFullVolumeScan
                    val pendingFsScan = mPendingFilesystemScan
                    mPendingFullVolumeScan = false
                    getDirectories(true, fullVolumeScan = pendingVolScan, filesystemScan = pendingFsScan, source = "pending restart (finally)")
                }
            }
        }
    }

    private fun setAsDefaultFolder() {
        config.defaultFolder = ""
        refreshMenuItems()
    }

    private fun openDefaultFolder() {
        if (config.defaultFolder.isEmpty()) {
            return
        }

        val defaultDir = File(config.defaultFolder)

        if ((!defaultDir.exists() || !defaultDir.isDirectory) && (config.defaultFolder != RECYCLE_BIN && config.defaultFolder != FAVORITES)) {
            config.defaultFolder = ""
            return
        }

        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, config.defaultFolder)
            handleMediaIntent(this)
        }
    }

    private fun checkPlaceholderVisibility(dirs: ArrayList<Directory>) {
        logPerf("checkPlaceholderVisibility: dirs=${dirs.size}, mLoadedInitialPhotos=$mLoadedInitialPhotos")
        binding.directoriesEmptyPlaceholder.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)
        binding.directoriesEmptyPlaceholder2.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)

        if (binding.mainMenu.isSearchOpen) {
            binding.directoriesEmptyPlaceholder.text =
                getString(org.fossify.commons.R.string.no_items_found)
            binding.directoriesEmptyPlaceholder2.beGone()
        } else if (dirs.isEmpty() && config.filterMedia == getDefaultFileFilter()) {
            if (isRPlus() && !isExternalStorageManager()) {
                binding.directoriesEmptyPlaceholder.text =
                    getString(org.fossify.commons.R.string.no_items_found)
                binding.directoriesEmptyPlaceholder2.beGone()
            } else {
                binding.directoriesEmptyPlaceholder.text = getString(R.string.no_media_add_included)
                binding.directoriesEmptyPlaceholder2.text = getString(R.string.add_folder)
            }

            binding.directoriesEmptyPlaceholder2.setOnClickListener {
                showAddIncludedFolderDialog {
                    refreshItems()
                }
            }
        } else {
            binding.directoriesEmptyPlaceholder.text = getString(R.string.no_media_with_filters)
            binding.directoriesEmptyPlaceholder2.text =
                getString(R.string.change_filters_underlined)

            binding.directoriesEmptyPlaceholder2.setOnClickListener {
                showFilterMediaDialog()
            }
        }

        binding.directoriesEmptyPlaceholder2.underlineText()
        binding.directoriesFastscroller.beVisibleIf(binding.directoriesEmptyPlaceholder.isGone())
    }

    private fun setupAdapter(
        dirs: ArrayList<Directory>,
        textToSearch: String = binding.mainMenu.getCurrentQuery(),
        forceRecreate: Boolean = false
    ) {
        val setupStart = SystemClock.elapsedRealtime()
        logPerf("setupAdapter: entered with ${dirs.size} dirs, forceRecreate=$forceRecreate, hasAdapter=${binding.directoriesGrid.adapter != null}")
        val currAdapter = binding.directoriesGrid.adapter
        val distinctDirs = dirs
            .distinctBy { it.path.getDistinctPath() }
            .toMutableList() as ArrayList<Directory>

        val sortedDirs = getSortedDirectories(distinctDirs)
        var dirsToShow = getDirsToShow(
            dirs = sortedDirs,
            allDirs = mDirs,
            currentPathPrefix = mCurrentPathPrefix
        ).clone() as ArrayList<Directory>

        if (currAdapter == null || forceRecreate) {
            mDirsIgnoringSearch = dirs
            initZoomListener()
            DirectoryAdapter(
                this,
                dirsToShow,
                this,
                binding.directoriesGrid,
                isPickIntent(intent) || isGetAnyContentIntent(intent),
                binding.directoriesRefreshLayout
            ) {
                val clickedDir = it as Directory
                val path = clickedDir.path
                if (clickedDir.subfoldersCount == 1 || !config.groupDirectSubfolders) {
                    if (path != config.tempFolderPath) {
                        itemClicked(path)
                    }
                } else {
                    mCurrentPathPrefix = path
                    mOpenedSubfolders.add(path)
                    setupAdapter(mDirs, "")
                }
            }.apply {
                setupZoomListener(mZoomListener)
                runOnUiThread {
                    binding.directoriesGrid.adapter = this
                    setupScrollDirection()

                    if (config.viewTypeFolders == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                        binding.directoriesGrid.scheduleLayoutAnimation()
                    }
                }
            }
        } else {
            runOnUiThread {
                if (textToSearch.isNotEmpty()) {
                    dirsToShow = dirsToShow
                        .filter { it.name.contains(textToSearch, true) }
                        .sortedBy { !it.name.startsWith(textToSearch, true) }
                        .toMutableList() as ArrayList
                }
                checkPlaceholderVisibility(dirsToShow)

                (binding.directoriesGrid.adapter as? DirectoryAdapter)?.updateDirs(dirsToShow)
            }
        }

        // recyclerview sometimes becomes empty at init/update, triggering an invisible refresh like this seems to work fine
        binding.directoriesGrid.postDelayed({
            binding.directoriesGrid.scrollBy(0, 0)
        }, 500)

        logPerf("setupAdapter: finished in ${SystemClock.elapsedRealtime() - setupStart} ms")
    }

    private fun setupScrollDirection() {
        val scrollHorizontally =
            config.scrollHorizontally && config.viewTypeFolders == VIEW_TYPE_GRID
        binding.directoriesFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun checkInvalidDirectories(dirs: ArrayList<Directory>, mediaStoreFolders: HashSet<String>? = null) {
        val invalidDirs = ArrayList<Directory>()
        val OTGPath = config.OTGPath
        val checkStart = SystemClock.elapsedRealtime()

        // mediaStoreFolders is pre-computed by gotDirectories on Android 11+ to avoid a second query
        val mediaStoreFoldersResolved = if (isRPlus() && mediaStoreFolders == null) {
            mLastMediaFetcher!!.getMediaStoreFolderInfo(emptySet()).allParentPaths
        } else {
            mediaStoreFolders
        }
        logPerf("checkInvalidDirectories: mediaStoreFolders has ${mediaStoreFoldersResolved?.size ?: 0} folders (took ${SystemClock.elapsedRealtime() - checkStart} ms)")

        // Safety: if MediaStore query failed/returned empty or returned suspiciously few folders,
        // skip invalidation to avoid false positives (e.g. partial cursor results from shouldStop)
        val mediaStoreFolderCount = mediaStoreFoldersResolved?.size ?: 0
        val skipMediaStoreCheck = mediaStoreFoldersResolved != null &&
            (mediaStoreFolderCount == 0 || (isRPlus() && dirs.size > 10 && mediaStoreFolderCount < dirs.size / 2))
        if (skipMediaStoreCheck) {
            logPerf("checkInvalidDirectories: MediaStore query returned $mediaStoreFolderCount folders (dirs=${dirs.size}), skipping invalidation to avoid false positives")
        }

        val includedFolders = config.includedFolders
        dirs.filter { !it.areFavorites() && !it.isRecycleBin() }.forEach {
            if (isRPlus()) {
                if (skipMediaStoreCheck) return@forEach
                // On Android 11+, avoid all FUSE calls.
                // Use MediaStore batch query to check if folder still has media.
                // Skip included folders — they may not be in MediaStore.
                if (it.path != config.tempFolderPath &&
                    it.path !in includedFolders &&
                    it.path.lowercase(Locale.getDefault()) !in mediaStoreFoldersResolved!!
                ) {
                    invalidDirs.add(it)
                }
            } else {
                if (!getDoesFilePathExist(it.path, OTGPath)) {
                    invalidDirs.add(it)
                } else if (it.path != config.tempFolderPath) {
                    val children = if (isPathOnOTG(it.path)) {
                        getOTGFolderChildrenNames(it.path)
                    } else {
                        File(it.path).list()?.asList()
                    }

                    val hasMediaFile = children?.any {
                        it != null && (
                                it.isMediaFile()
                                        || (it.startsWith("img_", true)
                                        && File(it).isDirectory)
                                )
                    } == true

                    if (!hasMediaFile) {
                        invalidDirs.add(it)
                    }
                }
            }
        }

        if (getFavoritePaths().isEmpty()) {
            val favoritesFolder = dirs.firstOrNull { it.areFavorites() }
            if (favoritesFolder != null) {
                invalidDirs.add(favoritesFolder)
            }
        }

        if (config.useRecycleBin) {
            try {
                val binFolder = dirs.firstOrNull { it.path == RECYCLE_BIN }
                if (binFolder != null && mediaDB.getDeletedMedia().isEmpty()) {
                    invalidDirs.add(binFolder)
                }
            } catch (ignored: Exception) {
            }
        }

        if (invalidDirs.isNotEmpty()) {
            dirs.removeAll(invalidDirs)
            setupAdapter(dirs)
            invalidDirs.forEach {
                try {
                    directoryDB.deleteDirPath(it.path)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun getCurrentlyDisplayedDirs() = getRecyclerAdapter()?.dirs ?: ArrayList()

    private fun setupLatestMediaId() {
        ensureBackgroundThread {
            if (hasPermission(PERMISSION_READ_STORAGE)) {
                mLatestMediaId = getLatestMediaId()
                mLatestMediaDateId = getLatestMediaByDateId()
            }
        }
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed) {
            return
        }

        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getDirectories(true, filesystemScan = false, source = "checkLastMediaChanged poll")
                    }
                } else {
                    mLastMediaHandler.removeCallbacksAndMessages(null)
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun checkRecycleBinItems() {
        if (config.useRecycleBin && config.lastBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000) {
            config.lastBinCheck = System.currentTimeMillis()
            Handler().postDelayed({
                ensureBackgroundThread {
                    try {
                        val filesToDelete = mediaDB.getOldRecycleBinItems(
                            System.currentTimeMillis() - MONTH_MILLISECONDS
                        )
                        filesToDelete.forEach {
                            if (File(it.path.replaceFirst(RECYCLE_BIN, recycleBinPath)).delete()) {
                                mediaDB.deleteMediumPath(it.path)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }, 3000L)
        }
    }

    // exclude probably unwanted folders, for example facebook stickers are split between hundreds of separate folders like
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/175139712676531/209575122566323
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/497837993632037/499671223448714
    private fun excludeSpamFolders() {
        ensureBackgroundThread {
            try {
                val internalPath = internalStoragePath
                val checkedPaths = ArrayList<String>()
                val oftenRepeatedPaths = ArrayList<String>()
                val paths = mDirs
                    .map { it.path.removePrefix(internalPath) }
                    .toMutableList() as ArrayList<String>
                paths.forEach {
                    val parts = it.split("/")
                    var currentString = ""
                    for (i in 0 until parts.size) {
                        currentString += "${parts[i]}/"

                        if (!checkedPaths.contains(currentString)) {
                            val cnt = paths.count { it.startsWith(currentString) }
                            if (cnt > 50 && currentString.startsWith("/Android/data", true)) {
                                oftenRepeatedPaths.add(currentString)
                            }
                        }

                        checkedPaths.add(currentString)
                    }
                }

                val substringToRemove = oftenRepeatedPaths.filter {
                    val path = it
                    it == "/" || oftenRepeatedPaths.any { it != path && it.startsWith(path) }
                }

                oftenRepeatedPaths.removeAll(substringToRemove)
                val OTGPath = config.OTGPath
                oftenRepeatedPaths.forEach {
                    val file = File("$internalPath/$it")
                    if (getDoesFilePathExist(file.absolutePath, OTGPath)) {
                        config.addExcludedFolder(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    override fun refreshItems() {
        getDirectories(true, source = "refreshItems")
    }

    override fun recheckPinnedFolders() {
        ensureBackgroundThread {
            gotDirectories(movePinnedDirectoriesToFront(getCurrentlyDisplayedDirs()))
        }
    }

    override fun updateDirectories(directories: ArrayList<Directory>) {
        ensureBackgroundThread {
            storeDirectoryItems(directories)
            removeInvalidDBDirectories()
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}

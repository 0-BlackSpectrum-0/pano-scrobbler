package com.arn.scrobble.pref

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.service.media.MediaBrowserService
import com.arn.scrobble.BuildKonfig
import com.arn.scrobble.utils.AndroidStuff
import com.arn.scrobble.utils.Stuff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


actual suspend fun AppListVM.load(
    packagesOverride: Set<String>?,
    useCache: Boolean,
    forceRefresh: Boolean,
    onSetAppList: (AppList) -> Unit,
    onSetHostnames: (List<String>) -> Unit,
    onSetBlockedHostnames: (Set<String>) -> Unit,
    onSetHasLoaded: () -> Unit,
) {
    val packageManager = AndroidStuff.applicationContext.packageManager

    val packagesToNotConsider = setOf(
        BuildKonfig.APP_ID,
        "com.android.bluetooth",
        "com.google.android.bluetooth"
    )

    fun Collection<ApplicationInfo>.removeSpam() = filter {
        it.icon != 0 && it.enabled && it.packageName !in packagesToNotConsider
    }

    fun Collection<ApplicationInfo>.sortAndTransform(): List<AppItem> {
        val (selectedList, unselectedList) = this
            .map { AppItem(it.packageName, packageManager.getApplicationLabel(it).toString()) }
            .sortedWith { a, b ->
                a.friendlyLabel.compareTo(b.label, true)
            }
            .partition { it.appId in selectedPackages.value }
        return selectedList + unselectedList
    }

    if (packagesOverride != null) {
        val musicPlayers = mutableMapOf<String, ApplicationInfo>()

        packagesOverride.forEach {
            val appInfo = try {
                packageManager.getApplicationInfo(it, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            if (appInfo != null)
                musicPlayers[it] = appInfo
        }

        onSetAppList(
            AppList(
                musicPlayers = musicPlayers.values.sortAndTransform(),
            )
        )

        onSetHasLoaded()

        return
    }

    val cacheFile = File(AndroidStuff.applicationContext.cacheDir, "app_list_cache.json")
    if (useCache && !forceRefresh && cacheFile.exists()) {
        try {
            val cached = Stuff.myJson.decodeFromString<AppList>(cacheFile.readText())
            val (selectedMusic, unselectedMusic) = cached.musicPlayers.partition { it.appId in selectedPackages.value }
            val (selectedOther, unselectedOther) = cached.otherApps.partition { it.appId in selectedPackages.value }
            onSetAppList(
                AppList(
                    musicPlayers = selectedMusic + unselectedMusic,
                    otherApps = selectedOther + unselectedOther
                )
            )
            onSetHasLoaded()
            return
        } catch (e: Exception) {
            // fallback
        }
    }


    withContext(Dispatchers.IO) {
        val musicPlayers = mutableMapOf<String, ApplicationInfo>()
        val otherApps = mutableMapOf<String, ApplicationInfo>()

        // this matches music players including shazam
        var intent = Intent(MediaBrowserService.SERVICE_INTERFACE)

        musicPlayers += packageManager.queryIntentServices(
            intent,
            PackageManager.GET_RESOLVED_FILTER
        ).map { it.serviceInfo.applicationInfo }
            .removeSpam()
            .map { it.packageName to it }

        // this matches the chromecast receiver on pixel tablets and tv
        intent = Intent("com.google.cast.action.BIND").addCategory(Intent.CATEGORY_DEFAULT)

        musicPlayers += packageManager.queryIntentServices(
            intent,
            PackageManager.GET_RESOLVED_FILTER
        ).map { it.serviceInfo.packageName to it.serviceInfo.applicationInfo }

        // this matches pixel now playing including ambient music mod
        intent =
            Intent("com.google.intelligence.sense.NOW_PLAYING_HISTORY").addCategory(Intent.CATEGORY_DEFAULT)

        musicPlayers += packageManager.queryIntentActivities(
            intent,
            PackageManager.GET_RESOLVED_FILTER
        ).map { it.activityInfo.packageName to it.activityInfo.applicationInfo }

        // apps on phone
        intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        otherApps +=
            packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
                .map { it.activityInfo.applicationInfo }
                .removeSpam()
                .map { it.packageName to it }

        // apps on tv
        intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        otherApps += packageManager.queryIntentActivities(
            intent,
            PackageManager.GET_RESOLVED_FILTER
        )
            .map { it.activityInfo.applicationInfo }
            .removeSpam()
            .map { it.packageName to it }

        // remove music players from other apps
        musicPlayers.forEach { (key, _) -> otherApps.remove(key) }

        val resultAppList = AppList(
            musicPlayers = musicPlayers.values.sortAndTransform(),
            otherApps = otherApps.values.sortAndTransform()
        )

        if (useCache || forceRefresh) {
            try {
                cacheFile.writeText(Stuff.myJson.encodeToString(resultAppList))
            } catch (e: Exception) {
                // ignore
            }
        }

        onSetAppList(resultAppList)
        onSetHasLoaded()
    }
}

actual val AppListVM.pluginsNeeded: List<Pair<String, String>>
    get() = emptyList()
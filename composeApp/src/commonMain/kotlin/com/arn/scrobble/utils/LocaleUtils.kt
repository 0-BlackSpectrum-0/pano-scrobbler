package com.arn.scrobble.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.io.File


object LocaleUtils {

    // localesSet start
    val localesMap = mapOf(
        "en" to "English",
        "hi" to "हिन्दी",
        "ja" to "日本語"
    )
    // localesSet end

    private val localeFile = File(PlatformStuff.filesDir, "locale.txt")
    val setLocaleFlow = MutableSharedFlow<String?>(extraBufferCapacity = 2)
    val locale = setLocaleFlow
        .distinctUntilChanged()
        .let {
            if (PlatformStuff.hasSystemLocaleStore)
                it.onStart {
                    emit(getCurrentLocale())
                }
            else
                it.onEach {
                    localeFile.writeText(it ?: "")
                }
        }
        .stateIn(
            Stuff.appScope,
            SharingStarted.Eagerly,
            if (!PlatformStuff.hasSystemLocaleStore && localeFile.exists())
                localeFile.readText().ifEmpty { null }
            else
                null
        )
}

expect fun LocaleUtils.setAppLocale(lang: String?, activityContext: Any?)

expect fun LocaleUtils.getCurrentLocale(): String?

expect fun LocaleUtils.getSystemCountryCode(): String
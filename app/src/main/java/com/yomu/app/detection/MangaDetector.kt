package com.yomu.app.detection

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val knownMangaSites = listOf(
        "manga", "mangadex", "mangakakalot", "manganelo",
        "mangafox", "mangareader", "mangapanda", "mangaplus",
        "comick", "bato.to", "mangahere", "mangasee",
        "readmanga", "chapter", "raw", "scanlator"
    )

    fun isPossibleMangaPage(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return knownMangaSites.any { lowerUrl.contains(it) }
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getKnownMangaSites(): List<String> = knownMangaSites.toList()
}

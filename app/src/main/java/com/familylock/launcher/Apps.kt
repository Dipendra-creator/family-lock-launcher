package com.familylock.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One launchable app, ready to draw. */
data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap)

object Apps {
    /** Every installed app that has a launcher icon (except this app), sorted by name. */
    suspend fun loadLaunchable(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppEntry(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

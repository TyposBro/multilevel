package com.typosbro.multilevel.utils

import android.content.Context
import android.content.pm.PackageManager

/**
 * Gets the version name of the app (e.g., "1.0.0") from the package manager.
 */
fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        "N/A" // Should not happen
    }.toString()
}
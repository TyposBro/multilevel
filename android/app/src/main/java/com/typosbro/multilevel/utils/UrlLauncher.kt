package com.typosbro.multilevel.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * A helper to launch a URL in a Chrome Custom Tab for a better in-app experience.
 * Falls back to a standard browser intent if an error occurs.
 */
fun openUrlInCustomTab(context: Context, url: String) {
    try {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        // Fallback to opening in the default browser if Custom Tabs fails
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
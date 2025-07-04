package com.obsidiancalendarhelper

import android.content.Context
import android.net.Uri

object AppPreferences {
    private const val PREFS_NAME = "obsidian_calendar_prefs"
    private const val KEY_SELECTED_DIRECTORY_URI = "selected_directory_uri"

    fun saveSelectedDirectoryUri(context: Context, uri: Uri?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_DIRECTORY_URI, uri?.toString()).apply()
    }

    fun getSelectedDirectoryUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_SELECTED_DIRECTORY_URI, null)
        return uriString?.let { Uri.parse(it) }
    }
}
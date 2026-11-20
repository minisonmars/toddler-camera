package com.toddlercamera

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

/** Reads pictures and videos from the device, newest first, following the gallery settings. */
object Media {
    class Item(val uri: Uri, val isVideo: Boolean)

    /** [limit] of 0 means everything. */
    fun load(ctx: Context, settings: Settings, limit: Int = 0): List<Item> {
        val list = mutableListOf<Item>()
        val id = MediaStore.Files.FileColumns._ID
        val type = MediaStore.Files.FileColumns.MEDIA_TYPE
        val image = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        val video = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

        var selection = when (settings.galleryTypes) {
            "photos" -> "$type=$image"
            "videos" -> "$type=$video"
            else -> "($type=$image OR $type=$video)"
        }
        val selArgs = mutableListOf<String>()
        if (settings.galleryScope == "app") {
            val rel = MediaStore.MediaColumns.RELATIVE_PATH
            selection += " AND ($rel LIKE ? OR $rel LIKE ?)"
            selArgs.add("Pictures/ToddlerCamera%")
            selArgs.add("Movies/ToddlerCamera%")
        }

        val query = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            if (selArgs.isNotEmpty()) {
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selArgs.toTypedArray())
            }
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")
            if (limit > 0) putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }

        ctx.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(id, type),
            query,
            null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(id)
            val typeCol = c.getColumnIndexOrThrow(type)
            while (c.moveToNext()) {
                val isVideo = c.getInt(typeCol) == video
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                list.add(Item(ContentUris.withAppendedId(base, c.getLong(idCol)), isVideo))
            }
        }
        return list
    }
}

package com.toddlercamera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Reads pictures and videos from the device, newest first, following the gallery settings. */
object Media {
    class Item(val uri: Uri, val isVideo: Boolean)

    fun load(ctx: Context, settings: Settings): List<Item> {
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
        val args = mutableListOf<String>()
        if (settings.galleryScope == "app") {
            val rel = MediaStore.MediaColumns.RELATIVE_PATH
            selection += " AND ($rel LIKE ? OR $rel LIKE ?)"
            args.add("Pictures/ToddlerCamera%")
            args.add("Movies/ToddlerCamera%")
        }

        ctx.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(id, type),
            selection,
            if (args.isEmpty()) null else args.toTypedArray(),
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
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

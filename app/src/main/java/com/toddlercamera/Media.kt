package com.toddlercamera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Reads every picture and video on the device, newest first. */
object Media {
    class Item(val uri: Uri, val isVideo: Boolean)

    fun load(ctx: Context): List<Item> {
        val list = mutableListOf<Item>()
        val id = MediaStore.Files.FileColumns._ID
        val type = MediaStore.Files.FileColumns.MEDIA_TYPE
        val image = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        val video = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

        ctx.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(id, type),
            "$type=? OR $type=?",
            arrayOf(image.toString(), video.toString()),
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

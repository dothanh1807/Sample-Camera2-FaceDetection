package com.vllenin.icamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.FileOutputStream

/**
 * Created by Vllenin on 2020-04-02.
 */
object FileUtils {

  private const val NAME_MEDIA_FOLDER = "Vllenins"
  private const val MIME_TYPE_JPEG = "image/jpeg"

  @Throws
  fun saveImageJPEGIntoFolderMedia(context: Context, bitmap: Bitmap, nameImage: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, nameImage)
        put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE_JPEG)
        put(MediaStore.MediaColumns.RELATIVE_PATH, getPathFolderMedia())
      }
      val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw Exception("insert to MediaStore failed: FileUtils.saveImageJPEGIntoFolderMedia(...)")
      context.contentResolver.openOutputStream(uri).use { outputStream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream?.close()
      }
    } else {
      FileOutputStream("${getPathFolderMedia()}/$nameImage.jpeg").use { fileOutputStream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
        fileOutputStream.close()
      }
    }

  }

  fun getPathFolderMedia(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      "${Environment.DIRECTORY_DCIM}/$NAME_MEDIA_FOLDER"
    } else {
      "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/$NAME_MEDIA_FOLDER"
    }
  }
}
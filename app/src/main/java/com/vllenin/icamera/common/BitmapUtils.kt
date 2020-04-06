package com.vllenin.icamera.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Size

/**
 * Created by Vllenin on 2020-03-28.
 */
object BitmapUtils {

  /**
   * Rotate and center crop bitmap matched with camera preview
   */
  fun configureBitmap(byteArray: ByteArray, previewSize: Size, cameraId: String): Bitmap {
    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    val ratioPreview = previewSize.height.toFloat() / previewSize.width

    val widthMatchedWithRatioPreView = bitmap.height * ratioPreview
    val coordinateX = bitmap.width.toFloat() / 2 - widthMatchedWithRatioPreView / 2
    val matrix = Matrix()
    if (cameraId.contains("0")) {
      matrix.postRotate(90f)
    } else {
      matrix.postRotate(90f)
      // flip bitmap
      matrix.preScale(-1.0f, 1.0f)
    }

    return Bitmap.createBitmap(bitmap, coordinateX.toInt(), 0,
      widthMatchedWithRatioPreView.toInt(), bitmap.height, matrix, true)
  }

}
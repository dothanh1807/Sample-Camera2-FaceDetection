package com.vllenin.icamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.vllenin.icamera.view.AutoFitTextureView
import com.vllenin.icamera.view.FaceBorderView

interface ICamera {
  fun openCamera(cameraFace: CameraFace)

  fun switchCamera()

  fun capture(takePictureCallbacks: TakePictureCallbacks, delayMs: Int = 0)

  fun captureBurst(takePictureCallbacks: TakePictureCallbacks, delayMs: Int = 0)

  fun captureBurstFreeHand(takePictureCallbacks: TakePictureCallbacks, delayMs: Int = 0)

  fun closeCamera()

  class Builder(private val context: Context) {

    private lateinit var textureView: AutoFitTextureView
    private lateinit var borderView: FaceBorderView

    fun setTargetView(textureView: AutoFitTextureView) = apply { this.textureView = textureView }

    fun setFaceDetectionView(borderView: FaceBorderView) = apply { this.borderView = borderView }

    fun build(): ICamera? {
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Camera2(context, textureView, borderView)
      } else {
        Camera2(context, textureView, borderView)
      }
    }
  }

  interface TakePictureCallbacks {
    fun takePictureSucceeded(picture: Bitmap, isBurstMode: Boolean)

    fun takePictureFailed(e: Exception)
  }

  enum class CameraFace {
    FRONT,
    BACK
  }
}
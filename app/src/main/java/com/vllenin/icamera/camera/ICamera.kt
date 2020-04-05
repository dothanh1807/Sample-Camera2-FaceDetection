package com.vllenin.icamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.vllenin.icamera.view.AutoFitTextureView
import com.vllenin.icamera.view.FaceBorderView

interface ICamera {
  fun openCamera(cameraFace: CameraFace)

  fun switchCamera()

  fun capture(takePictureCallbacks: CaptureImageCallbacks)

  fun captureBurst(takePictureCallbacks: CaptureImageCallbacks)

  fun stopCaptureBurst()

  fun captureBurstFreeHand(takePictureCallbacks: CaptureImageCallbacks,
                           amountImage: Int, distance: Long, delayMs: Long)

  fun stopCaptureBurstFreeHand()

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

  interface CaptureImageCallbacks {
    fun captureSucceeded(picture: Bitmap)

    /** [captureBurst] and [captureBurstFreeHand] always callback this method */
    fun captureBurstSucceeded(picture: Bitmap?, sessionBurstFinished: Boolean)

    fun countDownTimerCaptureWithDelay(time: Long, ended: Boolean)

    fun captureImageFailed(e: Exception)
  }

  enum class CameraFace {
    FRONT,
    BACK
  }

  enum class ModeCapture {
    IDLE,
    CAPTURE,
    BURST,
    BURST_FREE_HAND
  }
}
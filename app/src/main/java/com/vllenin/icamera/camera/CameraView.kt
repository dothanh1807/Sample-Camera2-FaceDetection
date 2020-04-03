package com.vllenin.icamera.camera

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import com.vllenin.icamera.camera.ICamera.CameraFace
import com.vllenin.icamera.view.AutoFitTextureView
import com.vllenin.icamera.view.FaceBorderView
import com.vllenin.icamera.view.onSurfaceListener

/**
 * Wrapper camera using Fragment instead of ViewGroup, because i want handle state camera when
 * system calls methods of Lifecycle
 */
class CameraView: Fragment() {

  private lateinit var viewContainer: RelativeLayout
  private lateinit var textureView: AutoFitTextureView
  private lateinit var faceBorderView: FaceBorderView

  private var iCamera: ICamera? = null

  override fun onAttach(context: Context) {
    super.onAttach(context)
    val layoutParamsContainer = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
      RelativeLayout.LayoutParams.MATCH_PARENT)
    viewContainer = RelativeLayout(context)
    viewContainer.layoutParams = layoutParamsContainer

    val layoutParamsView = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
      RelativeLayout.LayoutParams.WRAP_CONTENT)
    // This line of code means preview is center crop
    layoutParamsView.addRule(RelativeLayout.CENTER_IN_PARENT)
    textureView = AutoFitTextureView(context)
    faceBorderView = FaceBorderView(context)

    viewContainer.addView(textureView, layoutParamsView)
    viewContainer.addView(faceBorderView, layoutParamsView)

    iCamera = ICamera.Builder(context)
      .setTargetView(textureView)
      .setFaceDetectionView(faceBorderView)
      .build()
  }

  override fun onCreateView(inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return viewContainer
  }

  override fun onStart() {
    super.onStart()
    if (textureView.isAvailable) {
      iCamera?.openCamera(CameraFace.BACK)
    } else {
      textureView.onSurfaceListener(
        onSurfaceAvailable = {
          iCamera?.openCamera(CameraFace.BACK)
        }
      )
    }
  }

  override fun onStop() {
    super.onStop()
    stopCaptureBurstFreeHand()
    iCamera?.closeCamera()
  }

  fun switchCamera() {
    iCamera?.switchCamera()
  }

  fun capture(takePictureCallbacks: ICamera.CaptureImageCallbacks) {
    iCamera?.capture(takePictureCallbacks)
  }

  fun captureBurst(takePictureCallbacks: ICamera.CaptureImageCallbacks) {
    iCamera?.captureBurst(takePictureCallbacks)
  }

  fun stopCaptureBurst() {
    iCamera?.stopCaptureBurst()
  }

  fun captureBurstFreeHand(takePictureCallbacks: ICamera.CaptureImageCallbacks,
                           amountImage: Int, distance: Long, delayMs: Long) {
    iCamera?.captureBurstFreeHand(takePictureCallbacks, amountImage, distance, delayMs)
  }

  fun stopCaptureBurstFreeHand() {
    iCamera?.stopCaptureBurstFreeHand()
  }

}
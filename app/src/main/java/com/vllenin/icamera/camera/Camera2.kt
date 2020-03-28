package com.vllenin.icamera.camera

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.View.OnTouchListener
import android.widget.RelativeLayout
import com.vllenin.icamera.BitmapUtils
import com.vllenin.icamera.DebugLog
import com.vllenin.icamera.camera.CameraUtils.CalculationSize.IMAGE
import com.vllenin.icamera.camera.CameraUtils.CalculationSize.PREVIEW
import com.vllenin.icamera.camera.ICamera.CameraFace
import kotlin.math.max
import kotlin.math.min

@TargetApi(VERSION_CODES.LOLLIPOP)
@SuppressLint("MissingPermission")
class Camera2(
  private val context: Context,
  private val textureView: AutoFitTextureView
) : ICamera {

  companion object {
    private const val FOCUS_SIZE = 400
    private const val FOCUS_TAG = "FOCUS_TAG"
  }

  private var cameraId : String = ""
  private var cameraDevice : CameraDevice? = null
  private var cameraCharacteristics: CameraCharacteristics? = null
  private var cameraCaptureSession : CameraCaptureSession? = null
  private var previewRequestBuilder : CaptureRequest.Builder? = null
  private var captureCallbacks : ICamera.TakePictureCallbacks? = null
  private var rectFocus = Rect()

  private lateinit var imageReader: ImageReader
  private lateinit var backgroundThread : HandlerThread
  private lateinit var backgroundHandler : Handler

  private val onTouchListener = OnTouchListener { view, p1 ->
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraCharacteristic = cameraManager.getCameraCharacteristics(cameraId)
    val cameraActiveArray = cameraCharacteristic.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
    val eventX = p1.x
    val eventY = p1.y

    val focusX = (eventY / view.height.toFloat() * cameraActiveArray.width().toFloat()).toInt()
    val focusY = ((1 - eventX / view.width.toFloat()) * cameraActiveArray.height().toFloat()).toInt()
    val left = max(focusX - FOCUS_SIZE, 0)
    val top = max(focusY - FOCUS_SIZE, 0)
    val right = min(left + FOCUS_SIZE * 2, cameraActiveArray.width())
    val bottom = min(top + FOCUS_SIZE * 2, cameraActiveArray.width())
    rectFocus = Rect(left, top, right, bottom)
    focusTo(rectFocus)

    true
  }

  /** ---------------------------------- Setup callbacks ------------------------------------ */

  private val cameraStateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(camera: CameraDevice) {
      cameraDevice = camera
      previewCamera()
    }

    override fun onDisconnected(cameraDevice: CameraDevice) {

    }

    override fun onError(cameraDevice: CameraDevice, p1: Int) {
      closeCamera()
    }

    override fun onClosed(camera: CameraDevice) {
      super.onClosed(camera)
      if (::backgroundThread.isInitialized) {
        backgroundThread.quitSafely()
      }
    }

  }

  private val cameraSessionStateCallbackForPreview =
    object : CameraCaptureSession.StateCallback() {
    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {

    }

    override fun onConfigured(captureSession: CameraCaptureSession) {
      cameraCaptureSession = captureSession
      previewRequestBuilder?.let {
        cameraCaptureSession?.setRepeatingRequest(it.build(), null, backgroundHandler)
      }
    }
  }

  private val imageReaderAvailableListener = ImageReader.OnImageAvailableListener { imageReader ->
    val image: Image
    try {
      image = imageReader.acquireNextImage()
    } catch (e: IllegalStateException) {
      DebugLog.e(e.message ?: "")
      captureCallbacks?.takePictureFailed(e)
      return@OnImageAvailableListener
    } catch (e: Exception) {
      DebugLog.e(e.message ?: "")
      captureCallbacks?.takePictureFailed(e)
      return@OnImageAvailableListener
    }

    backgroundHandler.post {
      val buffer = image.planes[0].buffer
      val bytes = ByteArray(buffer.remaining())
      buffer.get(bytes)
      try {
        val previewSize = Size(((textureView.parent as RelativeLayout).width),
          ((textureView.parent as RelativeLayout).height))
        val configureBitmap = BitmapUtils.configureBitmap(bytes, previewSize)

        captureCallbacks?.takePictureSucceeded(configureBitmap)
      } catch (e: Exception) {
        DebugLog.e(e.message ?: "")
        captureCallbacks?.takePictureFailed(e)
      } finally {
        buffer.clear()
        image.close()
      }
    }
  }

  private val captureCallback = object: CameraCaptureSession.CaptureCallback() {
    override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest,
      partialResult: CaptureResult) {

    }

    override fun onCaptureCompleted(
      session: CameraCaptureSession,
      request: CaptureRequest,
      result: TotalCaptureResult
    ) {
      if (request.tag == FOCUS_TAG) {
        try {
          previewRequestBuilder?.setTag(null)
          previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
          previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
          previewRequestBuilder?.let { cameraCaptureSession?.setRepeatingRequest(it.build(), this, backgroundHandler) }
        } catch (e: Exception) {
          DebugLog.e("Focus failed onCaptureCompleted ${e.message}")
        }
      }
    }
  }

  /** --------------------------------------------------------------------------------------------- **/

  @SuppressLint("ClickableViewAccessibility")
  override fun openCamera(cameraFace: CameraFace) {
    initBackgroundThread()

    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cameraId = if (cameraFace == CameraFace.BACK) { "0" } else { "1" }
    cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
    val sensorOrientation = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
    val mapConfig =
      cameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val previewSize = CameraUtils.calculationSize(mapConfig!!, PREVIEW)

    if (sensorOrientation == 90 || sensorOrientation == 270) {
      textureView.setAspectRatio(previewSize.height, previewSize.width)
    } else {
      textureView.setAspectRatio(previewSize.width, previewSize.height)
    }

    try {
      cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
    } catch (e: CameraAccessException) {
      DebugLog.e(e.message ?: "")
    }

    textureView.setOnTouchListener(onTouchListener)
  }

  override fun switchCamera() {
    closeCamera()
    if (cameraId.contains("0")) {
      openCamera(CameraFace.FRONT)
    } else {
      openCamera(CameraFace.BACK)
    }
  }

  override fun takePicture(callback : ICamera.TakePictureCallbacks) {
    captureCallbacks = callback

    val takePictureRequestBuilder =
      cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
    takePictureRequestBuilder?.addTarget(imageReader.surface)
    takePictureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    takePictureRequestBuilder?.let {
      cameraCaptureSession?.capture(it.build(), object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
          // Unlock focus || off flash
        }
      }, backgroundHandler)
    }
  }

  override fun previewCamera() {
    val mapConfig = cameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val imageSize = CameraUtils.calculationSize(mapConfig!!, IMAGE)
    textureView.surfaceTexture.setDefaultBufferSize(imageSize.width, imageSize.height)
    val surface = Surface(textureView.surfaceTexture)
    imageReader = ImageReader.newInstance(imageSize.width, imageSize.height, ImageFormat.JPEG, 2)
    imageReader.setOnImageAvailableListener(imageReaderAvailableListener, backgroundHandler)

    previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    previewRequestBuilder?.addTarget(surface)
    previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    if (!rectFocus.isEmpty) {
      focusTo(rectFocus)
    }

    cameraDevice?.createCaptureSession(listOf(surface, imageReader.surface), cameraSessionStateCallbackForPreview, backgroundHandler)
  }

  override fun closeCamera() {
    try {
      cameraCaptureSession?.close()
      cameraCaptureSession = null
      cameraDevice?.close()
      cameraDevice = null
      imageReader.close()
      quitBackgroundThread()
    } catch (e: CameraAccessException) {
      DebugLog.e(e.message ?: " When closeCamera")
    } catch (e: InterruptedException) {
      DebugLog.e(e.message ?: " When closeCamera")
    } catch (e: Exception) {
      DebugLog.e(e.message ?: " When closeCamera")
    }
  }

  private fun initBackgroundThread() {
    backgroundThread = HandlerThread("Camera2")
    backgroundThread.start()
    backgroundHandler = Handler(backgroundThread.looper ?: Looper.getMainLooper())
  }

  private fun quitBackgroundThread() {
    backgroundHandler.looper.quitSafely()
    backgroundThread.join()
  }

  private fun focusTo(rect: Rect) {
    try {
      cameraCaptureSession?.stopRepeating()
      previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
      previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
      val meteringRectangle = MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX)

      if(isMeteringAreaAFSupported()){
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
      }
      if(isMeteringAreaAESupported()){
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRectangle))
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
      }
      previewRequestBuilder?.setTag(FOCUS_TAG)
      previewRequestBuilder?.let { cameraCaptureSession?.capture(it.build(), captureCallback, backgroundHandler) }
    } catch (e: CameraAccessException) {
      DebugLog.e("Focus failed ${e.message}")
    } catch (illegalStateException: IllegalStateException) {
      DebugLog.e("Focus failed ${illegalStateException.message}")
    }
  }

  private fun isMeteringAreaAESupported(): Boolean {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraCharacteristic = cameraManager.getCameraCharacteristics(cameraId)
    val maxRegionsAE = cameraCharacteristic.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)
      ?: return false
    return maxRegionsAE >= 1
  }

  private fun isMeteringAreaAFSupported(): Boolean {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraCharacteristic = cameraManager.getCameraCharacteristics(cameraId)
    val maxRegionsAF = cameraCharacteristic.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)
      ?: return false
    return maxRegionsAF >= 1
  }

}
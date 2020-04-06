package com.vllenin.icamera.camera

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.Face
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View.OnTouchListener
import android.widget.RelativeLayout
import com.vllenin.icamera.R
import com.vllenin.icamera.camera.CameraUtils.CalculationSize.IMAGE
import com.vllenin.icamera.camera.CameraUtils.CalculationSize.PREVIEW
import com.vllenin.icamera.camera.ICamera.CameraFace
import com.vllenin.icamera.common.BitmapUtils
import com.vllenin.icamera.common.DebugLog
import com.vllenin.icamera.common.FileUtils
import com.vllenin.icamera.view.AutoFitTextureView
import com.vllenin.icamera.view.FaceBorderView
import java.util.concurrent.Semaphore
import kotlin.math.max
import kotlin.math.min

@TargetApi(VERSION_CODES.LOLLIPOP)
@SuppressLint("MissingPermission")
class Camera2(
  private val context: Context,
  private val textureView: AutoFitTextureView,
  private val faceBorderView: FaceBorderView
) : ICamera {

  companion object {
    private const val FOCUS_TAG = "FOCUS_TAG"
    private const val DISTANCE_REDRAW = 50
  }

  private var cameraId: String = ""
  private var cameraDevice: CameraDevice? = null
  private var cameraCharacteristics: CameraCharacteristics? = null
  private var cameraCaptureSession: CameraCaptureSession? = null
  private var previewRequestBuilder: CaptureRequest.Builder? = null
  private var takePictureCallbacks: ICamera.CaptureImageCallbacks? = null
  private var backgroundThread: HandlerThread? = null
  private var backgroundHandler: Handler? = null
  private var takePictureRunnable: Runnable? = null
  private var countDownRunnable: Runnable? = null
  private var mainHandler: Handler? = null
  private var rectFocus = Rect()
  private var arrayMeteringRectangle = ArrayList<MeteringRectangle>()
  private var countTasksTakePicture = 0
  private var countImagesSaved = 0
  private var amountImagesMustCapture = 0
  private var isLandscape = false
  private var modeFocus = ICamera.ModeFocus.AUTO_FOCUS_TO_FACES
  private var modeCapture = ICamera.ModeCapture.CAPTURE

  private lateinit var sensorArraySize: Size
  private lateinit var imageReader: ImageReader

  private val takePictureImageLock = Semaphore(1)
  private val focusSize = context.resources.getDimensionPixelSize(R.dimen.size_rect_after)
  private val delayChangeModeFocus = Runnable {
    modeFocus = ICamera.ModeFocus.AUTO_FOCUS_TO_FACES
  }

  private val orientationEventListener =
    object : OrientationEventListener(context) {
      override fun onOrientationChanged(orientation: Int) {
        isLandscape = if (orientation in 0..44 || orientation in 315..359) {
          false
        } else if (orientation in 45..134) {
          true
        } else orientation !in 135..224
      }
    }

  private val onTouchPreviewListener = OnTouchListener { view, event ->
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        // Convert coordinate when touch on preview to coordinate on sensor
        val focusX = (event.y / view.height.toFloat() * sensorArraySize.width.toFloat()).toInt()
        val focusY = ((1 - event.x / view.width.toFloat()) * sensorArraySize.height.toFloat()).toInt()

        val left = max(focusX - focusSize, 0)
        val top = max(focusY - focusSize, 0)
        val right = min(focusX + focusSize, sensorArraySize.width)
        val bottom = min(focusY + focusSize, sensorArraySize.height)
        rectFocus = Rect(left, top, right, bottom)
        mainHandler?.post { faceBorderView.touchTo(event.x, event.y) }
        focusTo(arrayOf(rectFocus))
        modeFocus = ICamera.ModeFocus.TOUCH_FOCUS
        // When touch focus, 4s later auto change focus to faces if face detection
        mainHandler?.removeCallbacks(delayChangeModeFocus)
        mainHandler?.postDelayed(delayChangeModeFocus, 4000)
      }
    }

    true
  }

  /** ---------------------------------- Setup callbacks ------------------------------------ */

  private val cameraStateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(camera: CameraDevice) {
      cameraDevice = camera
      previewCamera()
    }

    override fun onDisconnected(cameraDevice: CameraDevice) {}

    override fun onError(cameraDevice: CameraDevice, p1: Int) {
      closeCamera()
    }

  }

  private val cameraSessionStateCallbackForPreview =
    object : CameraCaptureSession.StateCallback() {
      override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {}

      override fun onConfigured(captureSession: CameraCaptureSession) {
        cameraCaptureSession = captureSession
        previewRequestBuilder?.let {
          cameraCaptureSession?.setRepeatingRequest(it.build(), captureCallback, backgroundHandler)
        }
      }
    }

  private val imageReaderAvailableListener = ImageReader.OnImageAvailableListener { imageReader ->
    if (countImagesSaved < amountImagesMustCapture || modeCapture != ICamera.ModeCapture.BURST_FREE_HAND) {
      val image: Image
      try {
        image = imageReader.acquireLatestImage()
      } catch (e: Exception) {
        countTasksTakePicture--
        takePictureImageLock.release()
        takePictureCallbacks?.captureImageFailed(e)
        return@OnImageAvailableListener
      }

      backgroundHandler?.post {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        try {
          /**
           * previewSize is Size of viewContainer at [CameraView.onAttach], because [textureView] has
           * been added to viewContainer
           */
          val previewSize = Size(((textureView.parent as RelativeLayout).width),
            ((textureView.parent as RelativeLayout).height))
          val configureBitmap = BitmapUtils.configureBitmap(bytes, previewSize, cameraId)
          if (modeCapture != ICamera.ModeCapture.IDLE) {
            FileUtils.saveImageJPEGIntoMediaFolder(context, configureBitmap, FileUtils.getNameImageDynamic()) {
              countImagesSaved++
              if (modeCapture != ICamera.ModeCapture.CAPTURE) {
                takePictureCallbacks?.captureBurstSucceeded(configureBitmap,
                  countImagesSaved == amountImagesMustCapture)
              } else {
                takePictureCallbacks?.captureSucceeded(configureBitmap)
              }
            }
          }
        } catch (e: Exception) {
          takePictureCallbacks?.captureImageFailed(e)
        } finally {
          takePictureImageLock.release()
          buffer.clear()
          image.close()
        }
      }
    }
  }

  private val captureCallback =
    object : CameraCaptureSession.CaptureCallback() {

    override fun onCaptureCompleted(
      session: CameraCaptureSession,
      request: CaptureRequest,
      result: TotalCaptureResult
    ) {
      if (modeFocus == ICamera.ModeFocus.AUTO_FOCUS_TO_FACES) {
        val facesArray = result[CaptureResult.STATISTICS_FACES]
        faceDetection(facesArray)
      }

      // Keep state AE/AF of sensor
      if (request.tag == FOCUS_TAG) {
        try {
          previewRequestBuilder?.setTag(null)
          previewRequestBuilder?.let {
            cameraCaptureSession?.setRepeatingRequest(it.build(), this, backgroundHandler)
          }
        } catch (e: Exception) {
          DebugLog.e("Focus failed onCaptureCompleted ${e.message}")
        }
      }
    }
  }

  /** --------------------------------------------------------------------------------------------- **/

  override fun openCamera(cameraFace: CameraFace) {
    initBackgroundThread()

    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cameraId = if (cameraFace == CameraFace.BACK) { "0" } else { "1" }
    cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
    sensorArraySize = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)!!
    val sensorOrientation = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
    val mapConfig =
      cameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
    val previewSize = CameraUtils.calculationSize(mapConfig, PREVIEW)

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
  }

  override fun switchCamera() {
    closeCamera()
    if (cameraId.contains("0")) {
      openCamera(CameraFace.FRONT)
    } else {
      openCamera(CameraFace.BACK)
    }
  }

  override fun capture(takePictureCallbacks: ICamera.CaptureImageCallbacks) {
    this.takePictureCallbacks = takePictureCallbacks
    modeCapture = ICamera.ModeCapture.CAPTURE
    takePictureImageLock.release()

    takePicture()
  }

  override fun captureBurst(takePictureCallbacks: ICamera.CaptureImageCallbacks) {
    this.takePictureCallbacks = takePictureCallbacks
    modeCapture = ICamera.ModeCapture.BURST
    takePictureImageLock.release()

    takePictureRunnable = Runnable {
      if (modeCapture != ICamera.ModeCapture.IDLE) {
        mainHandler?.post {
          takePictureImageLock.acquire()
          takePicture()
          backgroundHandler?.postDelayed(takePictureRunnable!!, 100)
        }
      }
    }
    backgroundHandler?.post(takePictureRunnable!!)
  }

  override fun stopCaptureBurst() {
    takePictureImageLock.release()
    modeCapture = ICamera.ModeCapture.IDLE
    takePictureCallbacks = null
    backgroundHandler?.removeCallbacks(takePictureRunnable ?: Runnable {})
  }

  override fun captureBurstFreeHand(takePictureCallbacks: ICamera.CaptureImageCallbacks,
    amountImage: Int, distance: Long, delayMs: Long) {
    stopCaptureBurstFreeHand()
    this.takePictureCallbacks = takePictureCallbacks
    modeCapture = ICamera.ModeCapture.BURST_FREE_HAND
    takePictureImageLock.release()
    countTasksTakePicture = 0
    countImagesSaved = 0
    amountImagesMustCapture = amountImage

    var timeCountDown = 0
    countDownRunnable = Runnable {
      if (timeCountDown < delayMs) {
        takePictureCallbacks.countDownTimerCaptureWithDelay(delayMs - timeCountDown, false)
        timeCountDown += 1000
        backgroundHandler?.postDelayed(countDownRunnable!!, 1000)
      } else {
        takePictureCallbacks.countDownTimerCaptureWithDelay(1000, true)
        takePictureRunnable = Runnable {
          if (modeCapture != ICamera.ModeCapture.IDLE && countTasksTakePicture < amountImage) {
            mainHandler?.post {
              takePictureImageLock.acquire()
              countTasksTakePicture++
              takePicture()
              backgroundHandler?.postDelayed(takePictureRunnable!!, distance)
            }
          }
        }
        backgroundHandler?.post(takePictureRunnable!!)
      }
    }
    backgroundHandler?.post(countDownRunnable!!)

  }

  override fun stopCaptureBurstFreeHand() {
    backgroundHandler?.removeCallbacks(countDownRunnable ?: Runnable {})
    takePictureCallbacks?.countDownTimerCaptureWithDelay(-1, true)
    stopCaptureBurst()
  }

  override fun closeCamera() {
    try {
      orientationEventListener.disable()
      cameraCaptureSession?.close()
      cameraCaptureSession = null
      cameraDevice?.close()
      cameraDevice = null
      imageReader.close()
      quitBackgroundThread()
    } catch (e: CameraAccessException) {
      DebugLog.e(e.message ?: e.toString())
    } catch (e: InterruptedException) {
      DebugLog.e(e.message ?: e.toString())
    } catch (e: Exception) {
      DebugLog.e(e.message ?: e.toString())
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun previewCamera() {
    orientationEventListener.enable()
    val mapConfig = cameraCharacteristics?.get(
      CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val imageSize = CameraUtils.calculationSize(mapConfig!!, IMAGE)
    textureView.surfaceTexture.setDefaultBufferSize(imageSize.width, imageSize.height)
    textureView.setOnTouchListener(onTouchPreviewListener)
    val surface = Surface(textureView.surfaceTexture)
    imageReader = ImageReader.newInstance(imageSize.width, imageSize.height, ImageFormat.JPEG, 2)
    imageReader.setOnImageAvailableListener(imageReaderAvailableListener, backgroundHandler)

    previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    previewRequestBuilder?.addTarget(surface)
    previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    // Face detection with mode MODE_SIMPLE, can change to STATISTICS_FACE_DETECT_MODE_FULL
    previewRequestBuilder?.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
      CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)

    cameraDevice?.createCaptureSession(listOf(surface, imageReader.surface),
      cameraSessionStateCallbackForPreview, backgroundHandler)
  }

  private fun takePicture() {
    val takePictureRequestBuilder =
      cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
    takePictureRequestBuilder?.addTarget(imageReader.surface)
    takePictureRequestBuilder?.let {
      cameraCaptureSession?.capture(it.build(), object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession,
          request: CaptureRequest,
          result: TotalCaptureResult) {

        }
      }, null)
    }
  }

  private fun initBackgroundThread() {
    backgroundThread = HandlerThread("Camera2")
    backgroundThread?.start()
    backgroundHandler = Handler(backgroundThread?.looper ?: Looper.getMainLooper())

    mainHandler = Handler(Looper.getMainLooper())
  }

  private fun quitBackgroundThread() {
    backgroundHandler?.looper?.quitSafely()
    backgroundThread?.join()
  }

  private fun focusTo(arrayRect: Array<Rect>) {
    try {
      cameraCaptureSession?.stopRepeating()
      previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER,
        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
      previewRequestBuilder?.setTag(FOCUS_TAG)
      previewRequestBuilder?.let {
        cameraCaptureSession?.capture(it.build(), captureCallback, backgroundHandler)
      }

      arrayMeteringRectangle.clear()
      arrayRect.forEach {
        val meteringRectangle = MeteringRectangle(it, MeteringRectangle.METERING_WEIGHT_MAX - 1)
        arrayMeteringRectangle.add(meteringRectangle)
      }

      if (isMeteringAreaAFSupported()) {
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE,
          CameraMetadata.CONTROL_AF_MODE_AUTO)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, arrayMeteringRectangle.toTypedArray())
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER,
          CameraMetadata.CONTROL_AF_TRIGGER_START)
      }
      if (isMeteringAreaAESupported()) {
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, arrayMeteringRectangle.toTypedArray())
      }
      previewRequestBuilder?.setTag(FOCUS_TAG)
      previewRequestBuilder?.let {
        cameraCaptureSession?.capture(it.build(), captureCallback, backgroundHandler)
      }
    } catch (e: CameraAccessException) {
      DebugLog.e("Focus failed ${e.message}")
    } catch (e: IllegalStateException) {
      DebugLog.e("Focus failed ${e.message}")
    }
  }

  private fun isMeteringAreaAESupported(): Boolean {
    val maxRegionsAE = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)
      ?: return false
    return maxRegionsAE >= 1
  }

  private fun isMeteringAreaAFSupported(): Boolean {
    val maxRegionsAF = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)
      ?: return false
    return maxRegionsAF >= 1
  }

  private fun faceDetection(facesArray: Array<Face>?) {
    facesArray?.let {
      val boundsArray = Array<Rect>(facesArray.size) {
        facesArray[it].bounds
      }
      if (!boundsArray.isNullOrEmpty()) {
        if (rectFocus.centerY() < boundsArray[0].centerY() - DISTANCE_REDRAW ||
          rectFocus.centerY() > boundsArray[0].centerY() + DISTANCE_REDRAW ||
          rectFocus.centerX() < boundsArray[0].centerX() - DISTANCE_REDRAW ||
          rectFocus.centerX() > boundsArray[0].centerX() + DISTANCE_REDRAW ||
          faceBorderView.isFadeOut) {

          rectFocus = boundsArray[0]
          focusTo(boundsArray)

          val arrayRectWillDrawOnPreview = ArrayList<RectF>()
          boundsArray.forEach { boundsOnSensor ->
            val centerY: Float
            val centerX: Float
            val previewX: Float
            val previewY: Float
            var ratioFace = 1.3f

            if (cameraId.contains("1")) {// front camera
              centerY = (1.0f - boundsOnSensor.centerX().toFloat() / sensorArraySize.width) *
                textureView.height
              centerX = (1.0f - boundsOnSensor.centerY().toFloat() / sensorArraySize.height) *
                textureView.width
              previewX = centerX
              previewY = centerY
              ratioFace = 1f
            } else {
              // This formula is formula at onTouchPreviewListener in this class
              centerY = ((boundsOnSensor.centerX().toFloat() * textureView.height.toFloat()) /
                sensorArraySize.width.toFloat())
              centerX = ((1.0f - boundsOnSensor.centerY().toFloat() / sensorArraySize.height.toFloat()) *
                (textureView.width.toFloat()))
              /**
               * Workaround: Because i don't know why with this formula, i convert coordinate on
               * preview to coordinate on sensor is correct [onTouchPreviewListener], but in here
               * is incorrect.
               * If someone resolve this issues, please push your branch to git.
               */
              val plusX = (centerX - (textureView.width/2)) * (textureView.width.toFloat() /
                sensorArraySize.height)
              /**
               * Because preview is center crop, if above crop then "plusY = 0". Refer:
               * layoutParamsView.addRule(RelativeLayout.CENTER_IN_PARENT) in [CameraView.onAttach]
               */
              val plusY = (textureView.height/2 - textureView.height/2)

              previewX = centerX + plusX.toInt()
              previewY = centerY - plusY
            }

            val widthFace: Float
            val heightFace: Float
            if (!isLandscape) {
              widthFace = (boundsOnSensor.width().toFloat() * textureView.height.toFloat() /
                sensorArraySize.width * ratioFace)
              heightFace = (boundsOnSensor.height().toFloat() * textureView.width.toFloat() /
                sensorArraySize.height * ratioFace)
            } else {
              widthFace = (boundsOnSensor.height().toFloat() * textureView.width.toFloat() /
                sensorArraySize.height * ratioFace)
              heightFace = (boundsOnSensor.width().toFloat() * textureView.height.toFloat() /
                sensorArraySize.width * ratioFace)
            }

            val boundsOnPreview = RectF(previewX - heightFace/2, previewY - widthFace/2,
              previewX + heightFace/2, previewY + widthFace/2)
            arrayRectWillDrawOnPreview.add(boundsOnPreview)
          }
          mainHandler?.post { faceBorderView.drawListFace(arrayRectWillDrawOnPreview.toTypedArray()) }
        }
      } else if (boundsArray.isNullOrEmpty()) {
        mainHandler?.post { faceBorderView.fadeOut() }
      }
    }
  }

}
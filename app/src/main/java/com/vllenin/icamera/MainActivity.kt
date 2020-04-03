package com.vllenin.icamera

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.vllenin.icamera.camera.CameraView
import com.vllenin.icamera.camera.ICamera.CaptureImageCallbacks
import com.vllenin.icamera.common.DebugLog
import com.vllenin.icamera.common.FileUtils
import com.vllenin.icamera.common.permission.PermissionUtil
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : AppCompatActivity() {

  private var countImage = 0

  private val takePictureCallbacks = object : CaptureImageCallbacks {
    override fun captureSucceeded(picture: Bitmap) {
      runOnUiThread {
        Log.d("XXX", "captureSucceeded")
        textView.visibility = View.INVISIBLE
        imagePreview.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
        imageView.setImageBitmap(picture)
      }
    }

    override fun captureBurstSucceeded(picture: Bitmap, sessionBurstCompleted: Boolean) {
      runOnUiThread {
        Log.d("XXX", "captureBurstSucceeded $sessionBurstCompleted")
        textView.visibility = View.VISIBLE
        imagePreview.visibility = View.INVISIBLE
        overlay.visibility = View.INVISIBLE
        countImage++
        textView.text = countImage.toString()
      }
    }

    override fun countDownTimerCaptureWithDelay(time: Long, ended: Boolean) {
      runOnUiThread {
        if (ended) {
          countDown.visibility = View.INVISIBLE
        } else {
          countDown.visibility = View.VISIBLE
        }
        countDown.text = (time / 1000).toString()
      }
    }

    override fun captureImageFailed(e: Exception) {
      DebugLog.e(e.message ?: e.toString())
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    PermissionUtil(this).request(android.Manifest.permission.CAMERA,
      android.Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted, _ ->
      if (granted) {
        initCamera()
        initMediaFolder()
      }
    }

  }

  private fun initCamera() {
    val cameraView = CameraView()
    supportFragmentManager.beginTransaction()
      .replace(R.id.fragmentContainer, cameraView, CameraView::class.toString())
      .addToBackStack(null)
      .commit()

    switchCamera.setOnClickListener {
      cameraView.switchCamera()
    }

    /**************************** Capture ************************************************/

    capture.setOnClickListener {
      cameraView.capture(takePictureCallbacks)

    }

    /****************************** Burst **********************************************/

    burst.setOnLongClickListener {
      countImage = 0
      cameraView.captureBurst(takePictureCallbacks)

      true
    }

    burst.setOnTouchListener { _, motionEvent ->
      when (motionEvent.action) {
        MotionEvent.ACTION_UP -> {
          cameraView.stopCaptureBurst()
        }
      }

      false
    }

    /***************************** Burst free hand ************************************************/

    burstFreeHand.setOnClickListener {
      countImage = 0
      cameraView.captureBurstFreeHand(takePictureCallbacks, 10, 500L, 5000L)
    }
  }

  private fun initMediaFolder() {
    val file = File(FileUtils.getPathMediaFolder())
    if (!file.exists()) {
      file.mkdirs()
    }
  }

  override fun onBackPressed() {
    when {
      textView.visibility == View.VISIBLE -> textView.visibility = View.INVISIBLE

      imagePreview.visibility == View.VISIBLE -> {
        overlay.visibility = View.INVISIBLE
        imagePreview.visibility = View.INVISIBLE
      }

      else -> super.onBackPressed()
    }
  }


}

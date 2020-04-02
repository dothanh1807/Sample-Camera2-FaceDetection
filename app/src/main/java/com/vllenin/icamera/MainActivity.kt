package com.vllenin.icamera

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vllenin.icamera.camera.CameraView
import com.vllenin.icamera.camera.ICamera.TakePictureCallbacks
import com.vllenin.icamera.common.DebugLog
import com.vllenin.icamera.common.FileUtils
import com.vllenin.icamera.common.permission.PermissionUtil
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : AppCompatActivity() {

  private var permissionUtils: PermissionUtil? = null
  private var countImage = 0

  private val takePictureCallbacks = object : TakePictureCallbacks {
    override fun takePictureSucceeded(picture: Bitmap, isBurstMode: Boolean) {
      runOnUiThread {
        imagePreview.visibility = View.VISIBLE
        if (isBurstMode) {
          Log.d("XXX", "takePictureSucceeded 111")
          countImage++
          textView.text = countImage.toString()
        } else {
          Log.d("XXX", "takePictureSucceeded 222")
          overlay.visibility = View.VISIBLE
          imageView.setImageBitmap(picture)
        }
      }

    }

    override fun takePictureFailed(e: Exception) {
      DebugLog.e(e.message ?: e.toString())
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    permissionUtils = PermissionUtil(this)
    permissionUtils?.request(android.Manifest.permission.CAMERA,
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

    /**********************************************************************************************/

    takePicture.setOnClickListener {
      cameraView.capture(takePictureCallbacks)

    }

    /**********************************************************************************************/

    burst.setOnLongClickListener {
      it.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
      countImage = 0
      cameraView.captureBurst(takePictureCallbacks)

      true
    }

    burst.setOnTouchListener { view, motionEvent ->
      when (motionEvent.action) {
        MotionEvent.ACTION_UP -> {
          view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
          cameraView.stopCaptureBurst()
        }
      }

      false
    }

    /**********************************************************************************************/

    burstFreeHand.setOnClickListener {

    }
  }

  private fun initMediaFolder() {
    val file = File(FileUtils.getPathFolderMedia())
    if (!file.exists()) {
      file.mkdirs()
    }
  }

  override fun onBackPressed() {
    if (imagePreview.visibility == View.VISIBLE) {
      overlay.visibility = View.INVISIBLE
      imagePreview.visibility = View.INVISIBLE
    } else {
      super.onBackPressed()
    }
  }


}

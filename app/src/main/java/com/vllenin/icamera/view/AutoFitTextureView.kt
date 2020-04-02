package com.vllenin.icamera.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView

class AutoFitTextureView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : TextureView(context, attrs, defStyle) {

  private var ratioWidth = 0
  private var ratioHeight = 0
  private var isFillSpace = true
    set(fillSpace) {
      field = fillSpace
      requestLayout()
    }
  private var displayOrientation: Int = 0
    set(displayOrientation) {
      field = displayOrientation
      configureTransform()
    }

  init {
    surfaceTextureListener = object : SurfaceTextureListener {

      override fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int
      ) {
        configureTransform()
      }

      override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int
      ) {
        configureTransform()
      }

      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return true
      }

      override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
  }

  fun setAspectRatio(width: Int, height: Int) {
    require(!(width < 0 || height < 0)) { "Size cannot be negative." }
    ratioWidth = width
    ratioHeight = height
    requestLayout()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val height = MeasureSpec.getSize(heightMeasureSpec)
    if (0 == ratioWidth || 0 == ratioHeight) {
      setMeasuredDimension(width, height)
    } else {
      // is filling space by default
      val isFillSpaceWithoutScale = width == height * ratioWidth / ratioHeight
      if (isFillSpaceWithoutScale) {
        setMeasuredDimension(width, height)
        return
      }

      if (isFillSpace) {
        if (width < height * ratioWidth / ratioHeight) {
          setMeasuredDimension(height * ratioWidth / ratioHeight, height)
        } else {
          setMeasuredDimension(width, width * ratioHeight / ratioWidth)
        }
      } else {
        if (width < height * ratioWidth / ratioHeight) {
          setMeasuredDimension(width, width * ratioHeight / ratioWidth)
        } else {
          setMeasuredDimension(height * ratioWidth / ratioHeight, height)
        }
      }
    }
  }

  private fun configureTransform() {
    val matrix = Matrix()

    if (this.displayOrientation % 180 == 90) {
      val width = width
      val height = height
      // Rotate the camera preview when the screen is landscape.
      matrix.setPolyToPoly(
        floatArrayOf(
          0f, 0f, // top left
          width.toFloat(), 0f, // top right
          0f, height.toFloat(), // bottom left
          width.toFloat(), height.toFloat()
        )// bottom right
        , 0,
        if (this.displayOrientation == 90)
        // Clockwise
          floatArrayOf(
            0f, height.toFloat(), // top left
            0f, 0f, // top right
            width.toFloat(), height.toFloat(), // bottom left
            width.toFloat(), 0f
          )// bottom right
        else
        // mDisplayOrientation == 270
        // Counter-clockwise
          floatArrayOf(
            width.toFloat(), 0f, // top left
            width.toFloat(), height.toFloat(), // top right
            0f, 0f, // bottom left
            0f, height.toFloat()
          )// bottom right
        , 0,
        4
      )
    } else if (this.displayOrientation == 180) {
      matrix.postRotate(180f, (width / 2).toFloat(), (height / 2).toFloat())
    }
    setTransform(matrix)
  }
}
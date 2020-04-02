package com.vllenin.icamera.view

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceBorderView(context: Context?, attrs: AttributeSet? = null) : View(context, attrs) {

    companion object {
        private const val TIME_DETECT_ANIMATION: Long = 100
        private const val TIME_FOCUS_ANIMATION: Long = 300
        private const val TIME_ALPHA_ANIMATION: Long = 500
        private const val TIME_DELAY_HIDE_FOCUS: Long = 2000
        private const val TIME_DELAY: Long = 5000
        private const val MODE_TOUCH_FOCUS = 1
        private const val MODE_AUTO_FACE_DETECT = 2
    }

    private val listCenterCoordinates = ArrayList<RectF>()
    private val listRectF = ArrayList<RectF>()
    private val rectFTouch = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var flagModeAutoFaceDetect = true
    private var flagModeTouchFocus = true
    private var flagFirst = true
    private var mode = MODE_TOUCH_FOCUS
    private var touchX: Float = 0f
    private var touchY: Float = 0f
    private val sizeRecFBefore = 250f
    private val sizeRecFLate = 150f
    private var animatorTouchFocus: ValueAnimator? = null
    private var animatorFaceDetection: ValueAnimator? = null
    private val propertyLeft = "left"
    private val propertyTop = "top"
    private val propertyRight = "right"
    private val propertyBottom = "bottom"
    private val corner = 20f
    var isFadeOut = false

    private val runnableAutoFadeOut = Runnable {
        animate().alpha(0f).setDuration(TIME_ALPHA_ANIMATION).start()
    }

    private var runnableChangeMode = Runnable {
        if (listCenterCoordinates.isNotEmpty()) {
            alpha = 1f
            mode = MODE_AUTO_FACE_DETECT
            flagModeAutoFaceDetect = true
        }
    }

    init {
        paint.color = Color.YELLOW
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {

        when (MODE_AUTO_FACE_DETECT) {// Cause only mode auto focus to faces
            MODE_TOUCH_FOCUS -> {
                if (touchX == 0f) {
                    touchX = width / 2f
                    touchY = height / 2f
                    rectFTouch.left = touchX - sizeRecFLate
                    rectFTouch.top = touchY - sizeRecFLate
                    rectFTouch.right = touchX + sizeRecFLate
                    rectFTouch.bottom = touchY + sizeRecFLate
                }
                canvas.drawRoundRect(rectFTouch.left, rectFTouch.top, rectFTouch.right,
                    rectFTouch.bottom, corner, corner, paint)
            }

            MODE_AUTO_FACE_DETECT -> {
                if (listCenterCoordinates.isNotEmpty()) {
                    for (i in 0..listCenterCoordinates.lastIndex) {
                        canvas.drawRoundRect(listRectF[i].left, listRectF[i].top,
                            listRectF[i].right, listRectF[i].bottom, corner, corner, paint)
                    }
                }
            }
        }
    }

    fun drawListFace(list: Array<RectF>) {
        listCenterCoordinates.clear()
        listCenterCoordinates.addAll(list)
        getCoordinatesOfFace()
        if (true) {// Cause only mode auto focus to faces
            if(list.isNotEmpty()) {
                alpha = 1f
                isFadeOut = false
                removeCallbacks(runnableAutoFadeOut)
                for (i in 0..list.lastIndex) {
                    animatorFaceDetection = ValueAnimator.ofPropertyValuesHolder(
                        PropertyValuesHolder.ofFloat(propertyLeft, listRectF[i].left,
                            list[i].left),
                        PropertyValuesHolder.ofFloat(propertyTop, listRectF[i].top,
                            list[i].top),
                        PropertyValuesHolder.ofFloat(propertyRight, listRectF[i].right,
                            list[i].right),
                        PropertyValuesHolder.ofFloat(propertyBottom, listRectF[i].bottom,
                            list[i].bottom))
                    animatorFaceDetection?.duration =
                        TIME_DETECT_ANIMATION
                    animatorFaceDetection?.addUpdateListener {
                        listRectF[i].left = it.getAnimatedValue(propertyLeft) as Float
                        listRectF[i].top = it.getAnimatedValue(propertyTop) as Float
                        listRectF[i].right = it.getAnimatedValue(propertyRight) as Float
                        listRectF[i].bottom = it.getAnimatedValue(propertyBottom) as Float

                        if ((it.getAnimatedValue(propertyBottom) as Float) == list[i].bottom && alpha == 1f) {
                            postDelayed(runnableAutoFadeOut,
                                TIME_DELAY_HIDE_FOCUS
                            )
                        }

                        invalidate()
                    }
                    animatorFaceDetection?.start()
                }
            }
        } else {
            if(list.isNotEmpty()){
                if(flagModeTouchFocus){
                    mode = MODE_AUTO_FACE_DETECT
                    alpha = 1f
                    flagModeTouchFocus = false
                }
            }
        }
    }

    fun touchTo(x: Float, y: Float) {
        removeCallbacks(runnableChangeMode)
        animate().cancel()
        animatorTouchFocus?.cancel()
        animatorTouchFocus?.removeAllUpdateListeners()
        touchX = x
        touchY = y
        alpha = 1f
        flagFirst = true
        mode = MODE_TOUCH_FOCUS
        postDelayed(runnableChangeMode,
            TIME_DELAY
        )
        animatorTouchFocus = ValueAnimator.ofPropertyValuesHolder(
            PropertyValuesHolder.ofFloat(propertyLeft, touchX - sizeRecFBefore,
                touchX - sizeRecFLate),
            PropertyValuesHolder.ofFloat(propertyTop, touchY - sizeRecFBefore,
                touchY - sizeRecFLate),
            PropertyValuesHolder.ofFloat(propertyRight, touchX + sizeRecFBefore,
                touchX + sizeRecFLate),
            PropertyValuesHolder.ofFloat(propertyBottom, touchY +
                    sizeRecFBefore, touchY + sizeRecFLate))
        animatorTouchFocus?.duration =
            TIME_FOCUS_ANIMATION
        animatorTouchFocus?.addUpdateListener {
            rectFTouch.left = it.getAnimatedValue(propertyLeft) as Float
            rectFTouch.top = it.getAnimatedValue(propertyTop) as Float
            rectFTouch.right = it.getAnimatedValue(propertyRight) as Float
            rectFTouch.bottom = it.getAnimatedValue(propertyBottom) as Float
            invalidate()
        }
        animatorTouchFocus?.start()
    }

    fun fadeOut() {
        if (!isFadeOut) {
            listCenterCoordinates.clear()
            removeCallbacks(runnableAutoFadeOut)
            animate().alpha(0f).setDuration(TIME_FOCUS_ANIMATION).start()
            isFadeOut = true
        }
    }

    private fun getCoordinatesOfFace() {
        for (i in 0..listCenterCoordinates.lastIndex) {
            if (listRectF.size < listCenterCoordinates.size) {
                listRectF.add(RectF())
            }
        }
    }

}
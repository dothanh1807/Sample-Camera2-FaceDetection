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
import com.vllenin.icamera.R

class FaceBorderView(context: Context?, attrs: AttributeSet? = null) : View(context, attrs) {

    companion object {
        private const val TIME_DETECT_ANIMATION: Long = 100
        private const val TIME_FOCUS_ANIMATION: Long = 300
        private const val TIME_ALPHA_ANIMATION: Long = 500
        private const val TIME_DELAY_HIDE_FOCUS: Long = 2000
        private const val TIME_DELAY: Long = 4000
        private const val MODE_TOUCH_FOCUS = 1
        private const val MODE_AUTO_FACE_DETECT = 2
    }

    private val listFaces = ArrayList<RectF>()
    private val listRectF = ArrayList<RectF>()
    private val rectFTouch = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sizeRecFBefore = resources.getDimensionPixelSize(R.dimen.size_rect_before)
    private val sizeRecFLate = resources.getDimensionPixelSize(R.dimen.size_rect_after)
    private val widthLine = resources.getDimensionPixelSize(R.dimen.width_line)
    private val propertyLeft = "left"
    private val propertyTop = "top"
    private val propertyRight = "right"
    private val propertyBottom = "bottom"
    private val corner = 20f

    var isFadeOut = false

    private var mode = MODE_AUTO_FACE_DETECT
    private var touchX: Float = 0f
    private var touchY: Float = 0f
    private var animatorTouchFocus: ValueAnimator? = null
    private var animatorFaceDetection: ValueAnimator? = null

    private val runnableAutoFadeOut = Runnable {
        animate().alpha(0f).setDuration(TIME_ALPHA_ANIMATION).start()
    }

    private var runnableChangeMode = Runnable {
        if (listFaces.isNotEmpty()) {
            alpha = 1f
            mode = MODE_AUTO_FACE_DETECT
        }
    }

    init {
        paint.color = Color.YELLOW
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        when (mode) {
            MODE_TOUCH_FOCUS -> {
                canvas.drawRect(rectFTouch.left, rectFTouch.top, rectFTouch.right,
                    rectFTouch.bottom, paint)
                canvas.drawLine(rectFTouch.left, (rectFTouch.top + rectFTouch.bottom) / 2,
                  rectFTouch.left + widthLine, (rectFTouch.top + rectFTouch.bottom) / 2, paint)
                canvas.drawLine(rectFTouch.right, (rectFTouch.top + rectFTouch.bottom) / 2,
                  rectFTouch.right - widthLine, (rectFTouch.top + rectFTouch.bottom) / 2, paint)
                canvas.drawLine((rectFTouch.left + rectFTouch.right) / 2, rectFTouch.top,
                  (rectFTouch.left + rectFTouch.right) / 2, rectFTouch.top + widthLine, paint)
                canvas.drawLine((rectFTouch.left + rectFTouch.right) / 2, rectFTouch.bottom,
                  (rectFTouch.left + rectFTouch.right) / 2, rectFTouch.bottom - widthLine, paint)
            }

            MODE_AUTO_FACE_DETECT -> {
                if (listFaces.isNotEmpty()) {
                    for (i in 0..listFaces.lastIndex) {
                        canvas.drawRoundRect(listRectF[i].left, listRectF[i].top,
                            listRectF[i].right, listRectF[i].bottom, corner, corner, paint)
                    }
                }
            }
        }
    }

    fun drawListFace(list: Array<RectF>) {
        listFaces.clear()
        listFaces.addAll(list)
        for (i in 0..listFaces.lastIndex) {
            if (listRectF.size < listFaces.size) {
                listRectF.add(RectF())
            }
        }

        if (mode == MODE_AUTO_FACE_DETECT) {
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
                    animatorFaceDetection?.duration = TIME_DETECT_ANIMATION
                    animatorFaceDetection?.addUpdateListener {
                        listRectF[i].left = it.getAnimatedValue(propertyLeft) as Float
                        listRectF[i].top = it.getAnimatedValue(propertyTop) as Float
                        listRectF[i].right = it.getAnimatedValue(propertyRight) as Float
                        listRectF[i].bottom = it.getAnimatedValue(propertyBottom) as Float

                        if ((it.getAnimatedValue(propertyBottom) as Float) == list[i].bottom && alpha == 1f) {
                            postDelayed(runnableAutoFadeOut, TIME_DELAY_HIDE_FOCUS)
                        }

                        invalidate()
                    }
                    animatorFaceDetection?.start()
                }
            }
        } else {
            if(list.isNotEmpty() && mode != MODE_TOUCH_FOCUS){
                mode = MODE_AUTO_FACE_DETECT
                alpha = 1f
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
        mode = MODE_TOUCH_FOCUS
        postDelayed(runnableChangeMode, TIME_DELAY)
        removeCallbacks(runnableAutoFadeOut)
        animatorTouchFocus = ValueAnimator.ofPropertyValuesHolder(
            PropertyValuesHolder.ofFloat(propertyLeft, touchX - sizeRecFBefore,
                touchX - sizeRecFLate),
            PropertyValuesHolder.ofFloat(propertyTop, touchY - sizeRecFBefore,
                touchY - sizeRecFLate),
            PropertyValuesHolder.ofFloat(propertyRight, touchX + sizeRecFBefore,
                touchX + sizeRecFLate),
            PropertyValuesHolder.ofFloat(propertyBottom, touchY +
                    sizeRecFBefore, touchY + sizeRecFLate))
        animatorTouchFocus?.duration = TIME_FOCUS_ANIMATION
        animatorTouchFocus?.addUpdateListener {
            rectFTouch.left = it.getAnimatedValue(propertyLeft) as Float
            rectFTouch.top = it.getAnimatedValue(propertyTop) as Float
            rectFTouch.right = it.getAnimatedValue(propertyRight) as Float
            rectFTouch.bottom = it.getAnimatedValue(propertyBottom) as Float

            if ((it.getAnimatedValue(propertyBottom) as Float) == (touchY + sizeRecFLate) && alpha == 1f) {
                postDelayed(runnableAutoFadeOut, TIME_DELAY_HIDE_FOCUS)
            }

            invalidate()
        }
        animatorTouchFocus?.start()
    }

    fun fadeOut() {
        if (!isFadeOut) {
            listFaces.clear()
            removeCallbacks(runnableAutoFadeOut)
            animate().alpha(0f).setDuration(TIME_FOCUS_ANIMATION).start()
            isFadeOut = true
        }
    }

}
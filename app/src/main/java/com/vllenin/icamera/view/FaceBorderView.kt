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

    private val mListCenterCoordinates = ArrayList<RectF>()
    private val mListRectF = ArrayList<RectF>()
    private val mRectFTouch = RectF()
    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mFlagModeAutoFaceDetect = true
    private var mFlagModeTouchFocus = true
    private var mFlagFirst = true
    private var mMode = MODE_TOUCH_FOCUS
    private var mTouchX: Float = 0f
    private var mTouchY: Float = 0f
    private val mSizeRecFBefore = 250f
    private val mSizeRecFLate = 150f
    private var animatorTouchFocus: ValueAnimator? = null
    private var animatorFaceDetection: ValueAnimator? = null
    private val mPropertyLeft = "left"
    private val mPropertyTop = "top"
    private val mPropertyRight = "right"
    private val mPropertyBottom = "bottom"
    private val corner = 20f
    var isFadeOut = false
    private val runnableFadeOutDelay = Runnable {
        animate().alpha(0f).setDuration(TIME_ALPHA_ANIMATION).start()
    }

    private var mRunnableChangeMode = Runnable {
        if (mListCenterCoordinates.isNotEmpty()) {
            alpha = 1f
            mMode = MODE_AUTO_FACE_DETECT
            mFlagModeAutoFaceDetect = true
        }
    }

    companion object {
        private const val TIME_DETECT_ANIMATION: Long = 100
        private const val TIME_FOCUS_ANIMATION: Long = 300
        private const val TIME_ALPHA_ANIMATION: Long = 500
        private const val TIME_DELAY_HIDE_FOCUS: Long = 2000
        private const val TIME_DELAY: Long = 5000
        private const val MODE_TOUCH_FOCUS = 1
        private const val MODE_AUTO_FACE_DETECT = 2
    }

    init {
        mPaint.color = Color.YELLOW
        mPaint.strokeWidth = 3f
        mPaint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {

        when (MODE_AUTO_FACE_DETECT) {// Cause only mode auto focus to faces
            MODE_TOUCH_FOCUS -> {
                if (mTouchX == 0f) {
                    mTouchX = width / 2f
                    mTouchY = height / 2f
                    mRectFTouch.left = mTouchX - mSizeRecFLate
                    mRectFTouch.top = mTouchY - mSizeRecFLate
                    mRectFTouch.right = mTouchX + mSizeRecFLate
                    mRectFTouch.bottom = mTouchY + mSizeRecFLate
                }
                canvas.drawRoundRect(mRectFTouch.left, mRectFTouch.top, mRectFTouch.right,
                    mRectFTouch.bottom, corner, corner, mPaint)
            }

            MODE_AUTO_FACE_DETECT -> {
                if (mListCenterCoordinates.isNotEmpty()) {
                    for (i in 0..mListCenterCoordinates.lastIndex) {
                        canvas.drawRoundRect(mListRectF[i].left, mListRectF[i].top,
                            mListRectF[i].right, mListRectF[i].bottom, corner, corner, mPaint)
                    }
                }
            }
        }
    }

    fun drawListFace(list: Array<RectF>) {
        mListCenterCoordinates.clear()
        mListCenterCoordinates.addAll(list)
        getCoordinatesOfFace()
        if (true) {// Cause only mode auto focus to faces
            if(list.isNotEmpty()) {
                alpha = 1f
                isFadeOut = false
                removeCallbacks(runnableFadeOutDelay)
                for (i in 0..list.lastIndex) {
                    animatorFaceDetection = ValueAnimator.ofPropertyValuesHolder(
                        PropertyValuesHolder.ofFloat(mPropertyLeft, mListRectF[i].left,
                            list[i].left),
                        PropertyValuesHolder.ofFloat(mPropertyTop, mListRectF[i].top,
                            list[i].top),
                        PropertyValuesHolder.ofFloat(mPropertyRight, mListRectF[i].right,
                            list[i].right),
                        PropertyValuesHolder.ofFloat(mPropertyBottom, mListRectF[i].bottom,
                            list[i].bottom))
                    animatorFaceDetection?.duration =
                        TIME_DETECT_ANIMATION
                    animatorFaceDetection?.addUpdateListener {
                        mListRectF[i].left = it.getAnimatedValue(mPropertyLeft) as Float
                        mListRectF[i].top = it.getAnimatedValue(mPropertyTop) as Float
                        mListRectF[i].right = it.getAnimatedValue(mPropertyRight) as Float
                        mListRectF[i].bottom = it.getAnimatedValue(mPropertyBottom) as Float

                        if ((it.getAnimatedValue(mPropertyBottom) as Float) == list[i].bottom && alpha == 1f) {
                            postDelayed(runnableFadeOutDelay,
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
                if(mFlagModeTouchFocus){
                    mMode = MODE_AUTO_FACE_DETECT
                    alpha = 1f
                    mFlagModeTouchFocus = false
                }
            }
        }
    }

    fun touchTo(x: Float, y: Float) {
        removeCallbacks(mRunnableChangeMode)
        animate().cancel()
        animatorTouchFocus?.cancel()
        animatorTouchFocus?.removeAllUpdateListeners()
        mTouchX = x
        mTouchY = y
        alpha = 1f
        mFlagFirst = true
        mMode = MODE_TOUCH_FOCUS
        postDelayed(mRunnableChangeMode,
            TIME_DELAY
        )
        animatorTouchFocus = ValueAnimator.ofPropertyValuesHolder(
            PropertyValuesHolder.ofFloat(mPropertyLeft, mTouchX - mSizeRecFBefore,
                mTouchX - mSizeRecFLate),
            PropertyValuesHolder.ofFloat(mPropertyTop, mTouchY - mSizeRecFBefore,
                mTouchY - mSizeRecFLate),
            PropertyValuesHolder.ofFloat(mPropertyRight, mTouchX + mSizeRecFBefore,
                mTouchX + mSizeRecFLate),
            PropertyValuesHolder.ofFloat(mPropertyBottom, mTouchY +
                    mSizeRecFBefore, mTouchY + mSizeRecFLate))
        animatorTouchFocus?.duration =
            TIME_FOCUS_ANIMATION
        animatorTouchFocus?.addUpdateListener {
            mRectFTouch.left = it.getAnimatedValue(mPropertyLeft) as Float
            mRectFTouch.top = it.getAnimatedValue(mPropertyTop) as Float
            mRectFTouch.right = it.getAnimatedValue(mPropertyRight) as Float
            mRectFTouch.bottom = it.getAnimatedValue(mPropertyBottom) as Float
            invalidate()
        }
        animatorTouchFocus?.start()
    }

    fun fadeOut() {
        if (!isFadeOut) {
            mListCenterCoordinates.clear()
            removeCallbacks(runnableFadeOutDelay)
            animate().alpha(0f).setDuration(TIME_FOCUS_ANIMATION).start()
            isFadeOut = true
        }
    }

    private fun getCoordinatesOfFace() {
        for (i in 0..mListCenterCoordinates.lastIndex) {
            if (mListRectF.size < mListCenterCoordinates.size) {
                mListRectF.add(RectF())
            }
        }
    }

}
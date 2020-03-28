package com.vllenin.icamera

import android.util.Log

object DebugLog {

  private var mClassName: String = ""
  private var mMethodName: String = ""
  private var mLineNumber: Int = 0

  private val mIsDebuggable: Boolean
    get() = BuildConfig.DEBUG

  private fun createLog(log: String): String {
    val buffer = StringBuffer()
    buffer.append("[")
    buffer.append(mMethodName)
    buffer.append(":")
    buffer.append(mLineNumber)
    buffer.append("]")
    buffer.append(log)

    return buffer.toString()
  }

  private fun getMethodNames(sElements: Array<StackTraceElement>) {
    mClassName = sElements[1].fileName
    mMethodName = sElements[1].methodName
    mLineNumber = sElements[1].lineNumber
  }

  fun e(message: String) {
    if (!mIsDebuggable)
      return

    // Throwable instance must be created before any methods
    getMethodNames(Throwable().stackTrace)
    Log.e(mClassName, createLog(message))
  }

  fun i(message: String) {
    if (!mIsDebuggable)
      return

    getMethodNames(Throwable().stackTrace)
    Log.i(mClassName, createLog(message))
  }

  fun v(message: String) {
    if (!mIsDebuggable)
      return

    getMethodNames(Throwable().stackTrace)
    Log.v(mClassName, createLog(message))
  }

  fun w(message: String) {
    if (!mIsDebuggable)
      return

    getMethodNames(Throwable().stackTrace)
    Log.w(mClassName, createLog(message))
  }

  fun d(obj: Any) {
    if (!mIsDebuggable)
      return

    getMethodNames(Throwable().stackTrace)
    Log.d(mClassName, createLog(obj.toString()))
  }

  fun tag(tag: String, obj: Any) {
    if (!mIsDebuggable)
      return

    getMethodNames(Throwable().stackTrace)
    Log.d(tag, "[$mClassName]${createLog(obj.toString())}")
  }
}/* Protect from instantiations */
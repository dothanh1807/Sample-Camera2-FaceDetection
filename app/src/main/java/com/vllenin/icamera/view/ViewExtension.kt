package com.vllenin.icamera.view

import android.graphics.SurfaceTexture
import android.view.TextureView

internal fun TextureView.onSurfaceListener(onSurfaceAvailable: (SurfaceTexture) -> Unit) {
    if (isAvailable) {
        onSurfaceAvailable(this@onSurfaceListener.surfaceTexture)
    }

    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            onSurfaceAvailable(surface)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {

        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false
    }
}
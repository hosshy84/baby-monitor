package com.tatsuya.babymonitor.ui.zoom

import android.view.ScaleGestureDetector
import android.view.View

class ZoomScaleGestureListener(private val view: View) : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    private var scaleFactor = 1.0f
    private var focusX = 0f
    private var focusY = 0f

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        focusX = detector.focusX
        focusY = detector.focusY
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor = 1.0f.coerceAtLeast(scaleFactor.coerceAtMost(5.0f))
        view.scaleX = scaleFactor
        view.scaleY = scaleFactor
        view.pivotX = focusX
        view.pivotY = focusY
        return true
    }
}
package com.example.babymonitor.ui.zoom

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class ZoomGestureListener(private val view: View) : GestureDetector.SimpleOnGestureListener() {
    override fun onDoubleTap(e: MotionEvent): Boolean {
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        return true
    }
}
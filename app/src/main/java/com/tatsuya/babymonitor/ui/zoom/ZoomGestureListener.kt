package com.tatsuya.babymonitor.ui.zoom

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class ZoomGestureListener(private val view: View) : GestureDetector.SimpleOnGestureListener() {
    override fun onDoubleTap(e: MotionEvent): Boolean {
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        view.translationX = 0f
        view.translationY = 0f
        return true
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (view.scaleX == 1.0f && view.scaleY == 1.0f) {
            return true
        }

        view.translationX -= distanceX
        view.translationY -= distanceY
        return true
    }
}
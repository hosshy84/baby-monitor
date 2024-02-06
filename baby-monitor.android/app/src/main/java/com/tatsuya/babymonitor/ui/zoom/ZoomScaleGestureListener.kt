package com.tatsuya.babymonitor.ui.zoom

import android.view.ScaleGestureDetector
import android.view.View

class ZoomScaleGestureListener(private val view: View) : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    private var scaleFactor = 1.0f
    private var focusX = 0f
    private var focusY = 0f

    /**
     * スケールの変更を検知した時点で1度だけ発火
     * 倍率変更時の基準となる位置を記憶
     */
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        focusX = detector.focusX
        focusY = detector.focusY
        return true
    }

    /**
     * スケールの変更が行われている間定期的に発火
     * 倍率の指定と位置の固定（デフォルト値から変更しない）を行う
     */
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
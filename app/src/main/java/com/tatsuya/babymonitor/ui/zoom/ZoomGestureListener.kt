package com.tatsuya.babymonitor.ui.zoom

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class ZoomGestureListener(private val view: View) : GestureDetector.SimpleOnGestureListener() {
    /**
     * タッチの検知開始イベント（何もしない）
     */
    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    /**
     * ダブルタップを検知して、倍率と位置をリセットする
     */
    override fun onDoubleTap(e: MotionEvent): Boolean {
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        view.translationX = 0f
        view.translationY = 0f
        return true
    }

    /**
     * ドラッグ（スクロール）を検知して、表示位置を変更する
     * ただし、倍率が等倍以下の場合は移動させない
     */
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (view.scaleX <= 1.0f && view.scaleY <= 1.0f) {
            return true
        }

        view.translationX -= distanceX
        view.translationY -= distanceY
        return true
    }
}
package com.swiftxzn.veyra

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ScrollService : AccessibilityService() {

    companion object {
        var instance: ScrollService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ScrollService", "Serviço conectado!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Não precisamos usar eventos por enquanto
    }

    override fun onInterrupt() {
        // Não precisa fazer nada
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun scrollDown() {
        performScroll(true)
    }

    fun scrollUp() {
        performScroll(false)
    }

    private fun performScroll(isDown: Boolean) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val path = Path()
        if (isDown) {
            // Scroll para baixo (próximo vídeo)
            path.moveTo(width / 2, height * 0.7f)
            path.lineTo(width / 2, height * 0.3f)
        } else {
            // Scroll para cima (vídeo anterior)
            path.moveTo(width / 2, height * 0.3f)
            path.lineTo(width / 2, height * 0.7f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("ScrollService", "Scroll realizado")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d("ScrollService", "Scroll cancelado")
            }
        }, null)
    }
}
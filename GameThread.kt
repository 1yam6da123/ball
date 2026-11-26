package com.lyambda.ball

import android.view.SurfaceHolder

class GameThread(private val holder: SurfaceHolder, private val view: GameView) : Thread() {
    var running = false

    override fun run() {
        var lastTime = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000_000f
            lastTime = now

            // Обновляем логику (защита от сильных лагов)
            view.step(dt.coerceAtMost(0.05f))

            // Рисуем кадр
            val canvas = holder.lockCanvas() ?: continue
            try {
                view.drawGame(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
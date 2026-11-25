package com.lyambda.ball

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, SensorEventListener {
    private var thread: GameThread? = null
    private val safe = Rect()
    val game = Game()

    private val leaderboard = LeaderboardManager()
    private var leaderboardRequestId = 0
    private val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    var onPickImageRequested: ((Boolean) -> Unit)? = null
    var onShowKeyboard: (() -> Unit)? = null
    var onHideKeyboard: (() -> Unit)? = null
    var isPickingBackground = true
    var onExitRequested: (() -> Unit)? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialDistance = 0f
    private var initialScale = 1f

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    init {
        game.language = prefs.getString("ui_language", "ru").let { if (it == "en") "en" else "ru" }
        holder.addCallback(this)

        var playerId = prefs.getString("player_id", "") ?: ""
        if (playerId.isEmpty()) {
            playerId = UUID.randomUUID().toString()
            prefs.edit().putString("player_id", playerId).apply()
        }
        game.playerId = playerId

        game.playerName = prefs.getString("player_name", "") ?: ""

        val hasName = prefs.getBoolean("has_name", false)
        if (!hasName) {
            game.playerName = ""
            game.previousStateForName = Game.State.NAME_ENTRY
            game.state = Game.State.NAME_ENTRY
        } else {
            game.state = Game.State.MENU
        }

        leaderboard.signInAnonymously { }

        game.currentMode = prefs.getInt("current_mode", 0)
        game.isTiltMode = prefs.getBoolean("is_tilt_mode", false)
        game.totalOrbs = prefs.getInt("total_orbs", 0)

        game.particleColor1 = prefs.getInt("color1", Color.WHITE)
        game.particleColor2 = prefs.getInt("color2", Color.parseColor("#88CCFF"))
        game.hue1 = prefs.getFloat("hue1", 0f)
        game.hue2 = prefs.getFloat("hue2", 200f)

        loadHighScore()
        loadSavedImages()

        val ids = listOf(
            "mode_tilt_master", "mode_tilt_nohands",
            "mode_hearts_survivor", "mode_hearts_risky", "mode_hearts_secondwind",
            "global_customizer", "global_individual", "global_explorer", "global_collector"
        )
        val titles = listOf(
            "Мастер наклона", "Без рук",
            "Живучий", "На волоске", "Второе дыхание",
            "Кастомизатор", "Индивидуальность", "Исследователь", "Коллекционер"
        )
        val descs = listOf(
            "Набрать 50 очков в режиме наклона", "Набрать 20 очков без касаний экрана",
            "Набрать 50 очков, сохранив 3 сердечка", "Набрать 50 очков с 1 сердечком", "Побить рекорд, потеряв 2 жизни",
            "Загрузить свой фон", "Загрузить свой мяч", "Сыграть во все 4 режима", "Открыть все остальные достижения"
        )

        for (i in ids.indices) {
            game.achievementsList.add(Achievement(ids[i], titles[i], descs[i], prefs.getBoolean("ach_${ids[i]}_v3", false)))
        }

        game.saveAchievementCallback = { id ->
            prefs.edit().putBoolean("ach_${id}_v3", true).apply()
            checkGlobalAchievements()
        }

        game.saveOrbsCallback = { orbs ->
            prefs.edit().putInt("total_orbs", orbs).apply()
        }

        game.saveColorsCallback = { c1, c2, h1, h2 ->
            prefs.edit().putInt("color1", c1).putInt("color2", c2).putFloat("hue1", h1).putFloat("hue2", h2).apply()
        }
    }

    private fun checkGlobalAchievements() {
        val playedClassic = prefs.getBoolean("played_mode_0", false)
        val playedTilt = prefs.getBoolean("played_mode_1", false)
        val playedHearts = prefs.getBoolean("played_mode_2", false)
        val playedMultiBall = prefs.getBoolean("played_mode_3", false)
        if (playedClassic && playedTilt && playedHearts && playedMultiBall) {
            game.unlock("global_explorer", "Исследователь", "Сыграть во все 4 режима")
        }

        val allOthers = game.achievementsList.filter { it.id != "global_collector" }
        if (allOthers.all { it.isUnlocked }) {
            game.unlock("global_collector", "Коллекционер", "Открыть все остальные достижения")
        }
    }

    private fun loadHighScore() {
        game.highScore = when (game.currentMode) {
            0 -> prefs.getInt("high_classic", 0)
            1 -> prefs.getInt("high_tilt", 0)
            2 -> prefs.getInt("high_hearts", 0)
            else -> prefs.getInt("high_multiball", 0)
        }
    }

    private fun saveHighScore() {
        if (game.score > game.highScore) {
            game.highScore = game.score
            val key = when (game.currentMode) {
                0 -> "high_classic"
                1 -> "high_tilt"
                2 -> "high_hearts"
                else -> "high_multiball"
            }
            prefs.edit().putInt(key, game.highScore).apply()
        }

        val modeStr = when (game.currentMode) {
            0 -> "Classic"
            1 -> "Tilt"
            2 -> "Hearts"
            else -> "MultiBall"
        }
        leaderboard.submitScore(game.playerId, game.playerName, game.score, modeStr) { _, _ -> }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val deltaX = abs(x - lastX)
            val deltaY = abs(y - lastY)
            val deltaZ = abs(z - lastZ)
            val shakeDelta = deltaX + deltaY + deltaZ

            if (shakeDelta > 5.0f) {
                game.shakeIntensity = shakeDelta.coerceAtMost(30f)
            }

            lastX = x
            lastY = y
            lastZ = z

            var tilt = -x
            game.tiltX = game.tiltX * 0.8f + tilt * 0.2f
            game.tiltX = (game.tiltX / 9.8f).coerceIn(-1f, 1f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread = GameThread(holder, this).also { it.running = true; it.start() }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.let { it.running = false; it.join() }
        sensorManager.unregisterListener(this)
    }

    private fun loadSavedImages() {
        try {
            val bgFile = File(context.filesDir, "bg.png")
            if (bgFile.exists()) game.bgImage = BitmapFactory.decodeFile(bgFile.absolutePath)
            val ballFile = File(context.filesDir, "ball.png")
            if (ballFile.exists()) game.ballImage = BitmapFactory.decodeFile(ballFile.absolutePath)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap, fileName: String) {
        Thread {
            try {
                val fileOutputStream = FileOutputStream(File(context.filesDir, fileName))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                fileOutputStream.close()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    fun setSafeArea(left: Int, top: Int, right: Int, bottom: Int) {
        safe.set(left, top, right, bottom)
    }

    fun setBackgroundImage(bitmap: Bitmap) {
        game.tempBgImage = bitmap
        game.bgScale = 1f
        game.bgImageOffsetX = 0f
        game.bgImageOffsetY = 0f
        game.state = Game.State.CROPPING_BG
    }

    fun setBallCropImage(bitmap: Bitmap) {
        game.tempCropImage = bitmap
        game.cropScale = 1f
        game.cropImageOffsetX = 0f
        game.cropImageOffsetY = 0f
        game.state = Game.State.CROPPING_BALL
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (game.state == Game.State.NAME_ENTRY) {
            if (e.action == MotionEvent.ACTION_DOWN) {
                if (game.nameInputRect.contains(e.x, e.y)) {
                    game.errorMessage = ""
                    onShowKeyboard?.invoke()
                } else if (game.nameAcceptBtnRect.contains(e.x, e.y)) {
                    val newName = game.playerName.trim()
                    if (newName.isBlank()) {
                        game.errorMessage = game.t("Введите ник!")
                    } else {
                        game.errorMessage = "" // Сбрасываем ошибку перед проверкой
                        onHideKeyboard?.invoke()
                        val oldName = prefs.getString("player_name", "") ?: ""
                        leaderboard.checkAndSaveNickname(game.playerId, oldName, newName) { success, error ->
                            post {
                                if (success) {
                                    game.playerName = newName
                                    prefs.edit().putString("player_name", game.playerName).putBoolean("has_name", true).apply()
                                    game.openMenu(game.previousStateForName)
                                } else {
                                    // Не сохраняем занятый ник; оставляем его в поле для исправления
                                    game.playerName = newName
                                    game.errorMessage = game.t(error ?: "Ошибка проверки ника")
                                }
                            }
                        }
                    }
                } else if (game.nameDeclineBtnRect.contains(e.x, e.y)) {
                    if (game.previousStateForName == Game.State.SETTINGS) {
                        game.playerName = prefs.getString("player_name", "") ?: ""
                        game.errorMessage = ""
                        onHideKeyboard?.invoke()
                        game.openMenu(Game.State.SETTINGS)
                    } else {
                        onHideKeyboard?.invoke()
                        game.showConfirmation("Остаться анонимным? Будет присвоен случайный ник.") {
                            val randomSuffix = (1000..9999).random()
                            val anonName = "Отказался_$randomSuffix"
                            game.playerName = anonName
                            prefs.edit().putString("player_name", game.playerName).putBoolean("has_name", true).apply()
                            leaderboard.checkAndSaveNickname(game.playerId, "", game.playerName) { _, _ -> }
                            game.openMenu(Game.State.MENU)
                        }
                    }
                }
            }
            return true
        }

        if (game.showConfirmDialog) {
            if (e.action == MotionEvent.ACTION_DOWN) {
                if (game.confirmYesBtnRect.contains(e.x - width/2f, e.y - height/2f)) {
                    game.showConfirmDialog = false
                    game.pendingAction?.invoke()
                    game.pendingAction = null
                } else if (game.confirmNoBtnRect.contains(e.x - width/2f, e.y - height/2f)) {
                    game.showConfirmDialog = false
                    game.pendingAction = null
                }
            }
            return true
        }

        if (game.showAchievementPopup && game.popupRect.contains(e.x, e.y)) {
            val elapsed = System.currentTimeMillis() - game.popupTriggerTime
            if (e.action == MotionEvent.ACTION_DOWN && elapsed in 500..4500) {
                game.showAchievementPopup = false
                val targetState = if (game.state == Game.State.PLAYING) Game.State.PAUSED else game.state
                game.previousState = targetState
                game.openMenu(Game.State.ACHIEVEMENTS)
                return true
            }
        }

        if (game.isClosingMenu) return true

        if (game.state == Game.State.CROPPING_BG) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = e.x; lastTouchY = e.y }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (e.pointerCount >= 2) {
                        initialDistance = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                        initialScale = game.bgScale
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (e.pointerCount == 1) {
                        game.bgImageOffsetX += e.x - lastTouchX
                        game.bgImageOffsetY += e.y - lastTouchY
                        lastTouchX = e.x; lastTouchY = e.y
                    } else if (e.pointerCount >= 2) {
                        val currentDistance = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                        if (initialDistance > 10f) {
                            game.bgScale = (initialScale * (currentDistance / initialDistance)).coerceIn(0.5f, 5f)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (game.saveBgCropBtnRect.contains(e.x, e.y)) {
                        val img = game.tempBgImage
                        if (img != null) {
                            val w = if (game.viewWidth > 0) game.viewWidth else width
                            val h = if (game.viewHeight > 0) game.viewHeight else height

                            val finalBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val finalCanvas = Canvas(finalBitmap)
                            finalCanvas.drawColor(android.graphics.Color.parseColor("#121212"))

                            val savePaint = Paint().apply { isFilterBitmap = true }

                            finalCanvas.save()
                            finalCanvas.translate(w / 2f + game.bgImageOffsetX, h / 2f + game.bgImageOffsetY)
                            finalCanvas.scale(game.bgScale, game.bgScale)
                            val imgRect = RectF(-img.width / 2f, -img.height / 2f, img.width / 2f, img.height / 2f)
                            finalCanvas.drawBitmap(img, null, imgRect, savePaint)
                            finalCanvas.restore()

                            game.bgImage = finalBitmap
                            saveImageToInternalStorage(finalBitmap, "bg.png")
                            game.unlock("global_customizer", "Кастомизатор", "Загрузить свой фон")

                            game.tempBgImage = null
                            game.openMenu(game.previousState)
                        }
                    }
                }
            }
            return true
        }

        if (game.state == Game.State.CROPPING_BALL) {
            val cx = width / 2f
            val cy = height / 2f
            val circleRadius = width * 0.35f
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = e.x; lastTouchY = e.y }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (e.pointerCount >= 2) {
                        initialDistance = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                        initialScale = game.cropScale
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (e.pointerCount == 1) {
                        game.cropImageOffsetX += e.x - lastTouchX
                        game.cropImageOffsetY += e.y - lastTouchY
                        lastTouchX = e.x; lastTouchY = e.y
                    } else if (e.pointerCount >= 2) {
                        val currentDistance = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                        if (initialDistance > 10f) {
                            game.cropScale = (initialScale * (currentDistance / initialDistance)).coerceIn(0.5f, 5f)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (game.saveCropBtnRect.contains(e.x, e.y)) {
                        val img = game.tempCropImage
                        if (img != null) {
                            val scale = game.cropScale
                            val imgCenterX = (img.width / 2f) - (game.cropImageOffsetX / scale)
                            val imgCenterY = (img.height / 2f) - (game.cropImageOffsetY / scale)
                            val cropRadiusInImg = (circleRadius / scale)
                            val cropSize = (cropRadiusInImg * 2).toInt().coerceAtLeast(10)
                            val cropX = (imgCenterX - cropRadiusInImg).toInt().coerceIn(0, img.width - cropSize)
                            val cropY = (imgCenterY - cropRadiusInImg).toInt().coerceIn(0, img.height - cropSize)

                            val cropped = Bitmap.createBitmap(img, cropX, cropY, cropSize, cropSize)
                            game.ballImage = cropped
                            saveImageToInternalStorage(cropped, "ball.png")

                            game.unlock("global_individual", "Индивидуальность", "Загрузить свой мяч")

                            game.tempCropImage = null
                            game.openMenu(game.previousState)
                        }
                    }
                }
            }
            return true
        }

        if (e.action == MotionEvent.ACTION_DOWN) {
            if (game.state == Game.State.GAME_OVER) {
                if (game.restartBtnRect.contains(e.x, e.y)) game.reset(width, safe)
                return true
            }

            if (game.state == Game.State.MENU) {
                if (game.helpBtnRect.contains(e.x, e.y)) {
                    game.isMenuPhysicsEnabled = !game.isMenuPhysicsEnabled
                    if (game.isMenuPhysicsEnabled) {
                        game.fallingButtons.clear()
                        val btnW = 550f; val btnH = 75f
                        val left = width / 2f - btnW / 2f

                        val cPrimary = android.graphics.Color.parseColor("#2979FF")
                        val cSecondary = android.graphics.Color.parseColor("#2A2A2A")
                        val cDanger = android.graphics.Color.parseColor("#EF5350")

                        game.fallingButtons.add(FallingButton(left, safe.top + 480f, btnW, btnH, "Играть", cPrimary, Game.State.PLAYING))
                        game.fallingButtons.add(FallingButton(left, safe.top + 570f, btnW, btnH, "Режимы", cSecondary, Game.State.MODES))
                        game.fallingButtons.add(FallingButton(left, safe.top + 660f, btnW, btnH, "Рейтинг", cSecondary, Game.State.LEADERBOARD))
                        game.fallingButtons.add(FallingButton(left, safe.top + 750f, btnW, btnH, "Настройки", cSecondary, Game.State.SETTINGS))
                        game.fallingButtons.add(FallingButton(left, safe.top + 840f, btnW, btnH, "Достижения", cSecondary, Game.State.ACHIEVEMENTS))
                        game.fallingButtons.add(FallingButton(left, safe.top + 930f, btnW, btnH, "Выход", cDanger, Game.State.PLAYING))
                    }
                    return true
                }

                if (game.isMenuPhysicsEnabled) {
                    for (btn in game.fallingButtons) {
                        val hitRect = RectF(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h)
                        if (hitRect.contains(e.x, e.y)) {
                            game.previousState = Game.State.MENU
                            when (btn.targetState) {
                                Game.State.PLAYING -> if (btn.text == "Играть") game.closeMenu(Game.State.PLAYING, restart = true) else game.showConfirmation("Выйти из игры?") { onExitRequested?.invoke() }
                                Game.State.MODES -> game.closeMenu(Game.State.MODES)
                                Game.State.LEADERBOARD -> {
                                    game.closeMenu(Game.State.LEADERBOARD_MODES)
                                }
                                Game.State.SETTINGS -> game.closeMenu(Game.State.SETTINGS)
                                Game.State.ACHIEVEMENTS -> game.closeMenu(Game.State.ACHIEVEMENTS)
                                else -> {}
                            }
                            game.isMenuPhysicsEnabled = false
                            break
                        }
                    }
                    return true
                }

                when {
                    game.btnMenuPlay.contains(e.x, e.y) -> game.closeMenu(Game.State.PLAYING, restart = true)
                    game.btnMenuModes.contains(e.x, e.y) -> {
                        game.previousState = Game.State.MENU
                        game.closeMenu(Game.State.MODES)
                    }
                    game.btnMenuLeaderboard.contains(e.x, e.y) -> {
                        game.closeMenu(Game.State.LEADERBOARD_MODES)
                    }
                    game.btnMenuSettings.contains(e.x, e.y) -> {
                        game.previousState = Game.State.MENU
                        game.closeMenu(Game.State.SETTINGS)
                    }
                    game.btnMenuAch.contains(e.x, e.y) -> {
                        game.previousState = Game.State.MENU
                        game.closeMenu(Game.State.ACHIEVEMENTS)
                    }
                    game.btnMenuExit.contains(e.x, e.y) -> {
                        game.showConfirmation("Выйти из игры?") { onExitRequested?.invoke() }
                    }
                }
                return true
            }

            if (game.state == Game.State.LEADERBOARD_MODES) {
                if (game.isClosingMenu || game.menuAnimProgress < 0.95f) return true
                if (game.leaderboardModeBackBtn.contains(e.x, e.y)) {
                    game.closeMenu(Game.State.MENU)
                    return true
                }
                for (mode in 0..3) {
                    if (game.leaderboardModeButtons[mode].contains(e.x, e.y)) {
                        game.leaderboardMode = mode
                        game.leaderboardEntries.clear()
                        game.isLeaderboardLoading = true
                        val modeStr = arrayOf("Classic", "Tilt", "Hearts", "MultiBall")[mode]
                        val requestId = ++leaderboardRequestId
                        leaderboard.fetchTopScores(modeStr) { list, error ->
                            post {
                                if (requestId == leaderboardRequestId && game.leaderboardMode == mode) {
                                    game.leaderboardEntries.clear()
                                    game.leaderboardEntries.addAll(list)
                                    game.isLeaderboardLoading = false
                                    if (error != null) {
                                        Toast.makeText(context, game.t("Ошибка чтения: ") + error, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        game.closeMenu(Game.State.LEADERBOARD)
                        return true
                    }
                }
                return true
            }

            if (game.state == Game.State.LEADERBOARD) {
                if (!game.isClosingMenu && game.menuAnimProgress >= 0.95f && game.closeLeaderboardBtnRect.contains(e.x, e.y)) {
                    leaderboardRequestId++ // Игнорируем устаревшие ответы Firestore
                    game.closeMenu(Game.State.LEADERBOARD_MODES)
                }
                return true
            }

            if (game.state == Game.State.MODES) {
                when {
                    game.btnModeClassic.contains(e.x, e.y) -> {
                        game.currentMode = 0
                        game.isTiltMode = false
                        prefs.edit().putInt("current_mode", 0).putBoolean("is_tilt_mode", false).putBoolean("played_mode_0", true).apply()
                        loadHighScore()
                        checkGlobalAchievements()
                        game.closeMenu(Game.State.PLAYING, restart = true)
                    }
                    game.btnModeTilt.contains(e.x, e.y) -> {
                        game.currentMode = 1
                        game.isTiltMode = true
                        prefs.edit().putInt("current_mode", 1).putBoolean("is_tilt_mode", true).putBoolean("played_mode_1", true).apply()
                        loadHighScore()
                        checkGlobalAchievements()
                        game.closeMenu(Game.State.PLAYING, restart = true)
                    }
                    game.btnModeHearts.contains(e.x, e.y) -> {
                        game.currentMode = 2
                        game.isTiltMode = false
                        prefs.edit().putInt("current_mode", 2).putBoolean("is_tilt_mode", false).putBoolean("played_mode_2", true).apply()
                        loadHighScore()
                        checkGlobalAchievements()
                        game.closeMenu(Game.State.PLAYING, restart = true)
                    }
                    game.btnModeNew.contains(e.x, e.y) -> {
                        game.currentMode = 3
                        game.isTiltMode = false
                        prefs.edit().putInt("current_mode", 3)
                            .putBoolean("is_tilt_mode", false)
                            .putBoolean("played_mode_3", true).apply()
                        loadHighScore()
                        checkGlobalAchievements()
                        game.closeMenu(Game.State.PLAYING, restart = true)
                    }
                    game.btnModeBack.contains(e.x, e.y) -> game.closeMenu(game.previousState)
                }
                return true
            }

            if (game.state == Game.State.PAUSED) {
                when {
                    game.pauseResumeBtn.contains(e.x, e.y) -> game.closeMenu(Game.State.PLAYING)
                    game.pauseModesBtn.contains(e.x, e.y) -> {
                        game.previousState = Game.State.PAUSED
                        game.closeMenu(Game.State.MODES)
                    }
                    game.pauseSettingsBtn.contains(e.x, e.y) -> {
                        game.previousState = Game.State.PAUSED
                        game.closeMenu(Game.State.SETTINGS)
                    }
                    game.pauseAchBtn.contains(e.x, e.y) -> {
                        game.previousState = Game.State.PAUSED
                        game.closeMenu(Game.State.ACHIEVEMENTS)
                    }
                    game.pauseMainMenuBtn.contains(e.x, e.y) -> {
                        game.showConfirmation("Выйти в главное меню?") { game.closeMenu(Game.State.MENU) }
                    }
                }
                return true
            }

            if (game.state == Game.State.ACHIEVEMENTS) {
                if (game.closeAchievementsBtnRect.contains(e.x, e.y)) {
                    game.closeMenu(game.previousState)
                }
                if (game.resetAchievementsBtnRect.contains(e.x, e.y)) {
                    game.showConfirmation("Сбросить достижения?") {
                        prefs.edit().apply {
                            for (ach in game.achievementsList) {
                                putBoolean("ach_${ach.id}_v3", false)
                            }
                            putBoolean("played_mode_0", false)
                            putBoolean("played_mode_1", false)
                            putBoolean("played_mode_2", false)
                            putBoolean("played_mode_3", false)
                            apply()
                        }
                        for (ach in game.achievementsList) ach.isUnlocked = false
                    }
                }
                return true
            }

            if (game.state == Game.State.SETTINGS) {
                when {
                    game.btnSettingsChangeNick.contains(e.x, e.y) -> {
                        game.previousStateForName = Game.State.SETTINGS
                        game.openMenu(Game.State.NAME_ENTRY)
                        onShowKeyboard?.invoke()
                    }
                    game.bgBtnRect.contains(e.x, e.y) -> onPickImageRequested?.invoke(true)
                    game.resetBgBtnRect.contains(e.x, e.y) -> {
                        game.showConfirmation("Сбросить фон?") {
                            game.bgImage = null
                            File(context.filesDir, "bg.png").delete()
                        }
                    }
                    game.ballBtnRect.contains(e.x, e.y) -> onPickImageRequested?.invoke(false)
                    game.resetBallBtnRect.contains(e.x, e.y) -> {
                        game.showConfirmation("Сбросить мяч?") {
                            game.ballImage = null
                            File(context.filesDir, "ball.png").delete()
                        }
                    }
                    game.colorsBtnRect.contains(e.x, e.y) -> {
                        game.openMenu(Game.State.PARTICLE_COLORS)
                    }
                    game.languageBtnRect.contains(e.x, e.y) -> {
                        game.language = if (game.language == "ru") "en" else "ru"
                        game.animateLanguageSwitch()
                        prefs.edit().putString("ui_language", game.language).apply()
                    }
                    game.backBtnRect.contains(e.x, e.y) -> game.closeMenu(game.previousState)
                }
                return true
            }

            if (game.state == Game.State.PARTICLE_COLORS) {
                when {
                    game.color1BoxRect.contains(e.x, e.y) -> game.editingColor1 = true
                    game.color2BoxRect.contains(e.x, e.y) -> game.editingColor1 = false
                    game.hueSliderRect.contains(e.x, e.y) -> {
                        val percent = ((e.x - game.hueSliderRect.left) / game.hueSliderRect.width()).coerceIn(0f, 1f)
                        val hue = percent * 360f

                        if (game.editingColor1) {
                            game.hue1 = hue
                            game.particleColor1 = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                        } else {
                            game.hue2 = hue
                            game.particleColor2 = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                        }
                        game.saveColorsCallback?.invoke(game.particleColor1, game.particleColor2, game.hue1, game.hue2)
                    }
                    game.resetColorsBtnRect.contains(e.x, e.y) -> {
                        game.showConfirmation("Сбросить цвета?") {
                            game.particleColor1 = Цвет.БЕЛЫЙ
                            game.particleColor2 = Color.parseColor("#88CCFF")
                            игра.оттенок1 = 0ф
                            игра.оттенок2 = 200ф
                            игра.редактированиеЦвет1 = истинный
                            game.saveColorsCallback?.invoke(game.particleColor1, game.particleColor2, game.hue1, game.hue2)
                        }
                    }
                    game.backFromColorsBtnRect.contains(ex, ey) -> game.closeMenu(Game.State.SETTINGS)
                }
                возвращаться истинный
            }

            эсли (game.pauseBtnRect.contains(ex, ey)) {
                game.openMenu(Состоение.Игры.ПРИОСТАНОВЛЕНО)
                возвращаться истинный
            }
        }

        эсли (game.state == Game.State.PLAYING) {
            эсли (game.currentMode == 1) {
                game.hasTouchedInTiltMode = игровой
            }

            если (!game.isTiltMode) {
                эсли (e.action == MotionEvent.ACTION_MOVE || e.action == MotionEvent.ACTION_DOWN) {
                    game.paddleX = excoerceIn(
                        safe.left + game.paddleW/ 2,
                        ширина - safe.right - game.paddleW / 2
                    )
                }
            }
        }
        возвращаться истинный
    }

    веселье шаг(dt: Плавающий) {
        вал токащийPaddleY = высота - безпасное.дно - 100ф
        game.update(dt, safe, width, currentPaddleY)

        эсли (game.vibrateTrigger) {
            game.vibrateTrigger = ложный
            эсли (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                вибратор?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } еще {
                @Подавить("УСТАРЕВАНИЕ")
                вибратор?.вибрировать(30)
            }
        }

        эсли (game.state == Game.State.GAME_OVER) {
            сохранитьВысокийСчет()
        }
    }

    веселье ничьяИгра(c: Холсst) {
        game.render(c, безопасно)
    }
}

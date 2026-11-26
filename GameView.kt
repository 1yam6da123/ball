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
import android.os.Handler
import android.os.Looper
import java.util.UUID
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.abs
import kotlin.math.hypot

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, SensorEventListener {
    private var thread: GameThread? = null
    private val safe = Rect()
    val game = Game()

    private val leaderboard = LeaderboardManager()
    private var leaderboardRequestId = 0
    private val devicePrefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
    private val uid = requireNotNull(FirebaseAuth.getInstance().currentUser).uid
    private val prefs = context.getSharedPreferences("GameProfile_$uid", Context.MODE_PRIVATE)
    private val progress = ProgressManager()
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressReady = false
    private val scoresInFlight = mutableSetOf<Int>()
    private var progressInFlight = false
    private var progressWarned = false
    private var progressAttached = false
    private val progressSoon = Runnable { syncProgress() }
    private val progressPeriodic = object : Runnable {
        override fun run() {
            if (!progressAttached) return
            syncProgress()
            progressHandler.postDelayed(this, 30_000L)
        }
    }
    private var scoreSavedForRound = false
    private var profileLoading = false
    private var nicknameSaving = false
    private var legacyNotice = false
    var onAccountAction: ((String) -> Unit)? = null

    private fun initProgressStorage() {
        if (!prefs.contains("progress_device")) {
            prefs.edit().putString("progress_device", UUID.randomUUID().toString())
                .putInt("progress_base_orbs", prefs.getInt("total_orbs", 0).coerceAtLeast(0))
                .putLong("progress_earned", 0L).commit()
        }
    }

    private fun progressChanged() {
        progressHandler.removeCallbacks(progressSoon)
        progressHandler.postDelayed(progressSoon, 1_000L)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        progressAttached = true
        progressHandler.removeCallbacks(progressPeriodic)
        progressHandler.post(progressPeriodic)
    }

    override fun onDetachedFromWindow() {
        progressAttached = false
        progressHandler.removeCallbacks(progressPeriodic)
        progressHandler.removeCallbacks(progressSoon)
        super.onDetachedFromWindow()
    }

    @Synchronized
    fun syncProgress() {
        if (!progressAttached || !progressReady || windowVisibility != android.view.View.VISIBLE || FirebaseAuth.getInstance().currentUser?.uid != uid) return
        sendPendingScores()
        if (progressInFlight) return
        val snapshot = ProgressSnapshot(
            prefs.getString("progress_device", "").orEmpty(),
            prefs.getInt("progress_base_orbs", 0), prefs.getLong("progress_earned", 0L),
            progressScoreKeys.map { prefs.getInt(it, 0).coerceAtLeast(0) },
            progressAchievementIds.filter { prefs.getBoolean("ach_${it}_v3", false) }.toSet(),
            (0..3).filter { prefs.getBoolean("played_mode_$it", false) }.map { it.toString() }.toSet(),
            prefs.getString("progress_epoch", "").orEmpty(), prefs.getString("progress_reset_pending", "").orEmpty()
        )
        if (snapshot.device.isBlank()) return
        progressInFlight = true
        progress.sync(uid, snapshot) { result ->
            synchronized(this) {
                progressInFlight = false
                if (!progressAttached || FirebaseAuth.getInstance().currentUser?.uid != uid) return@synchronized
                if (result == null) {
                    if (!progressWarned) {
                        progressWarned = true
                        Toast.makeText(context, if (game.language == "en") "Cloud sync unavailable. Progress kept on this device." else "Облако недоступно. Прогресс сохранён на устройстве.", Toast.LENGTH_SHORT).show()
                    }
                    return@synchronized
                }
                progressWarned = false
                val earnedNow = prefs.getLong("progress_earned", 0L)
                val pending = prefs.getString("progress_reset_pending", "").orEmpty()
                val knownEpoch = prefs.getString("progress_epoch", "").orEmpty()
                val newResetQueued = pending != snapshot.pendingReset
                val editor = prefs.edit()
                val orbs = (result.orbs.toLong() + (earnedNow - result.acknowledgedEarned).coerceIn(0L, Int.MAX_VALUE.toLong())).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                editor.putInt("total_orbs", orbs).putLong("progress_earned", maxOf(earnedNow, result.acknowledgedEarned))
                for ((i, key) in progressScoreKeys.withIndex()) {
                    editor.putInt(key, maxOf(prefs.getInt(key, 0), result.scores[i]))
                }
                if (!newResetQueued) {
                    val mergeNow = knownEpoch == result.epoch || (pending.isNotEmpty() && pending == result.epoch)
                    for (ach in game.achievementsList) {
                        val unlocked = ach.id in result.achievements || (mergeNow && prefs.getBoolean("ach_${ach.id}_v3", false))
                        editor.putBoolean("ach_${ach.id}_v3", unlocked)
                        ach.isUnlocked = unlocked
                    }
                    for (i in 0..3) {
                        editor.putBoolean("played_mode_$i", i.toString() in result.played || (mergeNow && prefs.getBoolean("played_mode_$i", false)))
                    }
                    editor.putString("progress_epoch", result.epoch)
                    if (pending.isNotEmpty() && pending == result.acknowledgedReset) editor.remove("progress_reset_pending")
                }
                editor.apply()
                game.totalOrbs = orbs
                loadHighScore()
                checkGlobalAchievements()
                if (newResetQueued || earnedNow > result.acknowledgedEarned) progressChanged()
            }
        }
    }

    private fun migrateLocalProgress() {
        if (devicePrefs.getBoolean("account_migration_done", false)) return
        // Preserve old device progress locally, never transfer ownership of old server documents.
        if (FirebaseAuth.getInstance().currentUser?.isAnonymous == true &&
            devicePrefs.getString("player_id", "").orEmpty().isNotEmpty()) {
            val editor = prefs.edit()
            for ((key, value) in devicePrefs.all) {
                if (key.startsWith("high_") || key.startsWith("ach_") || key.startsWith("played_mode_") || key == "total_orbs") {
                    when (value) {
                        is Int -> editor.putInt(key, maxOf(prefs.getInt(key, 0), value))
                        is Boolean -> editor.putBoolean(key, prefs.getBoolean(key, false) || value)
                    }
                }
            }
            if (!editor.commit()) return
            legacyNotice = true
        }
        devicePrefs.edit().putBoolean("account_migration_done", true).commit()
    }

    fun loadAccountProfile() {
        if (profileLoading) return
        profileLoading = true
        game.accountLoadError = false
        game.openMenu(Game.State.AUTH_LOADING)
        leaderboard.loadProfile { name, scores, error ->
            post {
                profileLoading = false
                if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@post
                game.accountIsGuest = FirebaseAuth.getInstance().currentUser?.isAnonymous == true
                if (error != null) {
                    // Only this UID's previously confirmed nickname is eligible for offline use.
                    val cached = prefs.getString("player_name", "").orEmpty()
                    if (cached.isEmpty()) {
                        game.accountLoadError = true
                        return@post
                    }
                    game.playerName = cached
                } else {
                    game.playerName = name.orEmpty()
                    val editor = prefs.edit().putString("player_name", game.playerName)
                    for ((i, key) in listOf("high_classic", "high_tilt", "high_hearts", "high_multiball").withIndex()) {
                        editor.putInt(key, maxOf(prefs.getInt(key, 0), scores.getOrElse(i) { 0 }))
                    }
                    editor.apply()
                    loadHighScore()
                }
                progressReady = true
                syncProgress()
                game.previousStateForName = Game.State.MENU
                game.openMenu(if (game.playerName.isBlank()) Game.State.NAME_ENTRY else Game.State.MENU)
                if (legacyNotice) {
                    legacyNotice = false
                    Toast.makeText(context, if (game.language == "en") "Old scores remain in the leaderboard. Choose a new nickname; local progress is preserved." else "Старые рекорды остались в рейтинге. Выберите новый ник; локальный прогресс сохранён.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openAccount(from: Game.State) {
        onHideKeyboard?.invoke()
        game.accountReturnState = from
        game.accountIsGuest = FirebaseAuth.getInstance().currentUser?.isAnonymous == true
        game.openMenu(Game.State.ACCOUNT)
    }

    private fun saveNickname(name: String, random: Boolean = false) {
        if (nicknameSaving) return
        nicknameSaving = true
        val old = prefs.getString("player_name", "").orEmpty()
        leaderboard.checkAndSaveNickname(uid, old, name) { success, error ->
            post {
                nicknameSaving = false
                if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@post
                if (success) {
                    game.playerName = name.trim()
                    prefs.edit().putString("player_name", game.playerName).apply()
                    game.openMenu(game.previousStateForName)
                    progressChanged()
                } else {
                    if (random) game.playerName = ""
                    game.errorMessage = error ?: "Не удалось сохранить ник"
                }
            }
        }
    }

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
        game.language = devicePrefs.getString("ui_language", "en").let { if (it == "ru") "ru" else "en" }
        holder.addCallback(this)

        migrateLocalProgress()
        initProgressStorage()
        game.playerId = uid
        game.playerName = prefs.getString("player_name", "").orEmpty()
        game.previousStateForName = Game.State.MENU
        game.accountIsGuest = FirebaseAuth.getInstance().currentUser?.isAnonymous == true
        game.state = Game.State.AUTH_LOADING

        game.currentMode = devicePrefs.getInt("current_mode", 0)
        game.isTiltMode = devicePrefs.getBoolean("is_tilt_mode", false)
        game.totalOrbs = prefs.getInt("total_orbs", 0)

        game.particleColor1 = devicePrefs.getInt("color1", Color.WHITE)
        game.particleColor2 = devicePrefs.getInt("color2", Color.parseColor("#88CCFF"))
        game.hue1 = devicePrefs.getFloat("hue1", 0f)
        game.hue2 = devicePrefs.getFloat("hue2", 200f)

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
            progressChanged()
        }

        game.saveOrbsCallback = { orbs ->
            val delta = (orbs.toLong() - prefs.getInt("total_orbs", 0).toLong()).coerceAtLeast(0L)
            val earned = prefs.getLong("progress_earned", 0L)
            prefs.edit().putInt("total_orbs", orbs).putLong("progress_earned", (earned + delta).coerceAtMost(9_000_000_000_000_000L)).apply()
            progressChanged()
        }

        game.saveColorsCallback = { c1, c2, h1, h2 ->
            devicePrefs.edit().putInt("color1", c1).putInt("color2", c2).putFloat("hue1", h1).putFloat("hue2", h2).apply()
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
            progressChanged()
        }

        // Queue only scores actually earned in this version, not imported legacy records.
        val mode = game.currentMode.coerceIn(0, 3)
        val key = "pending_online_score_$mode"
        prefs.edit().putInt(key, maxOf(prefs.getInt(key, -1), game.score)).apply()
        progressChanged()
    }

    private fun sendPendingScores() {
        if (game.playerName.isBlank() || FirebaseAuth.getInstance().currentUser?.uid != uid) return
        val modes = listOf("Classic", "Tilt", "Hearts", "MultiBall")
        for (i in modes.indices) {
            val key = "pending_online_score_$i"
            val value = prefs.getInt(key, -1)
            if (value < 0 || !scoresInFlight.add(i)) continue
            leaderboard.submitScore(uid, game.playerName, value, modes[i]) { success, _ ->
                synchronized(this) {
                    scoresInFlight.remove(i)
                    if (!progressAttached || FirebaseAuth.getInstance().currentUser?.uid != uid) return@synchronized
                    if (success && prefs.getInt(key, -1) <= value) prefs.edit().remove(key).apply()
                }
            }
        }
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

    @Synchronized
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (game.state == Game.State.AUTH_LOADING) {
            if (e.action == MotionEvent.ACTION_DOWN && game.accountLoadError && game.retryAccountBtn.contains(e.x, e.y)) loadAccountProfile()
            return true
        }
        if (game.state == Game.State.ACCOUNT) {
            if (e.action == MotionEvent.ACTION_DOWN && !game.accountBusy && game.menuAnimProgress >= 0.99f) {
                val actions = if (game.accountIsGuest) listOf("register", "login", "reset", "back") else listOf("reset", "logout", "back")
                for ((i, action) in actions.withIndex()) {
                    if (game.accountButtons[i].contains(e.x, e.y)) {
                        if (action == "back") game.openMenu(game.accountReturnState) else onAccountAction?.invoke(action)
                        break
                    }
                }
            }
            return true
        }
        if (game.state == Game.State.NAME_ENTRY) {
            if (e.action == MotionEvent.ACTION_DOWN && !nicknameSaving && game.menuAnimProgress >= 0.99f) {
                if (game.nameInputRect.contains(e.x, e.y)) {
                    game.errorMessage = ""
                    onShowKeyboard?.invoke()
                } else if (game.nameAcceptBtnRect.contains(e.x, e.y)) {
                    val newName = game.playerName.trim()
                    if (newName.isBlank()) game.errorMessage = "Введите ник!"
                    else {
                        game.errorMessage = ""
                        onHideKeyboard?.invoke()
                        saveNickname(newName)
                    }
                } else if (game.nameDeclineBtnRect.contains(e.x, e.y)) {
                    onHideKeyboard?.invoke()
                    if (game.previousStateForName == Game.State.SETTINGS) {
                        game.playerName = prefs.getString("player_name", "").orEmpty()
                        game.openMenu(Game.State.SETTINGS)
                    } else if (game.accountIsGuest) {
                        saveNickname("Guest_${uid.take(8)}_${(1000..9999).random()}", random = true)
                    } else game.errorMessage = "Введите ник!"
                } else if (game.nameAccountBtnRect.contains(e.x, e.y)) {
                    openAccount(Game.State.NAME_ENTRY)
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
                if (!game.isMenuPhysicsEnabled && game.menuAnimProgress >= 0.99f && game.accountBtnRect.contains(e.x, e.y)) {
                    openAccount(Game.State.MENU)
                    return true
                }
                if (game.helpBtnRect.contains(e.x, e.y)) {
                    game.isMenuPhysicsEnabled = !game.isMenuPhysicsEnabled
                    if (game.isMenuPhysicsEnabled) {
                        game.fallingButtons.clear()
                        val btnW = 550f; val btnH = 75f
                        val left = width / 2f - btnW / 2f

                        val cPrimary = android.graphics.Color.parseColor("#2979FF")
                        val cSecondary = android.graphics.Color.parseColor("#2A2A2A")
                        val cDanger = android.graphics.Color.parseColor("#EF5350")

                        game.fallingButtons.add(FallingButton(left, safe.top + 135f, btnW, btnH, "Аккаунт", cSecondary, Game.State.ACCOUNT))
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
                                Game.State.ACCOUNT -> openAccount(Game.State.MENU)
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
                        devicePrefs.edit().putInt("current_mode", 0).putBoolean("is_tilt_mode", false).apply()
                        prefs.edit().putBoolean("played_mode_0", true).apply()
                        progressChanged()
                        loadHighScore()
                        checkGlobalAchievements()
                        game.closeMenu(Game.State.PLAYING, restart = true)
                    }
                    game.btnModeTilt.contains(e.x, e.y) -> {
                        game.currentMode = 1
                        game.isTiltMode = true
                        devicePrefs.edit().putInt("current_mode", 1).putBoolean("is_tilt_mode", true).apply()
                        prefs.edit().putBoolean("played_mode_1", true).apply()
                        progressChanged()
                        loadHighScore()
                        checkGlobalAchievements()
                        game.closeMenu(Game.State.PLAYING, restart = true)
                    }
                    game.btnModeHearts.contains(e.x, e.y) -> {
                        game.currentMode = 2
                        game.isTiltMode = false
                        devicePrefs.edit().putInt("current_mode", 2).putBoolean("is_tilt_mode", false).apply()
                        prefs.edit().putBoolean("played_mode_2", true).apply()
                        progressChanged()
                        loadHighScore()
                        checkGlobalAchievements()
                        game.closeMenu(Game.State.PLAYING, restart = true)
                    }
                    game.btnModeNew.contains(e.x, e.y) -> {
                        game.currentMode = 3
                        game.isTiltMode = false
                        devicePrefs.edit().putInt("current_mode", 3)
                            .putBoolean("is_tilt_mode", false)
                            .apply()
                        prefs.edit().putBoolean("played_mode_3", true).apply()
                        progressChanged()
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
                            putString("progress_reset_pending", UUID.randomUUID().toString())
                            apply()
                        }
                        for (ach in game.achievementsList) ach.isUnlocked = false
                        progressChanged()
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
                        devicePrefs.edit().putString("ui_language", game.language).apply()
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
                            game.particleColor1 = Color.WHITE
                            game.particleColor2 = Color.parseColor("#88CCFF")
                            game.hue1 = 0f
                            game.hue2 = 200f
                            game.editingColor1 = true
                            game.saveColorsCallback?.invoke(game.particleColor1, game.particleColor2, game.hue1, game.hue2)
                        }
                    }
                    game.backFromColorsBtnRect.contains(e.x, e.y) -> game.closeMenu(Game.State.SETTINGS)
                }
                return true
            }

            if (game.pauseBtnRect.contains(e.x, e.y)) {
                game.openMenu(Game.State.PAUSED)
                return true
            }
        }

        if (game.state == Game.State.PLAYING) {
            if (game.currentMode == 1) {
                game.hasTouchedInTiltMode = true
            }

            if (!game.isTiltMode) {
                if (e.action == MotionEvent.ACTION_MOVE || e.action == MotionEvent.ACTION_DOWN) {
                    game.paddleX = e.x.coerceIn(
                        safe.left + game.paddleW / 2,
                        width - safe.right - game.paddleW / 2
                    )
                }
            }
        }
        return true
    }

    @Synchronized
    fun step(dt: Float) {
        val currentPaddleY = height - safe.bottom - 100f
        game.update(dt, safe, width, currentPaddleY)

        if (game.vibrateTrigger) {
            game.vibrateTrigger = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        }

        if (game.state == Game.State.GAME_OVER && !scoreSavedForRound) {
            scoreSavedForRound = true
            prefs.edit().putBoolean("played_mode_${game.currentMode}", true).apply()
            checkGlobalAchievements()
            saveHighScore()
            progressChanged()
        } else if (game.state == Game.State.PLAYING) {
            scoreSavedForRound = false
        }
    }

    @Synchronized
    fun drawGame(c: Canvas) {
        game.render(c, safe)
    }
}

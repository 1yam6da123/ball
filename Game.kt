package com.lyambda.ball

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    var life = 1.0f
    var color = Color.WHITE
    var isOrb = false
}

data class Achievement(val id: String, val title: String, val desc: String, var isUnlocked: Boolean)

class FallingButton(
    var x: Float, var y: Float,
    val w: Float, val h: Float,
    val text: String, val color: Int,
    val targetState: Game.State
) {
    var vx = (Random.nextFloat() - 0.5f) * 1200f
    var vy = -Random.nextFloat() * 1000f - 500f
    var angle = 0f
    var angularVelocity = (Random.nextFloat() - 0.5f) * 10f

    fun update(dt: Float, screenW: Int, floorY: Float, tiltX: Float, shakeIntensity: Float) {
        vy += 2500f * dt
        vx += tiltX * 1500f * dt

        if (shakeIntensity > 0.05f) {
            vx += (Random.nextFloat() - 0.5f) * shakeIntensity * 2000f * dt
            vy -= Random.nextFloat() * shakeIntensity * 1000f * dt
        }

        vx *= 0.99f

        x += vx * dt
        y += vy * dt
        angle += angularVelocity * dt

        if (x < 0) { x = 0f; vx = abs(vx) * 0.8f; angularVelocity = -angularVelocity }
        if (x + w > screenW) { x = screenW - w; vx = -abs(vx) * 0.8f; angularVelocity = -angularVelocity }

        if (y + h > floorY) {
            y = floorY - h
            vy = -abs(vy) * 0.6f
            vx *= 0.95f
            angularVelocity *= 0.8f
            if (abs(vy) < 50f) vy = 0f
        }

        if (y < 0) { y = 0f; vy = abs(vy) * 0.8f }
    }
}

class Game {
    // UI language is independent of the device locale and gameplay identifiers.
    var language: String = "en"
    fun t(value: String): String {
        if (language != "en") return value
        val translated = englishUi[value]
        if (translated != null) return translated
        return when {
            value.startsWith("Рекорд: ") -> "Best: " + value.removePrefix("Рекорд: ")
            value.startsWith("Режим: ") -> "Mode: " + t(value.removePrefix("Режим: "))
            value.startsWith("МЯЧИ: ") -> "BALLS: " + value.removePrefix("МЯЧИ: ")
            value.startsWith("Твой счет: ") -> "Your score: " + value.removePrefix("Твой счет: ")
            value.endsWith(" Орбов") -> value.removeSuffix(" Орбов") + " Orbs"
            else -> value
        }
    }

    private val englishUi: Map<String, String> = mapOf(
        "Аккаунт" to "Account",
        "АККАУНТ" to "ACCOUNT",
        "Гостевой профиль" to "Guest profile",
        "Постоянный аккаунт" to "Registered account",
        "Войти" to "Sign in",
        "Регистрация" to "Register",
        "Восстановить пароль" to "Reset password",
        "Выйти из аккаунта" to "Sign out",
        "Повторить" to "Retry",
        "Не удалось загрузить профиль" to "Could not load profile",
        "Операция выполняется..." to "Please wait...",
        "Не удалось сохранить ник" to "Could not save nickname",
        "Ник: 1–24 символа, без /" to "Nickname: 1–24 characters, no /",
        "ИЗМЕНИТЬ НИК" to "CHANGE NICKNAME",
        "ВВЕДИТЕ ВАШ НИК" to "ENTER YOUR NICKNAME",
        "Нажмите чтобы ввести" to "Tap to enter",
        "Сохранить" to "Save",
        "Готово" to "Done",
        "Отмена" to "Cancel",
        "Отказаться" to "Skip",
        "Двигай и приближай фон" to "Move and zoom background",
        "Сохранить фон" to "Save background",
        "Двигай и приближай фото" to "Move and zoom image",
        "Сохранить мяч" to "Save ball",
        "+1 МЯЧ!" to "+1 BALL!",
        "Играть" to "Play",
        "Режимы" to "Modes",
        "Рейтинг" to "Leaderboard",
        "Настройки" to "Settings",
        "Достижения" to "Achievements",
        "Выход" to "Exit",
        "РЕЙТИНГ" to "LEADERBOARD",
        "ВЫБЕРИ РЕЖИМ" to "SELECT MODE",
        "Классический" to "Classic",
        "Наклон" to "Tilt",
        "Сердечки" to "Hearts",
        "Мультишар" to "MultiBall",
        "Загрузка..." to "Loading...",
        "Пока нет рекордов :(" to "No scores yet :(",
        "Назад" to "Back",
        "РЕЖИМЫ" to "MODES",
        "ПАУЗА" to "PAUSED",
        "Продолжить" to "Resume",
        "В главное меню" to "Main menu",
        "НАСТРОЙКИ" to "SETTINGS",
        "Изменить ник" to "Change nickname",
        "Загрузить фон" to "Upload background",
        "Сбросить фон" to "Reset background",
        "Загрузить мяч" to "Upload ball",
        "Сбросить мяч" to "Reset ball",
        "Цвета частиц" to "Particle colors",
        "ЦВЕТА ЧАСТИЦ" to "PARTICLE COLORS",
        "Цвет 1" to "Color 1",
        "Цвет 2" to "Color 2",
        "Сбросить цвета" to "Reset colors",
        "ДОСТИЖЕНИЯ" to "ACHIEVEMENTS",
        "Еще не открыто" to "Not unlocked yet",
        "Сбросить достижения" to "Reset achievements",
        "НОВЫЙ РЕКОРД!" to "NEW RECORD!",
        "ИГРА ОКОНЧЕНА" to "GAME OVER",
        "Начать заново" to "Play again",
        "новое достижение!" to "achievement unlocked!",
        "коснись чтобы посмотреть описание" to "tap to view details",
        "Да" to "Yes",
        "Мастер наклона" to "Tilt Master",
        "Без рук" to "Hands Free",
        "Живучий" to "Survivor",
        "На волоске" to "Close Call",
        "Второе дыхание" to "Second Wind",
        "Кастомизатор" to "Customizer",
        "Индивидуальность" to "Unique Style",
        "Исследователь" to "Explorer",
        "Коллекционер" to "Collector",
        "Набрать 50 очков в режиме наклона" to "Score 50 points in Tilt mode",
        "Набрать 20 очков без касаний экрана" to "Score 20 points without touching the screen",
        "Набрать 50 очков, сохранив 3 сердечка" to "Score 50 points with all 3 hearts",
        "Набрать 50 очков с 1 сердечком" to "Score 50 points with 1 heart",
        "Побить рекорд, потеряв 2 жизни" to "Beat your record after losing 2 lives",
        "Загрузить свой фон" to "Upload a custom background",
        "Загрузить свой мяч" to "Upload a custom ball",
        "Сыграть во все 4 режима" to "Play all 4 modes",
        "Открыть все остальные достижения" to "Unlock all other achievements",
        "Выйти из игры?" to "Exit the game?",
        "Выйти в главное меню?" to "Return to main menu?",
        "Сбросить достижения?" to "Reset achievements?",
        "Сбросить фон?" to "Reset background?",
        "Сбросить мяч?" to "Reset ball?",
        "Сбросить цвета?" to "Reset colors?",
        "Остаться анонимным? Будет присвоен случайный ник." to "Stay anonymous? A random nickname will be assigned.",
        "Введите ник!" to "Enter a nickname!",
        "Ошибка проверки ника" to "Nickname verification failed",
        "Ошибка чтения: " to "Read error: ",
        "Этот ник уже занят!" to "This nickname is taken!",
        "Не удалось проверить ник. Проверь интернет и правила Firestore." to "Could not verify nickname. Check your connection and Firestore rules."
    )
    var languageBtnRect = RectF()
    var accountBtnRect = RectF()
    var nameAccountBtnRect = RectF()
    val accountButtons = Array(5) { RectF() }
    var accountReturnState = State.MENU
    @Volatile var accountIsGuest = true
    @Volatile var accountBusy = false
    var accountStatus = ""
    var accountLoadError = false
    var retryAccountBtn = RectF()

    var paddleX = 0f
    val paddleW = 250f
    val paddleH = 40f

    var ballX = 0f
    var ballY = 0f
    var vx = 0f
    var vy = 0f
    val ballR = 30f

    data class ExtraBall(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float = 1f)
    val extraBalls = mutableListOf<ExtraBall>()
    private var nextMultiBallThreshold = 5
    private var multiBallNoticeTimer = 0f
    private var multiBallNoticeCount = 0


    var score = 0
    var highScore = 0
    @Volatile var state = State.MENU

    var viewWidth = 0
    var viewHeight = 0

    var currentMode = 0
    var lives = 3
    var isNewRecord = false
    var hasTouchedInTiltMode = false

    var leaderboardEntries = mutableListOf<LeaderboardEntry>()
    var isLeaderboardLoading = false
    var leaderboardMode = 0 // Режим просмотра рейтинга; не влияет на игровой режим
    var playerName = "Player"
    var playerId = ""
    var previousStateForName = State.NAME_ENTRY
    var errorMessage = "" // Переменная для ошибки

    var totalOrbs = 0
    var earnedOrbs = 0
    var orbsAwarded = false
    var orbCountAnim = 0f
    var saveOrbsCallback: ((Int) -> Unit)? = null

    var particleColor1 = Color.WHITE
    var particleColor2 = Color.parseColor("#88CCFF")
    var hue1 = 0f
    var hue2 = 200f
    var editingColor1 = true
    var saveColorsCallback: ((Int, Int, Float, Float) -> Unit)? = null

    var tempBgImage: Bitmap? = null
    var bgScale = 1f
    var bgImageOffsetX = 0f
    var bgImageOffsetY = 0f
    var saveBgCropBtnRect = RectF()

    var showConfirmDialog = false
    var confirmDialogAnim = 0f
    var confirmText = ""
    var pendingAction: (() -> Unit)? = null
    var confirmYesBtnRect = RectF()
    var confirmNoBtnRect = RectF()

    var languageSwitchAnim = 1f
    fun animateLanguageSwitch() { languageSwitchAnim = 0f }

    var menuAnimProgress = 0f
    var isClosingMenu = false
    var nextStateAfterClose = State.PLAYING
    var previousState = State.MENU
    var restartOnClose = false

    var isMenuPhysicsEnabled = false
    val fallingButtons = mutableListOf<FallingButton>()
    var helpBtnRect = RectF()
    var shakeIntensity = 0f

    private var isInitialized = false

    var isTiltMode = false
    var tiltX = 0f

    var bgImage: Bitmap? = null
    var ballImage: Bitmap? = null

    var tempCropImage: Bitmap? = null
    var cropScale = 1f
    var cropImageOffsetX = 0f
    var cropImageOffsetY = 0f

    var nameInputRect = RectF()
    var nameAcceptBtnRect = RectF()
    var nameDeclineBtnRect = RectF()

    var btnMenuPlay = RectF()
    var btnMenuModes = RectF()
    var btnMenuLeaderboard = RectF()
    var btnMenuSettings = RectF()
    var btnMenuAch = RectF()
    var btnMenuExit = RectF()

    var btnModeClassic = RectF()
    var btnModeTilt = RectF()
    var btnModeHearts = RectF()
    var btnModeNew = RectF()
    var btnModeBack = RectF()

    var pauseResumeBtn = RectF()
    var pauseModesBtn = RectF()
    var pauseSettingsBtn = RectF()
    var pauseAchBtn = RectF()
    var pauseMainMenuBtn = RectF()

    var btnSettingsChangeNick = RectF()
    var bgBtnRect = RectF()
    var resetBgBtnRect = RectF()
    var ballBtnRect = RectF()
    var resetBallBtnRect = RectF()
    var colorsBtnRect = RectF()
    var backBtnRect = RectF()

    var restartBtnRect = RectF()
    var saveCropBtnRect = RectF()
    var closeAchievementsBtnRect = RectF()
    var resetAchievementsBtnRect = RectF()
    var pauseBtnRect = RectF()
    var closeLeaderboardBtnRect = RectF()
    val leaderboardModeButtons = Array(4) { RectF() }
    var leaderboardModeBackBtn = RectF()

    var color1BoxRect = RectF()
    var color2BoxRect = RectF()
    var hueSliderRect = RectF()
    var resetColorsBtnRect = RectF()
    var backFromColorsBtnRect = RectF()

    var showAchievementPopup = false
    var popupTitle = ""
    var popupDesc = ""
    var popupTriggerTime = 0L
    var popupRect = RectF()
    val achievementsList = mutableListOf<Achievement>()

    val particles = mutableListOf<Particle>()
    var paddleFlashTimer = 0f
    var vibrateTrigger = false

    var gameOverAnimProgress = 0f
    var shakeTimer = 0f
    var flashTimer = 0f
    var isRedFlash = false

    private val colorBg = Color.parseColor("#121212")
    private val colorPrimary = Color.parseColor("#2979FF")
    private val colorSecondary = Color.parseColor("#2A2A2A")
    private val colorDanger = Color.parseColor("#EF5350")
    private val colorText = Color.WHITE
    private val colorSubText = Color.parseColor("#AAAAAA")
    private val colorOrb = Color.parseColor("#00BFFF")

    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isFilterBitmap = true
    }

    private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)
    private fun easeInCubic(t: Float): Float = t * t * t
    private fun easeOutQuad(t: Float): Float = t * (2f - t)
    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    fun openMenu(newState: State) {
        state = newState
        menuAnimProgress = 0f
        isClosingMenu = false
        if (state == State.MENU) {
            isMenuPhysicsEnabled = false
            fallingButtons.clear()
            shakeIntensity = 0f
        }
        if (state == State.NAME_ENTRY) {
            errorMessage = ""
        }
    }

    fun closeMenu(targetState: State, restart: Boolean = false) {
        isClosingMenu = true
        nextStateAfterClose = targetState
        restartOnClose = restart
    }

    fun showConfirmation(text: String, action: () -> Unit) {
        confirmText = text
        pendingAction = action
        showConfirmDialog = true
        confirmDialogAnim = 0f
    }

    fun reset(screenWidth: Int, safe: Rect) {
        paddleX = screenWidth / 2f
        ballX = screenWidth / 2f
        ballY = safe.top + 100f
        vx = if (Random.nextBoolean()) 400f else -400f
        vy = 500f
        score = 0
        extraBalls.clear()
        nextMultiBallThreshold = 5
        multiBallNoticeTimer = 0f
        multiBallNoticeCount = 0
        lives = 3
        isNewRecord = false
        hasTouchedInTiltMode = false
        earnedOrbs = 0
        orbsAwarded = false
        orbCountAnim = 0f
        state = State.PLAYING
        particles.clear()
        paddleFlashTimer = 0f
        showAchievementPopup = false
        gameOverAnimProgress = 0f
        shakeTimer = 0f
        flashTimer = 0f
        isClosingMenu = false
        menuAnimProgress = 1f
        showConfirmDialog = false
    }

    fun checkAchievements() {
        if (currentMode == 1 && score >= 50) unlock("mode_tilt_master", "Мастер наклона", "Набрано 50 очков!")
        if (currentMode == 1 && score >= 20 && !hasTouchedInTiltMode) unlock("mode_tilt_nohands", "Без рук", "Набрано 20 очков без касаний!")
        if (currentMode == 2 && score >= 50 && lives == 3) unlock("mode_hearts_survivor", "Живучий", "50 очков с 3 сердечками!")
        if (currentMode == 2 && score >= 50 && lives == 1) unlock("mode_hearts_risky", "На волоске", "50 очков с 1 сердечком!")
        if (currentMode == 2 && lives == 1 && score > highScore) unlock("mode_hearts_secondwind", "Второе дыхание", "Побит рекорд с 1 жизнью!")
    }

    fun unlock(id: String, title: String, desc: String) {
        val ach = achievementsList.find { it.id == id }
        if (ach != null && !ach.isUnlocked) {
            ach.isUnlocked = true
            popupTitle = title
            popupDesc = desc
            showAchievementPopup = true
            popupTriggerTime = System.currentTimeMillis()
            saveAchievementCallback?.invoke(id)
        }
    }

    var saveAchievementCallback: ((String) -> Unit)? = null

    private fun resolveCollisions() {
        for (i in 0 until fallingButtons.size) {
            for (j in i + 1 until fallingButtons.size) {
                val b1 = fallingButtons[i]
                val b2 = fallingButtons[j]

                if (b1.x < b2.x + b2.w && b1.x + b1.w > b2.x &&
                    b1.y < b2.y + b2.h && b1.y + b1.h > b2.y) {

                    val overlapX = min(b1.x + b1.w, b2.x + b2.w) - max(b1.x, b2.x)
                    val overlapY = min(b1.y + b1.h, b2.y + b2.h) - max(b1.y, b2.y)

                    if (overlapX < overlapY) {
                        if (b1.x < b2.x) {
                            b1.x -= overlapX / 2; b2.x += overlapX / 2
                        } else {
                            b1.x += overlapX / 2; b2.x -= overlapX / 2
                        }
                        val tempVx = b1.vx; b1.vx = b2.vx * 0.8f; b2.vx = tempVx * 0.8f
                    } else {
                        if (b1.y < b2.y) {
                            b1.y -= overlapY / 2; b2.y += overlapY / 2
                            b1.vy = -abs(b1.vy) * 0.8f; b2.vy = abs(b2.vy) * 0.8f
                        } else {
                            b1.y += overlapY / 2; b2.y -= overlapY / 2
                            b1.vy = abs(b1.vy) * 0.8f; b2.vy = -abs(b2.vy) * 0.8f
                        }
                    }
                }
            }
        }
    }

    fun update(dt: Float, safe: Rect, screenWidth: Int, paddleY: Float) {
        languageSwitchAnim = (languageSwitchAnim + dt * 5f).coerceAtMost(1f)
        if (!isInitialized) {
            val initialState = state
            reset(screenWidth, safe)
            state = initialState
            menuAnimProgress = 0f
            isInitialized = true
        }

        if (state == State.NAME_ENTRY) {
            if (menuAnimProgress < 1f) {
                menuAnimProgress += dt * 4f
                if (menuAnimProgress > 1f) menuAnimProgress = 1f
            }
            return
        }

        if (showConfirmDialog) {
            if (confirmDialogAnim < 1f) {
                confirmDialogAnim += dt * 5f
                if (confirmDialogAnim > 1f) confirmDialogAnim = 1f
            }
            return
        }

        if (isClosingMenu) {
            menuAnimProgress -= dt * 4f
            if (menuAnimProgress <= 0f) {
                menuAnimProgress = 0f
                isClosingMenu = false
                state = nextStateAfterClose

                if (restartOnClose) {
                    restartOnClose = false
                    reset(screenWidth, safe)
                } else if (state != State.PLAYING && state != State.CROPPING_BALL && state != State.CROPPING_BG) {
                    menuAnimProgress = 0f
                }
            }
        } else if (state != State.PLAYING && state != State.CROPPING_BALL && state != State.CROPPING_BG && state != State.GAME_OVER) {
            menuAnimProgress += dt * 4f
            if (menuAnimProgress > 1f) menuAnimProgress = 1f
        }

        shakeIntensity *= 0.95f
        if (shakeIntensity < 0.01f) shakeIntensity = 0f

        if (isMenuPhysicsEnabled && state == State.MENU) {
            val floorY = paddleY + 100f
            for (btn in fallingButtons) {
                btn.update(dt, screenWidth, floorY, tiltX, shakeIntensity)
            }
            resolveCollisions()
        }

        if (state == State.GAME_OVER) {
            if (gameOverAnimProgress == 0f) {
                shakeTimer = 0.5f
                flashTimer = 0.15f
                isRedFlash = false
                vibrateTrigger = true

                if (!orbsAwarded) {
                    orbsAwarded = true
                    earnedOrbs = (score.toLong() * 2L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    totalOrbs = (totalOrbs.toLong() + earnedOrbs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    saveOrbsCallback?.invoke(totalOrbs)

                    if (earnedOrbs > 0) {
                        for (i in 1..20) {
                            val p = Particle(
                                ballX, ballY,
                                Random.nextFloat() * 600f - 100f,
                                -Random.nextFloat() * 800f - 200f
                            )
                            p.isOrb = true
                            p.life = 1.5f
                            particles.add(p)
                        }
                    }
                }
            }

            if (gameOverAnimProgress > 0.2f) {
                val targetOrbs = earnedOrbs.toFloat()
                if (orbCountAnim < targetOrbs) {
                    orbCountAnim += dt * (targetOrbs / 0.8f)
                    if (orbCountAnim > targetOrbs) orbCountAnim = targetOrbs
                }
            }

            if (gameOverAnimProgress < 1f) {
                gameOverAnimProgress += dt * 3f
                if (gameOverAnimProgress > 1f) gameOverAnimProgress = 1f
            }
        }

        if (shakeTimer > 0f) shakeTimer -= dt
        if (flashTimer > 0f) flashTimer -= dt

        if (showAchievementPopup) {
            if (System.currentTimeMillis() - popupTriggerTime > 5000) {
                showAchievementPopup = false
            }
        }

        if (paddleFlashTimer > 0) paddleFlashTimer -= dt

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (!p.isOrb) p.vy += 500f * dt
            p.life -= dt * 2f
            if (p.life <= 0) iterator.remove()
        }

        if (state != State.PLAYING) return

        if (isTiltMode) {
            paddleX += tiltX * 2500f * dt
            paddleX = paddleX.coerceIn(
                safe.left + paddleW / 2,
                screenWidth - safe.right - paddleW / 2
            )
        }

        if (currentMode == 3) {
            multiBallNoticeTimer = (multiBallNoticeTimer - dt).coerceAtLeast(0f)
            updateMultiBall(dt, safe, screenWidth, paddleY)
            return
        }

        ballX += vx * dt
        ballY += vy * dt

        if (ballX - ballR < safe.left) { ballX = safe.left + ballR; vx = abs(vx); vibrateTrigger = true; spawnSparks(ballX, ballY, true) }
        if (ballX + ballR > screenWidth - safe.right) { ballX = screenWidth - safe.right - ballR; vx = -abs(vx); vibrateTrigger = true; spawnSparks(ballX, ballY, true) }

        if (ballY - ballR < safe.top) {
            ballY = safe.top + ballR
            if (abs(vy) < 1500f) { vy = abs(vy) * 1.1f; vx *= 1.1f } else { vy = abs(vy) }
            vibrateTrigger = true
            spawnSparks(ballX, ballY, false)
        }

        val half = paddleW / 2
        val hitX = ballX in (paddleX - half - ballR)..(paddleX + half + ballR)
        val hitY = ballY + ballR >= paddleY && ballY < paddleY + paddleH
        if (hitY && hitX) {
            ballY = paddleY - ballR
            vy = -abs(vy)
            vx += (ballX - paddleX) * 3f
            score++
            checkAchievements()
            paddleFlashTimer = 0.1f
            vibrateTrigger = true
            spawnSparks(ballX, paddleY, false)
        }

        if (ballY - ballR > paddleY + 200f) {
            if (currentMode == 2 && lives > 1) {
                lives--
                isRedFlash = true
                flashTimer = 0.2f
                vibrateTrigger = true

                ballX = screenWidth / 2f
                ballY = safe.top + 100f
                vx = if (Random.nextBoolean()) 400f else -400f
                vy = 500f
            } else {
                if (currentMode == 2) lives = 0
                isNewRecord = score > highScore
                state = State.GAME_OVER
            }
        }
    }

    private fun updateMultiBall(dt: Float, safe: Rect, screenWidth: Int, paddleY: Float) {
        // Основной мяч хранится в стандартных координатах, остальные — отдельно.
        // Обрабатываем их одним алгоритмом, затем возвращаем координаты основного.
        val balls = mutableListOf(ExtraBall(ballX, ballY, vx, vy))
        balls.addAll(extraBalls)
        for (ball in balls) ball.age = (ball.age + dt * 3f).coerceAtMost(1f)
        val left = safe.left.toFloat()
        val right = (screenWidth - safe.right).toFloat()
        val half = paddleW / 2f
        val iterator = balls.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.x - ballR < left) {
                b.x = left + ballR; b.vx = abs(b.vx)
                vibrateTrigger = true
                spawnSparks(b.x, b.y, true)
            }
            if (b.x + ballR > right) {
                b.x = right - ballR; b.vx = -abs(b.vx)
                vibrateTrigger = true
                spawnSparks(b.x, b.y, true)
            }
            if (b.y - ballR < safe.top) {
                b.y = safe.top + ballR
                if (abs(b.vy) < 1500f) {
                    b.vy = abs(b.vy) * 1.1f
                    b.vx *= 1.1f
                } else b.vy = abs(b.vy)
                vibrateTrigger = true
                spawnSparks(b.x, b.y, false)
            }
            val hitX = b.x in (paddleX - half - ballR)..(paddleX + half + ballR)
            val hitY = b.vy > 0f && b.y + ballR >= paddleY && b.y < paddleY + paddleH
            if (hitX && hitY) {
                b.y = paddleY - ballR
                b.vy = -abs(b.vy)
                b.vx += (b.x - paddleX) * 3f
                score++
                checkAchievements()
                paddleFlashTimer = 0.1f
                vibrateTrigger = true
                spawnSparks(b.x, paddleY, false)
            }
            if (b.y - ballR > paddleY + 200f) iterator.remove()
        }

        // Каждые 5 очков можно получить ещё один мяч, если активных меньше пяти.
        // Потерянные мячи не возвращаются автоматически: нужен следующий порог.
        // Порог обрабатывается один раз, даже если в кадре набрано несколько очков.
        while (score >= nextMultiBallThreshold) {
            if (balls.isNotEmpty() && balls.size < 5) {
                val origin = balls.first()
                balls.add(ExtraBall(
                    origin.x.coerceIn(left + ballR, right - ballR),
                    (origin.y - ballR * 2.5f).coerceAtLeast(safe.top + ballR),
                    if (Random.nextBoolean()) 450f else -450f,
                    -600f,
                    0f
                ))
                multiBallNoticeTimer = 1.2f
                multiBallNoticeCount = balls.size
                spawnSparks(origin.x, origin.y, false)
            }
            nextMultiBallThreshold += 5
        }

        if (balls.isEmpty()) {
            extraBalls.clear()
            isNewRecord = score > highScore
            state = State.GAME_OVER
            return
        }
        val primary = balls.removeAt(0)
        ballX = primary.x; ballY = primary.y
        vx = primary.vx; vy = primary.vy
        extraBalls.clear()
        extraBalls.addAll(balls)
    }

    private fun spawnSparks(x: Float, y: Float, isWall: Boolean) {
        for (i in 1..10) {
            val pVx = if (isWall) (if (x < 100) 300f else -300f) + Random.nextFloat() * 200f - 100f else Random.nextFloat() * 600f - 300f
            val pVy = if (isWall) Random.nextFloat() * 600f - 300f else Random.nextFloat() * -400f - 100f
            val p = Particle(x, y, pVx, pVy)
            p.color = if (Random.nextBoolean()) particleColor1 else particleColor2
            p.isOrb = false
            particles.add(p)
        }
    }

    private fun getMenuOffsetY(multiplier: Float = 1f): Float {
        return if (isClosingMenu) easeInCubic(1f - menuAnimProgress) * 300f * multiplier
        else (1f - easeOutCubic(menuAnimProgress)) * 300f * multiplier
    }

    // Icons use the same Canvas transform as their buttons, including menu physics.
    private val buttonIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val buttonIconPath = Path()

    private fun buttonIcon(text: String): String = when (text) {
        "Играть", "Продолжить", "Play", "Resume" -> "play"
        "Режимы", "Modes" -> "gamepad"
        "Рейтинг", "Leaderboard" -> "trophy"
        "Настройки", "Settings" -> "gear"
        "Достижения", "Achievements" -> "medal"
        "Выход", "Exit" -> "exit"
        "Классический", "Classic" -> "ball"
        "Наклон", "Tilt" -> "tilt"
        "Сердечки", "Hearts" -> "heart"
        "Мультишар", "MultiBall" -> "multi"
        "Назад", "Back" -> "back"
        "В главное меню", "Main menu" -> "home"
        "Аккаунт", "Account", "Регистрация", "Register" -> "user"
        "Войти", "Sign in" -> "login"
        "Восстановить пароль", "Reset password" -> "lock"
        "Выйти из аккаунта", "Sign out" -> "exit"
        "Повторить", "Retry" -> "restart"
        "Изменить ник", "Change nickname" -> "edit"
        "Загрузить фон", "Upload background" -> "image"
        "Сбросить фон", "Reset background" -> "resetImage"
        "Загрузить мяч", "Upload ball" -> "uploadBall"
        "Сбросить мяч", "Reset ball" -> "resetBall"
        "Цвета частиц", "Particle colors" -> "palette"
        "Язык: Русский", "Language: English" -> "language"
        "Сбросить цвета", "Reset colors" -> "resetPalette"
        "Сбросить достижения", "Reset achievements" -> "resetMedal"
        "Начать заново", "Play again" -> "restart"
        "Да", "Сохранить", "Готово", "Сохранить фон", "Сохранить мяч", "Yes", "Save", "Done", "Save background", "Save ball" -> "check"
        "Отмена", "Отказаться", "Cancel", "Skip" -> "close"
        else -> "arrow"
    }

    private fun drawButtonIcon(c: Canvas, kind: String, x: Float, y: Float, size: Float, color: Int) {
        val ip = buttonIconPaint
        ip.color = color
        c.save()
        c.translate(x - size / 2f, y - size / 2f)
        c.scale(size / 24f, size / 24f)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(x1, y1, x2, y2, ip)
        fun circle(x1: Float, y1: Float, r: Float) = c.drawCircle(x1, y1, r, ip)
        fun path(vararg xy: Float) {
            buttonIconPath.reset()
            buttonIconPath.moveTo(xy[0], xy[1])
            for (i in 2 until xy.size step 2) buttonIconPath.lineTo(xy[i], xy[i + 1])
            c.drawPath(buttonIconPath, ip)
        }
        fun reset() {
            c.drawArc(4f, 4f, 20f, 20f, -135f, 285f, false, ip)
            path(3f, 4f, 3f, 10f, 9f, 10f)
        }
        when (kind) {
            "user" -> { circle(12f, 7f, 4f); c.drawArc(4f, 12f, 20f, 27f, 180f, 180f, false, ip) }
            "login" -> { path(13f, 3f, 21f, 3f, 21f, 21f, 13f, 21f); line(3f, 12f, 16f, 12f); path(11f, 7f, 16f, 12f, 11f, 17f) }
            "lock" -> { c.drawRoundRect(5f, 10f, 19f, 21f, 2f, 2f, ip); c.drawArc(8f, 2f, 16f, 16f, 180f, 180f, false, ip); line(12f, 14f, 12f, 17f) }
            "play" -> path(7f, 3f, 20f, 12f, 7f, 21f, 7f, 3f)
            "gamepad" -> { c.drawRoundRect(2f, 6f, 22f, 18f, 5f, 5f, ip); line(5f, 12f, 11f, 12f); line(8f, 9f, 8f, 15f); circle(16f, 10f, 0.8f); circle(19f, 14f, 0.8f) }
            "trophy" -> { path(7f, 3f, 17f, 3f, 17f, 9f, 15f, 13f, 9f, 13f, 7f, 9f, 7f, 3f); path(7f, 5f, 3f, 5f, 3f, 9f, 7f, 11f); path(17f, 5f, 21f, 5f, 21f, 9f, 17f, 11f); line(12f, 13f, 12f, 21f); line(7f, 21f, 17f, 21f) }
            "gear" -> { circle(12f, 12f, 7f); circle(12f, 12f, 3f); for (i in 0..7) { c.save(); c.rotate(i * 45f, 12f, 12f); line(12f, 2f, 12f, 5f); c.restore() } }
            "medal" -> { circle(12f, 8f, 5f); path(8f, 12f, 5f, 22f, 10f, 19f, 12f, 22f, 15f, 19f, 19f, 22f, 16f, 12f) }
            "exit" -> { path(12f, 3f, 4f, 3f, 4f, 21f, 12f, 21f); line(9f, 12f, 21f, 12f); path(16f, 7f, 21f, 12f, 16f, 17f) }
            "ball" -> circle(12f, 12f, 8f)
            "tilt" -> { c.save(); c.rotate(-25f, 12f, 12f); c.drawRoundRect(7f, 2f, 17f, 22f, 2f, 2f, ip); line(10f, 18f, 14f, 18f); c.restore() }
            "heart" -> { buttonIconPath.reset(); buttonIconPath.moveTo(12f, 21f); buttonIconPath.cubicTo(0f, 13f, 0f, 5f, 6f, 4f); buttonIconPath.cubicTo(9f, 3f, 11f, 5f, 12f, 7f); buttonIconPath.cubicTo(13f, 5f, 15f, 3f, 18f, 4f); buttonIconPath.cubicTo(24f, 5f, 24f, 13f, 12f, 21f); c.drawPath(buttonIconPath, ip) }
            "multi" -> { circle(12f, 6f, 4f); circle(6f, 16f, 4f); circle(18f, 16f, 4f) }
            "back" -> { line(4f, 12f, 21f, 12f); path(11f, 5f, 4f, 12f, 11f, 19f) }
            "home" -> { path(2f, 11f, 12f, 3f, 22f, 11f); path(5f, 9f, 5f, 21f, 10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f, 19f, 21f, 19f, 9f) }
            "edit" -> { path(4f, 16f, 3f, 21f, 8f, 20f, 21f, 7f, 17f, 3f, 4f, 16f); line(14f, 6f, 18f, 10f) }
            "image" -> { c.drawRoundRect(3f, 3f, 21f, 21f, 2f, 2f, ip); circle(8f, 8f, 2f); path(4f, 18f, 10f, 12f, 14f, 16f, 18f, 11f, 21f, 14f) }
            "uploadBall" -> { circle(12f, 15f, 6f); line(12f, 2f, 12f, 12f); path(8f, 6f, 12f, 2f, 16f, 6f) }
            "palette" -> { c.drawOval(3f, 3f, 21f, 21f, ip); circle(8f, 8f, 1.3f); circle(15f, 7f, 1.3f); circle(17f, 13f, 1.3f); circle(8f, 16f, 1.3f) }
            "language" -> { circle(12f, 12f, 9f); c.drawOval(8f, 3f, 16f, 21f, ip); line(3f, 12f, 21f, 12f) }
            "restart", "resetImage", "resetBall", "resetPalette", "resetMedal" -> {
                reset()
                when (kind) {
                    "resetImage" -> { path(8f, 15f, 12f, 10f, 16f, 15f); circle(15f, 8f, 0.7f) }
                    "resetBall" -> circle(12f, 12f, 3f)
                    "resetPalette" -> { circle(10f, 10f, 0.8f); circle(14f, 10f, 0.8f); circle(12f, 14f, 0.8f) }
                    "resetMedal" -> { circle(12f, 10f, 2.5f); path(10f, 12f, 9f, 16f, 12f, 14f, 15f, 16f, 14f, 12f) }
                }
            }
            "check" -> path(4f, 12f, 9f, 17f, 20f, 6f)
            "close" -> { line(5f, 5f, 19f, 19f); line(19f, 5f, 5f, 19f) }
            else -> path(8f, 5f, 15f, 12f, 8f, 19f)
        }
        c.restore()
    }

    private fun drawButton(c: Canvas, rect: RectF, text: String, bgColor: Int, textColor: Int = Color.WHITE, textSize: Float = 45f, cornerRadius: Float = 16f) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        paint.color = bgColor
        c.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.color = textColor
        paint.textSize = textSize
        paint.textAlign = Paint.Align.CENTER
        val label = t(text)
        val padding = min(12f, rect.width() * 0.05f)
        val iconSize = min(min(38f, rect.height() * 0.52f), rect.width() * 0.18f)
        val gap = min(12f, iconSize * 0.32f)
        val maxLabelWidth = (rect.width() - padding * 2f - iconSize - gap).coerceAtLeast(1f)
        val measuredWidth = paint.measureText(label)
        if (measuredWidth > maxLabelWidth) paint.textSize = textSize * maxLabelWidth / measuredWidth
        val labelWidth = paint.measureText(label)
        val groupLeft = rect.centerX() - (iconSize + gap + labelWidth) / 2f
        val textY = rect.centerY() - (paint.ascent() + paint.descent()) / 2f
        drawButtonIcon(c, buttonIcon(text), groupLeft + iconSize / 2f, rect.centerY(), iconSize, textColor)
        c.drawText(label, groupLeft + iconSize + gap + labelWidth / 2f, textY, paint)
    }

    private fun drawOrbIcon(c: Canvas, x: Float, y: Float, radius: Float) {
        paint.color = colorOrb
        c.drawCircle(x, y, radius, paint)
        paint.color = Color.parseColor("#80FFFFFF")
        c.drawCircle(x - radius * 0.3f, y - radius * 0.3f, radius * 0.4f, paint)
    }

    fun render(c: Canvas, safe: Rect) {
        viewWidth = c.width
        viewHeight = c.height

        c.save()

        if (shakeTimer > 0f) {
            val intensity = 40f * (shakeTimer / 0.5f)
            c.translate((Random.nextFloat() - 0.5f) * intensity, (Random.nextFloat() - 0.5f) * intensity)
        }

        if (state == State.NAME_ENTRY) {
            c.drawColor(Color.parseColor("#121212"))

            val isFromSettings = previousStateForName == State.SETTINGS
            val titleText = t(if (isFromSettings) "ИЗМЕНИТЬ НИК" else "ВВЕДИТЕ ВАШ НИК")

            val titleScale = easeOutCubic(menuAnimProgress)
            c.save()
            c.translate(c.width / 2f, c.height / 2f - 300f)
            c.scale(titleScale, titleScale)
            paint.textAlign = Paint.Align.CENTER
            paint.color = colorPrimary
            paint.textSize = 80f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText(titleText, 0f, 0f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            c.restore()

            val p1 = easeOutCubic((menuAnimProgress - 0.1f).coerceIn(0f, 1f))
            if (p1 > 0) {
                c.save(); c.translate(0f, (1f - p1) * 200f)

                nameInputRect.set(c.width / 2f - 300f, c.height / 2f - 100f, c.width / 2f + 300f, c.height / 2f)
                paint.color = colorSecondary
                c.drawRoundRect(nameInputRect, 20f, 20f, paint)

                paint.style = Paint.Style.STROKE
                paint.color = colorPrimary
                paint.strokeWidth = 4f
                c.drawRoundRect(nameInputRect, 20f, 20f, paint)
                paint.style = Paint.Style.FILL

                paint.color = colorText
                paint.textSize = 50f
                paint.textAlign = Paint.Align.CENTER
                val displayName = if (playerName.isEmpty()) t("Нажмите чтобы ввести") else playerName
                paint.color = if (playerName.isEmpty()) colorSubText else colorText
                c.drawText(displayName, nameInputRect.centerX(), nameInputRect.centerY() + 18f, paint)

                c.restore()
            }

            // ОТРИСОВКА ОШИБКИ (КРАСНЫЙ ТЕКСТ)
            if (errorMessage.isNotEmpty()) {
                paint.color = colorDanger
                paint.textSize = 40f
                paint.textAlign = Paint.Align.CENTER
                c.drawText(t(errorMessage), c.width / 2f, c.height / 2f + 30f, paint)
            }

            val p2 = easeOutCubic((menuAnimProgress - 0.2f).coerceIn(0f, 1f))
            if (p2 > 0) {
                c.save(); c.translate(0f, (1f - p2) * 200f)
                nameAcceptBtnRect.set(c.width / 2f - 300f, c.height / 2f + 70f, c.width / 2f + 300f, c.height / 2f + 170f)
                val acceptText = t(if (isFromSettings) "Сохранить" else "Готово")
                drawButton(c, nameAcceptBtnRect, acceptText, colorPrimary, textSize = 50f)
                c.restore()
            }

            val p3 = easeOutCubic((menuAnimProgress - 0.3f).coerceIn(0f, 1f))
            nameDeclineBtnRect.setEmpty()
            if (p3 > 0 && (isFromSettings || accountIsGuest)) {
                c.save(); c.translate(0f, (1f - p3) * 200f)
                nameDeclineBtnRect.set(c.width / 2f - 300f, c.height / 2f + 200f, c.width / 2f + 300f, c.height / 2f + 300f)
                val declineText = t(if (isFromSettings) "Отмена" else "Отказаться")
                val declineColor = if (isFromSettings) colorSecondary else colorDanger
                drawButton(c, nameDeclineBtnRect, declineText, declineColor, textSize = 50f)
                c.restore()
            }

            nameAccountBtnRect.setEmpty()
            if (!isFromSettings && menuAnimProgress > 0.4f) {
                val w = min(600f, c.width * 0.85f)
                val top = c.height / 2f + 325f + (1f - easeOutCubic(menuAnimProgress)) * 100f
                nameAccountBtnRect.set(c.width / 2f - w / 2f, top, c.width / 2f + w / 2f, top + 75f)
                drawButton(c, nameAccountBtnRect, "Аккаунт", colorSecondary, textSize = 42f)
            }
            c.restore()
            return
        }

        if (state == State.AUTH_LOADING || state == State.ACCOUNT) {
            c.drawColor(colorBg)
            val w = min(550f, c.width * 0.86f)
            val available = (c.height - safe.top - safe.bottom).toFloat().coerceAtLeast(1f)
            val h = min(75f, available / 10f)
            val cx = c.width / 2f
            val cy = safe.top + available / 2f
            val progress = easeOutCubic(menuAnimProgress.coerceIn(0f, 1f))
            paint.color = colorPrimary; paint.textAlign = Paint.Align.CENTER; paint.textSize = min(68f, w / 7f)
            c.save(); c.translate(cx, cy - h * 3.7f); c.scale(progress, progress)
            c.drawText(t(if (state == State.ACCOUNT) "АККАУНТ" else "ball"), 0f, 0f, paint)
            c.restore()
            paint.color = colorSubText; paint.textSize = min(38f, w / 15f)
            val info = if (state == State.AUTH_LOADING) {
                if (accountLoadError) t("Не удалось загрузить профиль") else t("Загрузка...")
            } else if (accountBusy) t("Операция выполняется...") else t(if (accountIsGuest) "Гостевой профиль" else "Постоянный аккаунт")
            c.drawText(info, cx, cy - h * 2.8f, paint)
            if (state == State.AUTH_LOADING) {
                retryAccountBtn.setEmpty()
                if (accountLoadError) {
                    retryAccountBtn.set(cx - w / 2f, cy, cx + w / 2f, cy + h)
                    drawButton(c, retryAccountBtn, "Повторить", colorPrimary, textSize = 42f)
                }
            } else {
                val labels = if (accountIsGuest) listOf("Регистрация", "Войти", "Восстановить пароль", "Назад")
                    else listOf("Восстановить пароль", "Выйти из аккаунта", "Назад")
                for (r in accountButtons) r.setEmpty()
                for ((i, label) in labels.withIndex()) {
                    val p = easeOutCubic((menuAnimProgress - i * 0.08f).coerceIn(0f, 1f))
                    if (p <= 0f) continue
                    val top = cy - h * 1.8f + i * h * 1.25f + (1f - p) * 100f
                    val rect = accountButtons[i]
                    rect.set(cx - w / 2f, top, cx + w / 2f, top + h)
                    val bg = when (label) { "Регистрация" -> colorPrimary; "Выйти из аккаунта" -> colorDanger; else -> colorSecondary }
                    drawButton(c, rect, label, bg, textSize = 42f)
                }
            }
            c.restore()
            return
        }

        if (state == State.CROPPING_BG) {
            c.drawColor(Color.parseColor("#FF222222"))
            val img = tempBgImage
            if (img != null) {
                c.save()
                c.translate(c.width / 2f + bgImageOffsetX, c.height / 2f + bgImageOffsetY)
                c.scale(bgScale, bgScale)
                val imgRect = RectF(-img.width / 2f, -img.height / 2f, img.width / 2f, img.height / 2f)
                c.drawBitmap(img, null, imgRect, paint)
                c.restore()

                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                c.drawRect(safe.left.toFloat(), safe.top.toFloat(), (c.width - safe.right).toFloat(), (c.height - safe.bottom).toFloat(), paint)
                paint.style = Paint.Style.FILL

                paint.color = Color.WHITE
                paint.textSize = 50f
                paint.textAlign = Paint.Align.CENTER
                c.drawText(t("Двигай и приближай фон"), c.width / 2f, safe.top + 100f, paint)

                saveBgCropBtnRect.set(c.width / 2f - 250f, c.height - safe.bottom - 150f, c.width / 2f + 250f, c.height - safe.bottom - 50f)
                drawButton(c, saveBgCropBtnRect, "Сохранить фон", Color.parseColor("#4CAF50"), textSize = 50f, cornerRadius = 20f)
            }
            c.restore()
            return
        }

        if (state == State.CROPPING_BALL) {
            c.drawColor(Color.parseColor("#FF222222"))
            val img = tempCropImage
            if (img != null) {
                val cx = c.width / 2f
                val cy = c.height / 2f
                val circleRadius = c.width * 0.35f

                c.save()
                c.translate(cx + cropImageOffsetX, cy + cropImageOffsetY)
                c.scale(cropScale, cropScale)
                val imgRect = RectF(-img.width / 2f, -img.height / 2f, img.width / 2f, img.height / 2f)
                c.drawBitmap(img, null, imgRect, paint)
                c.restore()

                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                c.drawCircle(cx, cy, circleRadius, paint)
                paint.style = Paint.Style.FILL

                paint.color = Color.WHITE
                paint.textSize = 50f
                paint.textAlign = Paint.Align.CENTER
                c.drawText(t("Двигай и приближай фото"), cx, safe.top + 100f, paint)

                saveCropBtnRect.set(cx - 250f, c.height - safe.bottom - 150f, cx + 250f, c.height - safe.bottom - 50f)
                drawButton(c, saveCropBtnRect, "Сохранить мяч", Color.parseColor("#4CAF50"), textSize = 50f, cornerRadius = 20f)
            }
            c.restore()
            return
        }

        if (bgImage != null) c.drawBitmap(bgImage!!, null, Rect(0, 0, c.width, c.height), paint)
        else c.drawColor(colorBg)

        if (state == State.PLAYING || state == State.PAUSED || state == State.GAME_OVER) {
            if (paddleFlashTimer > 0) paint.color = Color.WHITE else paint.color = colorPrimary
            c.drawRoundRect(paddleX - paddleW / 2, c.height - safe.bottom - 100f, paddleX + paddleW / 2, c.height - safe.bottom - 100f + paddleH, 20f, 20f, paint)

            for (p in particles) {
                if (p.isOrb) {
                    drawOrbIcon(c, p.x, p.y, 15f * (p.life / 1.5f))
                } else {
                    paint.color = p.color
                    paint.alpha = (p.life * 255).toInt()
                    c.drawCircle(p.x, p.y, 8f, paint)
                }
            }
            paint.alpha = 255

            if (ballImage != null) {
                c.save()
                val path = Path().apply { addCircle(ballX, ballY, ballR, Path.Direction.CW) }
                c.clipPath(path)
                val ballRect = RectF(ballX - ballR, ballY - ballR, ballX + ballR, ballY + ballR)
                c.drawBitmap(ballImage!!, null, ballRect, paint)
                c.restore()
            } else {
                paint.color = Color.WHITE
                c.drawCircle(ballX, ballY, ballR, paint)
            }

            if (currentMode == 3) {
                for (b in extraBalls) {
                    val animatedRadius = ballR * easeOutBack(b.age.coerceIn(0f, 1f)).coerceAtLeast(0.05f)
                    if (ballImage != null) {
                        c.save()
                        c.clipPath(Path().apply { addCircle(b.x, b.y, animatedRadius, Path.Direction.CW) })
                        c.drawBitmap(ballImage!!, null,
                            RectF(b.x - animatedRadius, b.y - animatedRadius, b.x + animatedRadius, b.y + animatedRadius), paint)
                        c.restore()
                    } else {
                        paint.color = Color.WHITE
                        c.drawCircle(b.x, b.y, animatedRadius, paint)
                    }
                }
            }

            if (currentMode == 3) {
                // Счётчик и уведомление используют существующие цвета и анимации BALL.
                paint.color = colorSubText
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = 28f
                c.drawText(t("МЯЧИ: ${1 + extraBalls.size}/5"), c.width - safe.right - 35f, safe.top + 130f, paint)
                if (multiBallNoticeTimer > 0f) {
                    val progress = (1f - multiBallNoticeTimer / 1.2f).coerceIn(0f, 1f)
                    val alpha = ((multiBallNoticeTimer / 0.35f).coerceIn(0f, 1f) * 255f).toInt()
                    paint.color = colorPrimary
                    paint.alpha = alpha
                    paint.textSize = 44f
                    paint.textAlign = Paint.Align.CENTER
                    val rise = 45f * easeOutCubic(progress)
                    c.drawText(t("+1 МЯЧ!"), c.width / 2f, safe.top + 215f - rise, paint)
                    paint.alpha = 255
                }
            }

            paint.textSize = 60f; paint.textAlign = Paint.Align.LEFT; paint.color = colorText
            c.drawText(t("$score"), safe.left + 40f, safe.top + 70f, paint)
            paint.textSize = 35f; paint.color = colorSubText
            c.drawText(t("Рекорд: $highScore"), safe.left + 40f, safe.top + 120f, paint)

            val btnSize = 60f
            pauseBtnRect.set(c.width - safe.right - btnSize - 30f, safe.top + 30f, c.width - safe.right - 30f, safe.top + 30f + btnSize)
            paint.color = colorText
            if (state == State.PLAYING) {
                val barW = 12f; val barH = 30f; val barY = pauseBtnRect.top + 15f
                c.drawRect(pauseBtnRect.centerX() - 15f, barY, pauseBtnRect.centerX() - 15f + barW, barY + barH, paint)
                c.drawRect(pauseBtnRect.centerX() + 3f, barY, pauseBtnRect.centerX() + 3f + barW, barY + barH, paint)
            }

            if (currentMode == 2) {
                paint.textSize = 50f
                paint.textAlign = Paint.Align.RIGHT
                c.drawText("❤️".repeat(lives), c.width - safe.right - 30f, safe.top + 150f, paint)
            }
        }

        if (state == State.MENU) {
            paint.color = Color.argb((230 * menuAnimProgress).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val helpR = 30f
            helpBtnRect.set(safe.left + 40f, safe.top + 40f, safe.left + 40f + helpR*2, safe.top + 40f + helpR*2)
            paint.color = colorSecondary
            c.drawCircle(helpBtnRect.centerX(), helpBtnRect.centerY(), helpR, paint)
            paint.color = colorText; paint.textSize = 35f; paint.textAlign = Paint.Align.CENTER
            c.drawText(t("?"), helpBtnRect.centerX(), helpBtnRect.centerY() + 12f, paint)

            val orbX = c.width - safe.right - 120f
            val orbY = safe.top + 80f
            drawOrbIcon(c, orbX, orbY, 25f)

            paint.color = colorText
            paint.textSize = 45f
            paint.textAlign = Paint.Align.LEFT
            c.drawText(t("$totalOrbs"), orbX + 40f, orbY + 15f, paint)

            if (isMenuPhysicsEnabled) {
                for (btn in fallingButtons) {
                    c.save()
                    c.translate(btn.x + btn.w / 2f, btn.y + btn.h / 2f)
                    c.rotate(btn.angle)
                    drawButton(c, RectF(-btn.w / 2f, -btn.h / 2f, btn.w / 2f, btn.h / 2f), btn.text, btn.color)
                    c.restore()
                }
            } else {
                val titleScale = if (isClosingMenu) 1f - easeInCubic(1f - menuAnimProgress) else easeOutCubic(menuAnimProgress)
                c.save()
                c.translate(c.width / 2f, safe.top + 300f)
                c.scale(titleScale, titleScale)
                paint.textAlign = Paint.Align.CENTER; paint.color = colorPrimary; paint.textSize = 160f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                c.drawText(t("ball"), 0f, 0f, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                c.restore()

                val accountW = min(300f, c.width * 0.45f)
                val accountY = safe.top + 65f + getMenuOffsetY(0.4f)
                accountBtnRect.set(c.width / 2f - accountW / 2f, accountY, c.width / 2f + accountW / 2f, accountY + 60f)
                drawButton(c, accountBtnRect, "Аккаунт", colorSecondary, textSize = 35f)
                val btnW = 550f; val btnH = 75f
                val left = c.width / 2f - btnW / 2f; val right = c.width / 2f + btnW / 2f

                val p1 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.1f).coerceIn(0f, 1f))
                if (p1 > 0 || isClosingMenu) {
                    c.save(); c.translate(0f, getMenuOffsetY(0.7f))
                    btnMenuPlay.set(left, safe.top + 480f, right, safe.top + 480f + btnH)
                    drawButton(c, btnMenuPlay, "Играть", colorPrimary, textSize = 50f)
                    c.restore()
                }

                val p2 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.15f).coerceIn(0f, 1f))
                if (p2 > 0 || isClosingMenu) {
                    c.save(); c.translate(0f, getMenuOffsetY(0.8f))
                    btnMenuModes.set(left, safe.top + 570f, right, safe.top + 570f + btnH)
                    drawButton(c, btnMenuModes, "Режимы", colorSecondary)
                    c.restore()
                }

                val pLeader = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.2f).coerceIn(0f, 1f))
                if (pLeader > 0 || isClosingMenu) {
                    c.save(); c.translate(0f, getMenuOffsetY(0.9f))
                    btnMenuLeaderboard.set(left, safe.top + 660f, right, safe.top + 660f + btnH)
                    drawButton(c, btnMenuLeaderboard, "Рейтинг", colorSecondary)
                    c.restore()
                }

                val p3 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.25f).coerceIn(0f, 1f))
                if (p3 > 0 || isClosingMenu) {
                    c.save(); c.translate(0f, getMenuOffsetY(1.0f))
                    btnMenuSettings.set(left, safe.top + 750f, right, safe.top + 750f + btnH)
                    drawButton(c, btnMenuSettings, "Настройки", colorSecondary)
                    c.restore()
                }

                val p4 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.3f).coerceIn(0f, 1f))
                if (p4 > 0 || isClosingMenu) {
                    c.save(); c.translate(0f, getMenuOffsetY(1.1f))
                    btnMenuAch.set(left, safe.top + 840f, right, safe.top + 840f + btnH)
                    drawButton(c, btnMenuAch, "Достижения", colorSecondary)
                    c.restore()
                }

                val p5 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.35f).coerceIn(0f, 1f))
                if (p5 > 0 || isClosingMenu) {
                    c.save(); c.translate(0f, getMenuOffsetY(1.2f))
                    btnMenuExit.set(left, safe.top + 930f, right, safe.top + 930f + btnH)
                    drawButton(c, btnMenuExit, "Выход", colorDanger)
                    c.restore()
                }
            }

            paint.color = colorSubText; paint.textSize = 35f; paint.textAlign = Paint.Align.LEFT
            c.drawText(t("by @lyambda123"), safe.left + 30f, c.height - safe.bottom - 30f, paint)
        }

        if (state == State.LEADERBOARD_MODES) {
            paint.color = Color.argb((255 * menuAnimProgress).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val titleScale = if (isClosingMenu) 1f - easeInCubic(1f - menuAnimProgress) else easeOutCubic(menuAnimProgress)
            c.save()
            c.translate(c.width / 2f, c.height / 2f - 350f)
            c.scale(titleScale, titleScale)
            paint.textAlign = Paint.Align.CENTER
            paint.color = colorText
            paint.textSize = 66f
            c.drawText(t("ВЫБЕРИ РЕЖИМ"), 0f, 0f, paint)
            c.restore()

            val labels = arrayOf("Классический", "Наклон", "Сердечки", "Мультишар")
            val btnW = 550f
            val btnH = 65f
            val left = c.width / 2f - btnW / 2f
            val right = left + btnW
            for (i in labels.indices) {
                val progress = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.1f - i * 0.1f).coerceIn(0f, 1f))
                val rect = leaderboardModeButtons[i]
                rect.setEmpty()
                if (progress > 0f || isClosingMenu) {
                    val offset = getMenuOffsetY(0.7f + i * 0.1f)
                    val top = c.height / 2f - 225f + i * 95f
                    val local = RectF(left, top, right, top + btnH)
                    c.save()
                    c.translate(0f, offset)
                    drawButton(c, local, labels[i], colorSecondary)
                    c.restore()
                    rect.set(local)
                    rect.offset(0f, offset)
                }
            }
            leaderboardModeBackBtn.setEmpty()
            val backProgress = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.55f).coerceIn(0f, 1f))
            if (backProgress > 0f || isClosingMenu) {
                val offset = getMenuOffsetY(1.1f)
                val top = c.height / 2f + 190f
                val local = RectF(left, top, right, top + btnH)
                c.save()
                c.translate(0f, offset)
                drawButton(c, local, "Назад", colorSecondary)
                c.restore()
                leaderboardModeBackBtn.set(local)
                leaderboardModeBackBtn.offset(0f, offset)
            }
        }

        if (state == State.LEADERBOARD) {
            paint.color = Color.argb((255 * menuAnimProgress).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val titleScale = if (isClosingMenu) 1f - easeInCubic(1f - menuAnimProgress) else easeOutCubic(menuAnimProgress)
            c.save()
            c.translate(c.width / 2f, safe.top + 120f)
            c.scale(titleScale, titleScale)
            paint.textAlign = Paint.Align.CENTER; paint.color = colorText; paint.textSize = 70f
            c.drawText(t("РЕЙТИНГ"), 0f, 0f, paint)
            c.restore()

            val modeNames = arrayOf("Классический", "Наклон", "Сердечки", "Мультишар")
            paint.textAlign = Paint.Align.CENTER
            paint.color = colorPrimary
            paint.textSize = 40f
            c.drawText(t("Режим: ${modeNames[leaderboardMode.coerceIn(0, 3)]}"), c.width / 2f, safe.top + 180f, paint)

            if (isLeaderboardLoading) {
                paint.color = colorSubText
                paint.textSize = 40f
                c.drawText(t("Загрузка..."), c.width / 2f, c.height / 2f, paint)
            } else if (leaderboardEntries.isEmpty()) {
                paint.color = colorSubText
                paint.textSize = 40f
                c.drawText(t("Пока нет рекордов :("), c.width / 2f, c.height / 2f, paint)
            } else {
                var yOff = safe.top + 240f
                for (i in 0 until min(leaderboardEntries.size, 8)) {
                    val entry = leaderboardEntries[i]
                    paint.textAlign = Paint.Align.LEFT
                    paint.textSize = 40f

                    paint.color = if (i == 0) Color.parseColor("#FFD700") else if (i == 1) Color.parseColor("#C0C0C0") else if (i == 2) Color.parseColor("#CD7F32") else colorSubText
                    c.drawText(t("${i + 1}."), safe.left + 60f, yOff, paint)

                    paint.color = colorText
                    c.drawText(entry.name, safe.left + 130f, yOff, paint)

                    paint.textAlign = Paint.Align.RIGHT
                    paint.color = colorPrimary
                    c.drawText(t("${entry.score}"), c.width - safe.right - 60f, yOff, paint)

                    yOff += 70f
                }
            }

            val pBack = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.3f).coerceIn(0f, 1f))
            if (pBack > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.0f))
                closeLeaderboardBtnRect.set(c.width / 2f - 250f, c.height - safe.bottom - 150f, c.width / 2f + 250f, c.height - safe.bottom - 50f)
                drawButton(c, closeLeaderboardBtnRect, "Назад", colorSecondary, textSize = 50f)
                c.restore()
            }
        }

        if (state == State.MODES) {
            paint.color = Color.argb((255 * menuAnimProgress).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val titleScale = if (isClosingMenu) 1f - easeInCubic(1f - menuAnimProgress) else easeOutCubic(menuAnimProgress)
            c.save()
            c.translate(c.width / 2f, c.height / 2f - 350f)
            c.scale(titleScale, titleScale)
            paint.textAlign = Paint.Align.CENTER; paint.color = colorText; paint.textSize = 70f
            c.drawText(t("РЕЖИМЫ"), 0f, 0f, paint)
            c.restore()

            val btnW = 550f; val btnH = 65f
            val left = c.width / 2f - btnW / 2f; val right = c.width / 2f + btnW / 2f

            val p1 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.1f).coerceIn(0f, 1f))
            if (p1 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.7f))
                btnModeClassic.set(left, c.height / 2f - 180f, right, c.height / 2f - 180f + btnH)
                drawButton(c, btnModeClassic, "Классический", if (currentMode == 0) colorPrimary else colorSecondary, textSize = 40f)
                c.restore()
            }

            val p2 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.15f).coerceIn(0f, 1f))
            if (p2 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.8f))
                btnModeTilt.set(left, c.height / 2f - 100f, right, c.height / 2f - 100f + btnH)
                drawButton(c, btnModeTilt, "Наклон", if (currentMode == 1) colorPrimary else colorSecondary, textSize = 40f)
                c.restore()
            }

            val p3 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.2f).coerceIn(0f, 1f))
            if (p3 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.9f))
                btnModeHearts.set(left, c.height / 2f - 20f, right, c.height / 2f - 20f + btnH)
                drawButton(c, btnModeHearts, "Сердечки", if (currentMode == 2) colorPrimary else colorSecondary, textSize = 40f)
                c.restore()
            }

            val pNew = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.25f).coerceIn(0f, 1f))
            if (pNew > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.0f))
                btnModeNew.set(left, c.height / 2f + 60f, right, c.height / 2f + 60f + btnH)
                drawButton(c, btnModeNew, "Мультишар", if (currentMode == 3) colorPrimary else colorSecondary, textSize = 40f)
                c.restore()
            }

            val p4 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.3f).coerceIn(0f, 1f))
            if (p4 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.1f))
                btnModeBack.set(left, c.height / 2f + 140f, right, c.height / 2f + 140f + btnH)
                drawButton(c, btnModeBack, "Назад", colorSecondary, textSize = 40f)
                c.restore()
            }
        }

        if (state == State.PAUSED) {
            paint.color = Color.argb((200 * menuAnimProgress).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val titleScale = if (isClosingMenu) 1f - easeInCubic(1f - menuAnimProgress) else easeOutCubic(menuAnimProgress)
            c.save()
            c.translate(c.width / 2f, c.height / 2f - 300f)
            c.scale(titleScale, titleScale)
            paint.textAlign = Paint.Align.CENTER; paint.color = colorText; paint.textSize = 80f
            c.drawText(t("ПАУЗА"), 0f, 0f, paint)
            c.restore()

            val btnW = 550f; val btnH = 75f
            val left = c.width / 2f - btnW / 2f; val right = c.width / 2f + btnW / 2f

            val p1 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.1f).coerceIn(0f, 1f))
            if (p1 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.8f))
                pauseResumeBtn.set(left, c.height / 2f - 180f, right, c.height / 2f - 180f + btnH)
                drawButton(c, pauseResumeBtn, "Продолжить", colorPrimary)
                c.restore()
            }

            val p2 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.15f).coerceIn(0f, 1f))
            if (p2 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.9f))
                pauseModesBtn.set(left, c.height / 2f - 90f, right, c.height / 2f - 90f + btnH)
                drawButton(c, pauseModesBtn, "Режимы", colorSecondary)
                c.restore()
            }

            val p3 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.2f).coerceIn(0f, 1f))
            if (p3 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.0f))
                pauseSettingsBtn.set(left, c.height / 2f, right, c.height / 2f + btnH)
                drawButton(c, pauseSettingsBtn, "Настройки", colorSecondary)
                c.restore()
            }

            val p4 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.25f).coerceIn(0f, 1f))
            if (p4 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.1f))
                pauseAchBtn.set(left, c.height / 2f + 90f, right, c.height / 2f + 90f + btnH)
                drawButton(c, pauseAchBtn, "Достижения", colorSecondary)
                c.restore()
            }

            val p5 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.3f).coerceIn(0f, 1f))
            if (p5 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.2f))
                pauseMainMenuBtn.set(left, c.height / 2f + 180f, right, c.height / 2f + 180f + btnH)
                drawButton(c, pauseMainMenuBtn, "В главное меню", colorDanger)
                c.restore()
            }
        }

        if (state == State.SETTINGS) {
            paint.color = Color.argb((255 * menuAnimProgress).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val titleScale = if (isClosingMenu) 1f - easeInCubic(1f - menuAnimProgress) else easeOutCubic(menuAnimProgress)
            c.save()
            c.translate(c.width / 2f, c.height / 2f - 450f)
            c.scale(titleScale, titleScale)
            paint.textAlign = Paint.Align.CENTER; paint.color = colorText; paint.textSize = 70f
            c.drawText(t("НАСТРОЙКИ"), 0f, 0f, paint)
            c.restore()

            val btnW = 550f; val btnH = 65f
            val left = c.width / 2f - btnW / 2f; val right = c.width / 2f + btnW / 2f

            val pNick = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.05f).coerceIn(0f, 1f))
            if (pNick > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.6f))
                btnSettingsChangeNick.set(left, c.height / 2f - 280f, right, c.height / 2f - 280f + btnH)
                drawButton(c, btnSettingsChangeNick, "Изменить ник", colorSecondary)
                c.restore()
            }

            val p1 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.1f).coerceIn(0f, 1f))
            if (p1 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.7f))
                bgBtnRect.set(left, c.height / 2f - 205f, right, c.height / 2f - 205f + btnH)
                drawButton(c, bgBtnRect, "Загрузить фон", colorSecondary)
                c.restore()
            }

            val p2 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.15f).coerceIn(0f, 1f))
            if (p2 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.8f))
                resetBgBtnRect.set(left, c.height / 2f - 130f, right, c.height / 2f - 130f + btnH)
                drawButton(c, resetBgBtnRect, "Сбросить фон", colorDanger)
                c.restore()
            }

            val p3 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.2f).coerceIn(0f, 1f))
            if (p3 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.9f))
                ballBtnRect.set(left, c.height / 2f - 55f, right, c.height / 2f - 55f + btnH)
                drawButton(c, ballBtnRect, "Загрузить мяч", colorSecondary)
                c.restore()
            }

            val p4 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.25f).coerceIn(0f, 1f))
            if (p4 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.0f))
                resetBallBtnRect.set(left, c.height / 2f + 20f, right, c.height / 2f + 20f + btnH)
                drawButton(c, resetBallBtnRect, "Сбросить мяч", colorDanger)
                c.restore()
            }

            val pColors = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.3f).coerceIn(0f, 1f))
            if (pColors > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.1f))
                colorsBtnRect.set(left, c.height / 2f + 95f, right, c.height / 2f + 95f + btnH)
                drawButton(c, colorsBtnRect, "Цвета частиц", colorSecondary)
                c.restore()
            }

            val pLanguage = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.35f).coerceIn(0f, 1f))
            if (pLanguage > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.2f))
                languageBtnRect.set(left, c.height / 2f + 170f, right, c.height / 2f + 170f + btnH)
                // Same button styling as other settings; short fade/scale on language change.
                val switchProgress = easeOutCubic(languageSwitchAnim)
                c.save()
                c.scale(0.96f + 0.04f * switchProgress, 0.96f + 0.04f * switchProgress,
                    languageBtnRect.centerX(), languageBtnRect.centerY())
                c.saveLayerAlpha(languageBtnRect.left - 10f, languageBtnRect.top - 10f,
                    languageBtnRect.right + 10f, languageBtnRect.bottom + 10f,
                    (120f + 135f * switchProgress).toInt())
                drawButton(c, languageBtnRect, if (language == "ru") "Язык: Русский" else "Language: English", colorSecondary, textSize = 38f)
                c.restore()
                c.restore()
                c.restore()
            }

            val p5 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.35f).coerceIn(0f, 1f))
            if (p5 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.2f))
                backBtnRect.set(left, c.height / 2f + 245f, right, c.height / 2f + 245f + btnH)
                drawButton(c, backBtnRect, "Назад", colorSecondary)
                c.restore()
            }
        }

        if (state == State.PARTICLE_COLORS) {
            paint.color = Color.argb((255 * menuAnimProgress).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val titleScale = if (isClosingMenu) 1f - easeInCubic(1f - menuAnimProgress) else easeOutCubic(menuAnimProgress)
            c.save()
            c.translate(c.width / 2f, c.height / 2f - 400f)
            c.scale(titleScale, titleScale)
            paint.textAlign = Paint.Align.CENTER; paint.color = colorText; paint.textSize = 70f
            c.drawText(t("ЦВЕТА ЧАСТИЦ"), 0f, 0f, paint)
            c.restore()

            val p1 = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.1f).coerceIn(0f, 1f))
            if (p1 > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(0.8f))

                val boxSize = 160f
                val spacing = 80f
                color1BoxRect.set(c.width / 2f - boxSize - spacing / 2f, c.height / 2f - 250f, c.width / 2f - spacing / 2f, c.height / 2f - 250f + boxSize)
                color2BoxRect.set(c.width / 2f + spacing / 2f, c.height / 2f - 250f, c.width / 2f + boxSize + spacing / 2f, c.height / 2f - 250f + boxSize)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (editingColor1) 10f else 4f
                paint.color = if (editingColor1) colorPrimary else colorSubText
                c.drawRoundRect(color1BoxRect, 20f, 20f, paint)

                paint.strokeWidth = if (!editingColor1) 10f else 4f
                paint.color = if (!editingColor1) colorPrimary else colorSubText
                c.drawRoundRect(color2BoxRect, 20f, 20f, paint)

                paint.style = Paint.Style.FILL
                paint.color = particleColor1
                c.drawRoundRect(color1BoxRect, 20f, 20f, paint)

                paint.color = particleColor2
                c.drawRoundRect(color2BoxRect, 20f, 20f, paint)

                paint.color = colorText
                paint.textSize = 35f
                paint.textAlign = Paint.Align.CENTER
                c.drawText(t("Цвет 1"), color1BoxRect.centerX(), color1BoxRect.bottom + 40f, paint)
                c.drawText(t("Цвет 2"), color2BoxRect.centerX(), color2BoxRect.bottom + 40f, paint)

                hueSliderRect.set(c.width / 2f - 400f, c.height / 2f - 20f, c.width / 2f + 400f, c.height / 2f + 40f)

                val hueColors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
                val huePositions = floatArrayOf(0f, 0.16f, 0.33f, 0.5f, 0.66f, 0.83f, 1f)
                val shader = LinearGradient(hueSliderRect.left, 0f, hueSliderRect.right, 0f, hueColors, huePositions, Shader.TileMode.CLAMP)

                val sliderPaint = Paint().apply { this.shader = shader; isAntiAlias = true }
                c.drawRoundRect(hueSliderRect, 20f, 20f, sliderPaint)

                val currentHue = if (editingColor1) hue1 else hue2
                val indicatorX = hueSliderRect.left + (currentHue / 360f) * hueSliderRect.width()

                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                c.drawCircle(indicatorX, hueSliderRect.centerY(), 25f, paint)
                paint.style = Paint.Style.FILL

                c.restore()
            }

            val pReset = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.2f).coerceIn(0f, 1f))
            if (pReset > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.0f))
                resetColorsBtnRect.set(c.width / 2f - 250f, c.height / 2f + 120f, c.width / 2f + 250f, c.height / 2f + 200f)
                drawButton(c, resetColorsBtnRect, "Сбросить цвета", colorDanger)
                c.restore()
            }

            val pBack = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.3f).coerceIn(0f, 1f))
            if (pBack > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.1f))
                backFromColorsBtnRect.set(c.width / 2f - 250f, c.height / 2f + 240f, c.width / 2f + 250f, c.height / 2f + 320f)
                drawButton(c, backFromColorsBtnRect, "Назад", colorSecondary)
                c.restore()
            }
        }

        if (state == State.ACHIEVEMENTS) {
            paint.color = Color.argb((255 * menuAnimProgress).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val titleScale = if (isClosingMenu) 1f - easeInCubic(1f - menuAnimProgress) else easeOutCubic(menuAnimProgress)
            c.save()
            c.translate(c.width / 2f, safe.top + 150f)
            c.scale(titleScale, titleScale)
            paint.textAlign = Paint.Align.CENTER; paint.color = colorText; paint.textSize = 70f
            c.drawText(t("ДОСТИЖЕНИЯ"), 0f, 0f, paint)
            c.restore()

            var yOffset = safe.top + 280f
            var index = 0
            for (ach in achievementsList) {
                val itemProgress = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.1f * index).coerceIn(0f, 1f))
                if (itemProgress > 0 || isClosingMenu) {
                    c.save()
                    val offY = if (isClosingMenu) easeInCubic(1f - menuAnimProgress) * 200f else (1f - itemProgress) * 200f
                    c.translate(0f, offY)

                    val itemRect = RectF(safe.left + 40f, yOffset - 50f, c.width - safe.right - 40f, yOffset + 70f)
                    paint.color = colorSecondary
                    c.drawRoundRect(itemRect, 16f, 16f, paint)

                    paint.textAlign = Paint.Align.LEFT
                    paint.textSize = 45f
                    if (ach.isUnlocked) {
                        paint.color = colorText
                        c.drawText("🏆 ${t(ach.title)}", itemRect.left + 30f, yOffset, paint)
                        paint.color = colorSubText; paint.textSize = 35f
                        c.drawText(t(ach.desc), itemRect.left + 30f, yOffset + 45f, paint)
                    } else {
                        paint.color = Color.DKGRAY
                        c.drawText(t("🔒 ???"), itemRect.left + 30f, yOffset, paint)
                        paint.color = Color.DKGRAY; paint.textSize = 35f
                        c.drawText(t("Еще не открыто"), itemRect.left + 30f, yOffset + 45f, paint)
                    }
                    c.restore()
                }
                yOffset += 150f
                index++
            }

            val btnW = 550f; val btnH = 75f
            val left = c.width / 2f - btnW / 2f; val right = c.width / 2f + btnW / 2f

            val pBack = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.5f).coerceIn(0f, 1f))
            if (pBack > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.0f))
                closeAchievementsBtnRect.set(left, c.height - safe.bottom - 250f, right, c.height - safe.bottom - 250f + btnH)
                drawButton(c, closeAchievementsBtnRect, "Назад", colorSecondary)
                c.restore()
            }

            val pReset = if (isClosingMenu) 1f else easeOutCubic((menuAnimProgress - 0.6f).coerceIn(0f, 1f))
            if (pReset > 0 || isClosingMenu) {
                c.save(); c.translate(0f, getMenuOffsetY(1.1f))
                resetAchievementsBtnRect.set(left, c.height - safe.bottom - 160f, right, c.height - safe.bottom - 160f + btnH)
                drawButton(c, resetAchievementsBtnRect, "Сбросить достижения", colorDanger)
                c.restore()
            }
        }

        if (state == State.GAME_OVER) {
            if (flashTimer > 0f) {
                val flashColor = if (isRedFlash) Color.RED else Color.WHITE
                paint.color = Color.argb((200 * (flashTimer / 0.2f)).toInt(), Color.red(flashColor), Color.green(flashColor), Color.blue(flashColor))
                c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)
            }

            paint.color = Color.argb((220 * easeOutQuad(gameOverAnimProgress)).toInt(), 18, 18, 18)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val textProgress = easeOutCubic((gameOverAnimProgress - 0.1f).coerceIn(0f, 1f))
            val textYOffset = -(1f - textProgress) * 200f

            c.save()
            c.translate(0f, textYOffset)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 80f

            if (isNewRecord) {
                paint.color = Color.parseColor("#FFD700")
                c.drawText(t("НОВЫЙ РЕКОРД!"), c.width / 2f, c.height / 2f - 180f, paint)
            } else {
                paint.color = colorText
                c.drawText(t("ИГРА ОКОНЧЕНА"), c.width / 2f, c.height / 2f - 180f, paint)
            }

            paint.color = colorSubText; paint.textSize = 50f
            c.drawText(t("Твой счет: $score"), c.width / 2f, c.height / 2f - 100f, paint)

            paint.color = colorOrb; paint.textSize = 60f
            c.drawText("+${orbCountAnim.toInt()} ${if (language == "en") "Orbs" else "Орбов"}", c.width / 2f, c.height / 2f - 20f, paint)

            c.restore()

            val btnProgress = easeOutCubic((gameOverAnimProgress - 0.3f).coerceIn(0f, 1f))
            if (btnProgress > 0) {
                c.save()
                c.translate(0f, (1f - btnProgress) * 100f)
                restartBtnRect.set(c.width / 2f - 250f, c.height / 2f + 80f, c.width / 2f + 250f, c.height / 2f + 160f)
                drawButton(c, restartBtnRect, "Начать заново", colorPrimary, textSize = 45f)
                c.restore()
            }
        }

        if (showAchievementPopup) {
            val elapsed = System.currentTimeMillis() - popupTriggerTime

            if (elapsed < 5000) {
                val popupW = c.width * 0.9f
                val popupH = 180f
                val finalY = safe.top + 30f
                val startY = -popupH - 50f

                var animProgress = 1f
                if (elapsed < 500) animProgress = elapsed / 500f
                else if (elapsed > 4500) animProgress = 1f - (elapsed - 4500) / 500f

                val currentY = startY + (finalY - startY) * easeOutCubic(animProgress.coerceIn(0f, 1f))

                popupRect.set(c.width / 2f - popupW / 2f, currentY, c.width / 2f + popupW / 2f, currentY + popupH)

                paint.color = colorSecondary
                c.drawRoundRect(popupRect, 16f, 16f, paint)
                paint.style = Paint.Style.STROKE; paint.color = Color.parseColor("#55FFFFFF"); paint.strokeWidth = 2f
                c.drawRoundRect(popupRect, 16f, 16f, paint); paint.style = Paint.Style.FILL

                paint.textAlign = Paint.Align.CENTER

                paint.color = Color.parseColor("#FFD700")
                paint.textSize = 35f
                c.drawText(t("новое достижение!"), popupRect.centerX(), popupRect.top + 45f, paint)

                paint.color = colorText
                paint.textSize = 50f
                c.drawText(t(popupTitle), popupRect.centerX(), popupRect.top + 105f, paint)

                paint.color = colorSubText
                paint.textSize = 30f
                c.drawText(t("коснись чтобы посмотреть описание"), popupRect.centerX(), popupRect.top + 155f, paint)
            }
        }

        if (showConfirmDialog) {
            paint.color = Color.argb((150 * confirmDialogAnim).toInt(), 0, 0, 0)
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

            val animScale = easeOutBack(confirmDialogAnim)

            c.save()
            c.translate(c.width / 2f, c.height / 2f)
            c.scale(animScale, animScale)

            val dialogW = 800f
            val dialogH = 400f
            val dialogRect = RectF(-dialogW/2, -dialogH/2, dialogW/2, dialogH/2)

            paint.color = colorSecondary
            c.drawRoundRect(dialogRect, 30f, 30f, paint)

            paint.color = colorText
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            c.drawText(t(confirmText), 0f, -40f, paint)

            val btnW = 300f
            val btnH = 80f
            confirmYesBtnRect.set(-btnW - 20f, 50f, -20f, 50f + btnH)
            confirmNoBtnRect.set(20f, 50f, 20f + btnW, 50f + btnH)

            drawButton(c, confirmYesBtnRect, "Да", colorDanger, textSize = 45f)
            drawButton(c, confirmNoBtnRect, "Отмена", colorPrimary, textSize = 45f)

            c.restore()
        }

        c.restore()
    }

    enum class State { MENU, NAME_ENTRY, MODES, PLAYING, PAUSED, SETTINGS, ACHIEVEMENTS, GAME_OVER, CROPPING_BALL, CROPPING_BG, PARTICLE_COLORS, LEADERBOARD, LEADERBOARD_MODES, AUTH_LOADING, ACCOUNT }
}

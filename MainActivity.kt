package com.lyambda.ball

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private lateinit var accounts: AccountManager
    private val blue = Color.parseColor("#2979FF")
    private val bg = Color.parseColor("#121212")
    private val secondary = Color.parseColor("#2A2A2A")
    private var starting = false
    private var recreating = false
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (::gameView.isInitialized && active()) {
            if (firebaseAuth.currentUser?.uid != gameView.game.playerId) recreateOnce()
            else gameView.game.accountIsGuest = firebaseAuth.currentUser?.isAnonymous == true
        }
    }

    private fun recreateOnce() {
        if (!active() || recreating) return
        recreating = true
        if (::gameView.isInitialized) gameView.game.accountBusy = true
        recreate()
    }

    override fun onStart() {
        super.onStart()
        if (::accounts.isInitialized) FirebaseAuth.getInstance().addAuthStateListener(authListener)
    }

    override fun onStop() {
        if (::accounts.isInitialized) FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        super.onStop()
    }
    private var accountDialog: AlertDialog? = null
    private val language: String get() = if (::gameView.isInitialized) gameView.game.language else getSharedPreferences("GamePrefs", MODE_PRIVATE).getString("ui_language", "en") ?: "en"
    private fun tr(ru: String, en: String) = if (language == "en") en else ru
    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun active() = !isFinishing && !isDestroyed

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && ::gameView.isInitialized) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val w = info.size.width; val h = info.size.height
                        if (w > 1920 || h > 1920) {
                            val ratio = minOf(1920f / w, 1920f / h)
                            decoder.setTargetSize((w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1))
                        }
                    }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1920) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                        ?: throw IllegalArgumentException("Invalid image")
                }
                if (gameView.isPickingBackground) gameView.setBackgroundImage(bitmap) else gameView.setBallCropImage(bitmap)
            } catch (_: Exception) { toast(tr("Ошибка загрузки фото", "Could not load image")) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The Google Services plugin reads the local google-services.json. No embedded API key.
        try {
            if (FirebaseApp.getApps(this).isEmpty() && FirebaseApp.initializeApp(this) == null) {
                showStartup(tr("Проверь google-services.json и плагин Google Services", "Check google-services.json and the Google Services plugin"), false)
                return
            }
            accounts = AccountManager()
        } catch (_: Exception) {
            showStartup(tr("Firebase не настроен", "Firebase is not configured"), false)
            return
        }
        startSession()
    }

    private fun showStartup(message: String, retry: Boolean) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }
        panel.addView(TextView(this).apply { text = "ball"; textSize = 64f; setTextColor(blue); gravity = android.view.Gravity.CENTER })
        panel.addView(TextView(this).apply { text = message; textSize = 16f; setTextColor(Color.WHITE); gravity = android.view.Gravity.CENTER; setPadding(0, dp(16), 0, dp(16)) })
        if (retry) panel.addView(Button(this).apply {
            text = tr("Повторить", "Retry"); backgroundTintList = ColorStateList.valueOf(blue); setTextColor(Color.WHITE)
            setOnClickListener { startSession() }
        })
        setContentView(panel)
        immersive(panel)
    }

    private fun startSession() {
        if (starting) return
        starting = true
        showStartup(tr("Подключение...", "Connecting..."), false)
        accounts.ensureGuest { ok ->
            if (!active()) return@ensureGuest
            starting = false
            if (ok) attachGame() else showStartup(tr("Не удалось войти. Проверь интернет и анонимную авторизацию.", "Sign-in failed. Check your connection and anonymous authentication."), true)
        }
    }

    private fun immersive(view: android.view.View) {
        WindowCompat.getInsetsController(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun attachGame() {
        gameView = GameView(this)
        val root = FrameLayout(this)
        root.addView(gameView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        gameView.onShowKeyboard = { nicknameDialog() }
        gameView.onHideKeyboard = { /* Native input dialog closes on confirmation. */ }
        gameView.onPickImageRequested = { background ->
            gameView.isPickingBackground = background
            pickImageLauncher.launch("image/*")
        }
        gameView.onExitRequested = { finish() }
        gameView.onAccountAction = { action ->
            if (action == "logout") confirmLogout() else authDialog(action)
        }
        immersive(gameView)
        ViewCompat.setOnApplyWindowInsetsListener(gameView) { _, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            gameView.setSafeArea(maxOf(sb.left, cut.left), maxOf(sb.top, cut.top), maxOf(sb.right, cut.right), maxOf(sb.bottom, cut.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(gameView)
        gameView.post { gameView.loadAccountProfile() }
    }

    private fun field(hintText: String, type: Int): EditText = EditText(this).apply {
        hint = hintText
        setSingleLine(true)
        inputType = type
        setTextColor(Color.WHITE)
        setHintTextColor(Color.LTGRAY)
        background = GradientDrawable().apply { setColor(secondary); cornerRadius = dp(12).toFloat(); setStroke(dp(1), blue) }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }

    private fun dialogContent() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(8))
    }

    private fun styleDialog(dialog: AlertDialog, secure: Boolean = false) {
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply { setColor(bg); cornerRadius = dp(18).toFloat() })
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(blue)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.LTGRAY)
        if (secure) dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.window?.decorView?.let { view ->
            view.alpha = 0f; view.scaleX = 0.96f; view.scaleY = 0.96f
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        }
    }

    private fun nicknameDialog() {
        if (accountDialog?.isShowing == true) return
        val edit = field(tr("Ник", "Nickname"), InputType.TYPE_CLASS_TEXT)
        edit.setText(gameView.game.playerName)
        edit.setSelection(edit.text.length)
        val panel = dialogContent(); panel.addView(edit)
        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(tr("Ник игрока", "Player nickname"))
            .setView(panel).setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Готово", "Done")) { _, _ -> gameView.game.playerName = edit.text.toString() }
            .create()
        accountDialog = dialog
        dialog.setOnDismissListener { accountDialog = null; immersive(gameView) }
        dialog.show(); styleDialog(dialog); edit.requestFocus()
    }

    private fun authDialog(action: String) {
        if (accountDialog?.isShowing == true || gameView.game.accountBusy) return
        val isRegister = action == "register"
        val reset = action == "reset"
        if (!isRegister && !reset && action != "login") return
        val title = when (action) {
            "register" -> tr("Регистрация", "Register")
            "login" -> tr("Вход", "Sign in")
            else -> tr("Восстановление пароля", "Reset password")
        }
        val panel = dialogContent()
        val note = when (action) {
            "register" -> tr("Привяжи почту и пароль к текущему гостевому профилю.", "Link email and password to your current guest profile.")
            "login" -> tr("Вход в другой аккаунт заменит гостевую сессию. Если хочешь сохранить гостевой профиль, сначала зарегистрируй его.", "Signing in replaces the guest session. Register the guest profile first if you want to keep it.")
            else -> tr("Введи почту аккаунта. Если аккаунт существует, на неё придёт письмо.", "Enter the account email. If the account exists, a reset email will be sent.")
        }
        panel.addView(TextView(this).apply { text = note; setTextColor(Color.LTGRAY); textSize = 14f })
        val email = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        if (reset) email.setText(accounts.email)
        panel.addView(email)
        val password = if (!reset) field(tr("Пароль", "Password"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) else null
        val confirm = if (isRegister) field(tr("Повтори пароль", "Confirm password"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) else null
        password?.let { panel.addView(it) }; confirm?.let { panel.addView(it) }
        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(title).setView(panel)
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Готово", "Done"), null).create()
        accountDialog = dialog
        dialog.setOnDismissListener {
            password?.text?.clear(); confirm?.text?.clear()
            accountDialog = null
            if (active()) immersive(gameView)
        }
        dialog.show(); styleDialog(dialog, secure = !reset); email.requestFocus()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val address = email.text.toString().trim()
            val secret = password?.text?.toString().orEmpty()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.error = tr("Неверная почта", "Invalid email"); return@setOnClickListener }
            if (!reset && secret.isEmpty()) { password?.error = tr("Введи пароль", "Enter password"); return@setOnClickListener }
            if (isRegister && secret.length < 8) { password?.error = tr("Минимум 8 символов", "At least 8 characters"); return@setOnClickListener }
            if (isRegister && secret != confirm?.text?.toString()) { confirm?.error = tr("Пароли не совпадают", "Passwords do not match"); return@setOnClickListener }
            accounts.setLanguage(language)
            gameView.game.accountBusy = true
            dialog.dismiss()
            when (action) {
                "register" -> accounts.register(address, secret) { ok ->
                    if (!active()) return@register
                    gameView.game.accountBusy = false
                    if (ok) {
                        gameView.game.accountIsGuest = false
                        toast(tr("Аккаунт создан! Гостевой UID сохранён.", "Account created! Guest UID preserved."))
                    } else toast(tr("Регистрация не удалась. Проверь почту, пароль и подключение.", "Registration failed. Check email, password and connection."))
                }
                "login" -> accounts.login(address, secret) { ok ->
                    if (!active()) return@login
                    if (ok) {
                        // Keep the old view blocked until recreation replaces its account-specific data.
                        recreateOnce()
                    } else {
                        gameView.game.accountBusy = false
                        toast(tr("Не удалось войти. Проверь данные и подключение.", "Sign-in failed. Check credentials and connection."))
                    }
                }
                else -> accounts.resetPassword(address) { ok ->
                    if (!active()) return@resetPassword
                    gameView.game.accountBusy = false
                    toast(if (ok) tr("Если аккаунт существует, письмо отправлено.", "If the account exists, a reset email was sent.") else tr("Не удалось отправить запрос. Проверь подключение и попробуй снова.", "Request failed. Check your connection and try again."))
                }
            }
        }
    }

    private fun confirmLogout() {
        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(tr("Выйти из аккаунта?", "Sign out?"))
            .setMessage(tr("Ты сможешь войти снова по почте и паролю. На другом устройстве доступен только уже синхронизированный прогресс.", "You can sign in again using email and password. Only synced progress is available on another device."))
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Выйти", "Sign out")) { _, _ ->
                gameView.game.accountBusy = true
                accounts.logout(); recreateOnce()
            }.create()
        dialog.show(); styleDialog(dialog)
    }

    override fun onDestroy() {
        accountDialog?.dismiss()
        super.onDestroy()
    }
}

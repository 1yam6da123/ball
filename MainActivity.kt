package com.lyambda.ball

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private lateinit var hiddenEditText: EditText

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val source = ImageDecoder.createSource(contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxSize = 1920
                    val width = info.size.width
                    val height = info.size.height
                    if (width > maxSize || height > maxSize) {
                        val ratio = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
                        decoder.setTargetSize((width * ratio).toInt(), (height * ratio).toInt())
                    }
                }

                if (gameView.isPickingBackground) {
                    gameView.setBackgroundImage(bitmap)
                } else {
                    gameView.setBallCropImage(bitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyBPzj4D-J3GrBRR8rDHZ5fCW5SBNY6Ey74")
                .setApplicationId("1:33848083480:android:f13007f7d3b53cff43af6d")
                .setProjectId("ball-6742")
                .build()
            FirebaseApp.initializeApp(this, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        gameView = GameView(this)

        hiddenEditText = EditText(this).apply {
            visibility = View.INVISIBLE
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    gameView.game.playerName = s.toString()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard()
                    true
                } else false
            }
        }

        val rootLayout = FrameLayout(this)
        rootLayout.addView(gameView)
        rootLayout.addView(hiddenEditText)
        setContentView(rootLayout)

        gameView.onShowKeyboard = {
            // Подставляем текущее имя для редактирования
            hiddenEditText.setText(gameView.game.playerName)
            hiddenEditText.setSelection(hiddenEditText.text.length)

            hiddenEditText.visibility = View.VISIBLE
            hiddenEditText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenEditText, InputMethodManager.SHOW_IMPLICIT)
        }

        gameView.onHideKeyboard = {
            hideKeyboard()
        }

        gameView.onPickImageRequested = { isBackground ->
            gameView.isPickingBackground = isBackground
            pickImageLauncher.launch("image/*")
        }

        gameView.onExitRequested = { finish() }

        WindowCompat.getInsetsController(window, gameView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        ViewCompat.setOnApplyWindowInsetsListener(gameView) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            gameView.setSafeArea(
                left = maxOf(sb.left, cut.left),
                top = maxOf(sb.top, cut.top),
                right = maxOf(sb.right, cut.right),
                bottom = maxOf(sb.bottom, cut.bottom)
            )
            insets
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(hiddenEditText.windowToken, 0)
        hiddenEditText.visibility = View.INVISIBLE
    }
}

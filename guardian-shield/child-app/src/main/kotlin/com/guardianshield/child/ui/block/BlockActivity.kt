package com.guardianshield.child.ui.block

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class BlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make fullscreen and exclude from recents
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Disable back button completely
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to enforce parental control block
            }
        })

        // Programmatically create premium, beautiful fullscreen layout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            // Harmonious HSL-driven deep dark background gradient
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#141419"), Color.parseColor("#08080A"))
            )
            setPadding(48, 48, 48, 48)
        }

        // Lock Icon Container (with subtle gradient ring outline)
        val iconContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(260, 260).apply {
                bottomMargin = 64
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1F1B24"))
                setStroke(6, GradientDrawable(
                    GradientDrawable.Orientation.TR_BL,
                    intArrayOf(Color.parseColor("#FF3366"), Color.parseColor("#FF9933"))
                ).let { Color.parseColor("#FF3F5F") }) // Solid border fallback
            }
        }

        // Programmatic Pulsing Alert Icon
        val alertIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_lock_idle_lock)
            setColorFilter(Color.parseColor("#FF3F5F"))
            layoutParams = LinearLayout.LayoutParams(120, 120)
        }
        iconContainer.addView(alertIcon)

        // Pulsing micro-animation for Lock Icon
        ObjectAnimator.ofFloat(alertIcon, "scaleX", 1f, 1.15f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofFloat(alertIcon, "scaleY", 1f, 1.15f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        // Restricted Status Pill Label
        val warningPill = TextView(this).apply {
            text = "ACCESS RESTRICTED"
            setTextColor(Color.parseColor("#FF3F5F"))
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(32, 12, 32, 12)
            background = GradientDrawable().apply {
                cornerRadius = 100f
                setColor(Color.parseColor("#2D1B22"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 48
            }
        }

        // Main Title (Vibrant, Bold typography)
        val titleText = TextView(this).apply {
            text = "App is Locked"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        // Secondary descriptive message text
        val descriptionText = TextView(this).apply {
            text = "Your parent has restricted access to this application to keep you safe and focused."
            setTextColor(Color.parseColor("#A0A0AB"))
            textSize = 15f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 16
                rightMargin = 16
            }
        }

        // Build view hierarchy
        rootLayout.addView(iconContainer)
        rootLayout.addView(warningPill)
        rootLayout.addView(titleText)
        rootLayout.addView(descriptionText)

        setContentView(rootLayout)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            // Close system dialogs (like status bar pull-downs or power option clicks)
            val closeDialogs = android.content.Intent(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeDialogs)
        }
    }
}

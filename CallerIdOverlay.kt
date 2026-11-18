package com.example.hiyaclone

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

object CallerIdOverlay {
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null

    fun show(context: Context, phone: String, name: String, category: String, spamScore: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        Handler(Looper.getMainLooper()).post {
            dismiss()

            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(45, 35, 45, 35)
                val isDanger = category == "SPAM" || category == "FRAUD" || spamScore >= 70
                setBackgroundColor(if (isDanger) Color.parseColor("#C62828") else Color.parseColor("#1565C0"))
            }

            val tvTitle = TextView(context).apply {
                text = if (spamScore >= 70) "⚠️ CẢNH BÁO SPAM ($spamScore%)" else "ℹ️ NHẬN DIỆN NGƯỜI GỌI"
                setTextColor(Color.YELLOW)
                textSize = 13f
            }

            val tvName = TextView(context).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 20f
            }

            val tvPhone = TextView(context).apply {
                text = "Số: $phone • Chạm để đóng"
                setTextColor(Color.LTGRAY)
                textSize = 14f
            }

            layout.addView(tvTitle)
            layout.addView(tvName)
            layout.addView(tvPhone)
            layout.setOnClickListener { dismiss() }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            overlayView = layout
            windowManager?.addView(overlayView, params)
            Handler(Looper.getMainLooper()).postDelayed({ dismiss() }, 15000)
        }
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) { }
            overlayView = null
        }
    }
}

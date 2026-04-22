package kr.ac.kopo.talkti

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.ac.kopo.talkti.features.images.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.ac.kopo.talkti.data.remote.NetworkRepository // 패키지 경로 확인!
import android.util.Log
import kr.ac.kopo.talkti.features.voice.VoiceToTextParser
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private lateinit var voiceParser: VoiceToTextParser
    private var overlayTextView: TextView? = null
    private var overlayMicButton: Button? = null

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceParser.startListening()
        } else {
            Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            lifecycleScope.launch {
                delay(500)
                startScreenCaptureService(result.resultCode, result.data!!)
            }
        } else {
            Toast.makeText(this, "화면 캡처 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            showOverlay()
        } else {
            Toast.makeText(this, "오버레이 권한이 허용되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        voiceParser = VoiceToTextParser(application)

        lifecycleScope.launch {
            voiceParser.state.collect { state ->
                overlayTextView?.let { tv ->
                    if (state.spokenText.isNotEmpty()) {
                        tv.text = state.spokenText
                    } else if (state.isSpeaking) {
                        tv.text = "듣는 중..."
                    } else if (state.error != null) {
                        tv.text = "오류: ${state.error}\n마이크 버튼을 다시 눌러주세요"
                    } else {
                        tv.text = "똑띠 오버레이 작동 중!\n마이크 버튼을 눌러주세요"
                    }
                }
                
                overlayMicButton?.let { btn ->
                    if (state.isSpeaking) {
                        btn.text = "마이크 끄기"
                    } else {
                        btn.text = "마이크 켜기"
                    }
                }
            }
        }

        val btnCapture = findViewById<Button>(R.id.btnCapture)
        btnCapture.setOnClickListener {
            requestScreenCapture()
        }

        val btnOverlayStart = findViewById<Button>(R.id.btnOverlayStart)
        btnOverlayStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                showOverlay()
            }
        }

        val btnOverlayStop = findViewById<Button>(R.id.btnOverlayStop)
        btnOverlayStop.setOnClickListener {
            removeOverlay()
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#99000000")) // 반투명 검은색 배경
            setPadding(48, 48, 48, 48)
        }

        val textView = TextView(this).apply {
            text = "똑띠 오버레이 작동 중!\n마이크 버튼을 눌러주세요"
            setTextColor(Color.WHITE)
            textSize = 20f
        }
        overlayTextView = textView

        val micButton = Button(this).apply {
            text = "마이크 켜기"
            setOnClickListener {
                if (text == "마이크 켜기") {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        voiceParser.startListening()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    voiceParser.stopListening()
                }
            }
        }
        overlayMicButton = micButton

        layout.addView(textView)
        layout.addView(micButton)
        overlayView = layout

        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
            overlayTextView = null
            overlayMicButton = null
            voiceParser.stopListening()
        }
    }

    private fun requestScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }

    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "권한 설정 타이밍 문제로 실행되지 않았습니다. 다시 눌러주세요.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}
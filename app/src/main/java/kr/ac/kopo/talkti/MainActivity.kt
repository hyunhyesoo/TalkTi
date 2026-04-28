package kr.ac.kopo.talkti

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.ac.kopo.talkti.features.accessibility.TalkTiAccessibilityService // 👈 팀원 파일 import 추가!
import kr.ac.kopo.talkti.features.images.ScreenCaptureService
import kr.ac.kopo.talkti.features.overlay.OverlayView
import kr.ac.kopo.talkti.features.voice.VoiceToTextParser

class MainActivity : ComponentActivity() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private lateinit var voiceParser: VoiceToTextParser
    private var overlayTextView: TextView? = null
    private var overlayMicButton: Button? = null
    private lateinit var customOverlayView: OverlayView

    // 1. AccessibilityNodeInfo에서 ID로 뷰 찾기
    fun findNodeById(rootNode: AccessibilityNodeInfo?, targetId: String) {
        if (rootNode == null) return
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(targetId)
        if (nodes.isNotEmpty()) {
            val targetNode = nodes[0]
            val rect = Rect()
            targetNode.getBoundsInScreen(rect)
            drawRedBoxOverlay(rect)
        } else {
            Toast.makeText(this, "해당 ID의 뷰를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawRedBoxOverlay(rect: Rect) {
        customOverlayView.updateRect(rect)
    }

    private fun removeRedBoxOverlay() {
        customOverlayView.updateRect(null)
    }

    // --- 권한 요청 런처들 ---
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) voiceParser.startListening()
        else Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            lifecycleScope.launch {
                delay(500)
                startScreenCaptureService(result.resultCode, result.data!!)
            }
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        customOverlayView = findViewById(R.id.overlayView)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        voiceParser = VoiceToTextParser(application)

        // 🟢 [수정 포인트 1] lifecycleScope와 collect 블록을 여기서 닫아줘야 합니다.
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
                    btn.text = if (state.isSpeaking) "마이크 끄기" else "마이크 켜기"
                }

                state.lastResponse?.let { response ->
                    if (response.status == "overlay_command") {
                        val rootNode = TalkTiAccessibilityService.instance?.rootInActiveWindow
                        if (rootNode != null) {
                            findNodeById(rootNode, response.target_id)
                        } else {
                            Log.e("OVERLAY", "접근성 서비스가 연결되지 않았거나 권한이 없습니다.")
                        }
                    } else if (response.status == "clear") {
                        removeRedBoxOverlay()
                    }
                }
            } // collect 끝
        } // lifecycleScope 끝 (이게 안 닫혀 있어서 아래 버튼들이 에러가 났던 거예요!)

        // 🟢 [수정 포인트 2] 버튼 리스너들은 onCreate의 메인 흐름에 있어야 합니다.
        val btnCapture = findViewById<Button>(R.id.btnCapture)
        btnCapture.setOnClickListener {
            requestScreenCapture()
        }

        val btnOverlayStart = findViewById<Button>(R.id.btnOverlayStart)
        btnOverlayStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
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
            setBackgroundColor(Color.parseColor("#99000000"))
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
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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
        removeRedBoxOverlay()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        if (::voiceParser.isInitialized) voiceParser.destroy()
    }
}
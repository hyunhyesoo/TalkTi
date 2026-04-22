package kr.ac.kopo.talkti.features.images

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.ac.kopo.talkti.data.remote.NetworkRepository
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // 전송 간격 제어를 위한 최근 전송 시간 기억 (밀리초)
    private var lastSendTime = 0L

    companion object {
        private const val CHANNEL_ID = "ScreenCaptureServiceChannel"
        private const val NOTIFICATION_ID = 1

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("화면 캡처 중")
            .setContentText("화면 캡처 서비스가 백그라운드에서 실행 중입니다.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        // 1. Android 14 정책 엄수: 포그라운드 서비스 활성화를 무조건 가장 먼저(최우선) 실행합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            @Suppress("DEPRECATION")
            val resultData: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)

            if (resultCode != 0 && resultData != null) {
                // 2. startForeground 완료 직후에 권한 객체(MediaProjection) 획득
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)

                // 3. 미디어 프로젝션 객체가 성공적으로 생성되면 화면 캡처 시작
                if (mediaProjection != null) {
                    setupVirtualDisplay()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // ImageReader 설정
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        // Android 14 정책: 가상 디스플레이 생성 전에 반드시 콜백을 등록해야 함
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopCapture() // 캡처가 중단되면 즉시 리소스 해제
            }
        }
        mediaProjection?.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val currentTime = System.currentTimeMillis()
                    // 2. 3초에 한 번만 전송 (전송 간격 제어 로직)
                    if (currentTime - lastSendTime >= 3000) {
                        lastSendTime = currentTime

                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)

                        val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                        // 1. 가로 720px 해상도로 리사이징
                        val targetWidth = 720
                        val ratio = targetWidth.toFloat() / width.toFloat()
                        val targetHeight = (height.toFloat() * ratio).toInt()
                        val resizedBitmap = Bitmap.createScaledBitmap(finalBitmap, targetWidth, targetHeight, true)

                        // 2. 용량 체크 루프 & 하한선(30)
                        var quality = 80
                        var byteArray: ByteArray

                        do {
                            val outputStream = ByteArrayOutputStream()
                            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                            byteArray = outputStream.toByteArray()

                            // 100KB 이하이거나 품질이 30 이하이면 중단
                            if (byteArray.size <= 100 * 1024 || quality <= 30) {
                                break
                            }
                            quality -= 10
                        } while (true)

                        // 전송 직전 로그 출력
                        Log.d("ScreenCapture", "최종 용량: ${byteArray.size / 1024} KB, 적용 품질: ${quality}%")

                        // 3. 트리 추출 로직 연동
                        val treeJson = kr.ac.kopo.talkti.features.accessibility.TalkTiAccessibilityService.extractScreenTreeJson()
                        
                        // ANALYSIS_TREE 로그캣 분할 출력 (4000자씩)
                        val logText = treeJson ?: "{}"
                        Log.d("ANALYSIS_TREE", "--- 화면 트리 JSON 추출 결과 --- (총 길이: ${logText.length})")
                        val maxLogSize = 4000
                        for (i in 0..logText.length / maxLogSize) {
                            val start = i * maxLogSize
                            val end = if ((i + 1) * maxLogSize < logText.length) (i + 1) * maxLogSize else logText.length
                            if (start < end) {
                                Log.d("ANALYSIS_TREE", logText.substring(start, end))
                            }
                        }
                        Log.d("ANALYSIS_TREE", "-----------------------------------")

                        // 4. 비동기(Coroutine)로 서버에 통합 전송
                        CoroutineScope(Dispatchers.IO).launch {
                            val repository = NetworkRepository()
                            val isSuccess = repository.sendScreenAnalysisToServer(byteArray, logText)
                            
                            // 5. 전송 결과 로그캣 출력
                            if (isSuccess) {
                                Log.d("ScreenCapture", "화면 분석(이미지+트리) 전송 성공! (이미지 크기: ${byteArray.size} bytes)")
                            } else {
                                Log.e("ScreenCapture", "화면 분석(이미지+트리) 전송 실패 ㅠㅠ")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image?.close()
            }
        }, null)
    }
    
    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
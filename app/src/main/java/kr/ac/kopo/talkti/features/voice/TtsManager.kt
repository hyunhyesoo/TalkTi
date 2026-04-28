package kr.ac.kopo.talkti.features.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    // 메모리 누수 방지를 위해 applicationContext 사용
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        // TTS 엔진 초기화 시작
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "한국어 지원 불가: 설정에서 한국어 음성 데이터를 확인해주세요.")
            } else {
                isInitialized = true
                Log.d("TtsManager", "TTS 엔진 초기화 성공 및 한국어 설정 완료")
            }
        } else {
            Log.e("TtsManager", "TTS 초기화 실패 (에러 코드: $status)")
        }
    }

    /**
     * @param text 출력할 문장
     * @param isQueueAdd true면 이전 말을 다 하고 이어서 말함, false면 이전 말을 끊고 즉시 말함
     */
    fun speak(text: String, isQueueAdd: Boolean = false) {
        if (isInitialized && tts != null) {
            val queueMode = if (isQueueAdd) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
            tts?.speak(text, queueMode, null, "TalkTiMsgID")
            Log.d("TtsManager", "말하기 실행: $text (모드: ${if (isQueueAdd) "추가" else "새로침"})")
        } else {
            Log.w("TtsManager", "TTS 미준비 상태로 무시됨: $text")
        }
    }

    fun stop() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    // 서비스나 액티비티가 종료될 때 반드시 호출
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d("TtsManager", "TTS 리소스 해제 완료")
    }
}
package kr.ac.kopo.talkti.features.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "한국어를 지원하지 않거나 데이터가 없습니다.")
            } else {
                isInitialized = true
            }
        } else {
            Log.e("TtsManager", "TTS 초기화 실패")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w("TtsManager", "TTS가 아직 초기화되지 않았습니다.")
        }
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

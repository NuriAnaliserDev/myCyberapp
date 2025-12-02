package com.example.cyberapp

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceAlertManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceAlertManager", "The Language specified is not supported!")
            } else {
                isInitialized = true
            }
        } else {
            Log.e("VoiceAlertManager", "Initialization Failed!")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        } else {
            Log.e("VoiceAlertManager", "TTS not initialized yet")
        }
    }

    fun shutdown() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
    }
}

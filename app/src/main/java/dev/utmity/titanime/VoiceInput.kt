package dev.utmity.titanime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Main-thread session. Invalidate before destroy so late/duplicate callbacks cannot insert. */
internal class VoiceInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val gate = VoiceSessionGate()
    private var retryPending = false
    val active get() = recognizer != null || retryPending
    fun cancel() {
        gate.invalidate()
        retryPending = false
        val old = recognizer
        recognizer = null
        old?.cancel(); old?.destroy()
    }
    fun start(language: String, status: (String) -> Unit, result: (String) -> Unit) =
        startAttempt(language, android.os.Build.VERSION.SDK_INT >= 34, status, result)

    private fun startAttempt(language: String, automatic: Boolean, status: (String) -> Unit, result: (String) -> Unit) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { status("音声認識サービスがありません"); return }
        val token = gate.token()
        val fallbackNotice = if (!automatic && android.os.Build.VERSION.SDK_INT >= 34)
            "優先言語で再開（もう一度話してください）：" else ""
        val service = try { SpeechRecognizer.createSpeechRecognizer(context) }
        catch (_: RuntimeException) { status("音声認識サービスを開けません"); return }
        recognizer = service
        service.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (gate.accepts(token)) status(fallbackNotice + "聞き取り中…") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (gate.accepts(token)) status("認識中…") }
            override fun onError(error: Int) {
                if (!gate.accepts(token)) return
                cancel()
                if (VoiceLanguagePolicy.retryWithoutSwitch(automatic, error)) {
                    status("自動切替を利用できないため優先言語で再開します。もう一度話してください")
                    val retry = gate.token()
                    retryPending = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (gate.accepts(retry)) startAttempt(language, false, status, result)
                    }, 300)
                } else status("音声入力を終了しました（エラー $error）。再度開始できます")
            }
            override fun onResults(results: Bundle?) {
                if (!gate.consume(token)) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                cancel()
                if (!text.isNullOrBlank()) result(text) else status("認識結果がありません")
            }
            override fun onLanguageDetection(results: Bundle) {
                if (!gate.accepts(token) || android.os.Build.VERSION.SDK_INT < 34) return
                val detected = results.getString(SpeechRecognizer.DETECTED_LANGUAGE)
                val label = when (detected?.substringBefore('-')) { "ja" -> "日本語"; "en" -> "英語"; else -> null }
                if (label != null) status("聞き取り中… 検出：$label")
            }
            override fun onPartialResults(partialResults: Bundle?) {} // Never commit interim hypotheses.
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (automatic && android.os.Build.VERSION.SDK_INT >= 34) {
                intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    .putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                    .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, arrayListOf("ja-JP", "en-US"))
                    .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("ja-JP", "en-US"))
            }
            service.startListening(intent)
            status(fallbackNotice + "音声入力を開始しています…")
        } catch (_: RuntimeException) { cancel(); status("音声入力を開始できません。マイク権限と認識サービスを確認してください") }
    }
}

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
    val active get() = recognizer != null
    fun cancel() {
        gate.invalidate()
        val old = recognizer
        recognizer = null
        old?.cancel(); old?.destroy()
    }
    fun start(language: String, status: (String) -> Unit, result: (String) -> Unit) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { status("音声認識サービスがありません"); return }
        val token = gate.token()
        val service = try { SpeechRecognizer.createSpeechRecognizer(context) }
        catch (_: RuntimeException) { status("音声認識サービスを開けません"); return }
        recognizer = service
        service.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (gate.accepts(token)) status("聞き取り中…") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (gate.accepts(token)) status("認識中…") }
            override fun onError(error: Int) {
                if (!gate.accepts(token)) return
                cancel(); status("音声入力を終了しました（エラー $error）。再度開始できます")
            }
            override fun onResults(results: Bundle?) {
                if (!gate.consume(token)) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                cancel()
                if (!text.isNullOrBlank()) result(text) else status("認識結果がありません")
            }
            override fun onPartialResults(partialResults: Bundle?) {} // Never commit interim hypotheses.
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
            status("音声入力を開始しています…")
        } catch (_: RuntimeException) { cancel(); status("音声入力を開始できません。マイク権限と認識サービスを確認してください") }
    }
}

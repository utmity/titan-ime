package dev.utmity.titanime

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*

class KanaCorrectionTest {
    @Test fun compareKanaCorrection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = ImePreferences(context)
        val saved = Triple(preferences.prediction, preferences.correction, preferences.learning)
        val engine = MozcEngine(context)
        val store = UserDictionaryStore(context)
        val token = "TitanKana" + System.currentTimeMillis()
        try {
            preferences.prediction = true; preferences.learning = false; engine.setLearning(false)
            for (enabled in listOf(false, true)) {
                preferences.correction = enabled
                val timings = mutableListOf<Long>()
                for ((roman, expectedReading) in linkedMapOf("katukou" to "かつこう", "gakukou" to "がくこう",
                    "byouin" to "びょういn", "hiyouin" to "ひよういn", "pan" to "ぱn", "han" to "はn", "nihongo" to "にほんご")) {
                    engine.start()
                    var output = Output.getDefaultInstance()
                    roman.forEach {
                        val start = android.os.SystemClock.elapsedRealtimeNanos()
                        output = engine.key(KeyEvent.newBuilder().setKeyCode(it.code).build())
                        timings.add(android.os.SystemClock.elapsedRealtimeNanos() - start)
                    }
                    assertFalse(output.hasResult())
                    val suggestions = output.allCandidateWords.candidatesList.map { it.value }
                    val reading = output.preedit.segmentList.joinToString("") { it.value }
                    assertEquals(expectedReading, reading)
                    if (roman == "katukou") assertEquals(enabled, "学校" in suggestions)
                    if (roman == "hiyouin") assertEquals(enabled, "病院" in suggestions)
                    if (roman == "nihongo") assertEquals("日本語", suggestions.first())
                    if (enabled && roman in listOf("katukou", "hiyouin")) {
                        val word = if (roman == "katukou") "学校" else "病院"
                        val candidate = output.allCandidateWords.candidatesList.first { it.value == word }
                        val result = engine.command(SessionCommand.CommandType.SUBMIT_CANDIDATE, candidate.id)
                        assertEquals(word, result.result.value)
                        assertEquals(0, result.preedit.segmentCount)
                        engine.start()
                        roman.forEach { engine.key(KeyEvent.newBuilder().setKeyCode(it.code).build()) }
                    }
                    val converted = engine.key(KeyEvent.newBuilder().setSpecialKey(KeyEvent.SpecialKey.SPACE).build())
                    val words = converted.allCandidateWords.candidatesList.map { it.value }
                    if (roman == "han") assertEquals(enabled, "パン" in words)
                    android.util.Log.i("TitanKanaTest", "$enabled $roman reading=$reading suggest=$suggestions convert=${converted.allCandidateWords.candidatesList.map { it.value }}")
                }
                val ordered = timings.sorted()
                android.util.Log.i("TitanKanaTest", "native SEND_KEY correction=$enabled count=${ordered.size} p50us=${ordered[ordered.size / 2] / 1000} p95us=${ordered[(ordered.size * 95 / 100).coerceAtMost(ordered.lastIndex)] / 1000} maxus=${ordered.last() / 1000}")
            }
            store.put(UserDictionaryStore.entry("かつこう", token,
                org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.PosType.NOUN))
            engine.reloadDictionary()
            for (enabled in listOf(false, true)) {
                preferences.correction = enabled; engine.start()
                var output = Output.getDefaultInstance()
                "katukou".forEach { output = engine.key(KeyEvent.newBuilder().setKeyCode(it.code).build()) }
                assertEquals("Explicit dictionary reading stays first", token, output.allCandidateWords.candidatesList.first().value)
            }
        } finally {
            store.list().filter { it.entry.value == token }.sortedByDescending { it.index }.forEach(store::delete)
            engine.reloadDictionary()
            preferences.prediction = saved.first; preferences.correction = saved.second; preferences.learning = saved.third
            engine.close(); engine.setLearning(saved.third); engine.configureSuggestions()
            check(preferences.store.edit().putBoolean("prediction", saved.first).putBoolean("correction", saved.second)
                .putBoolean("learning", saved.third).commit())
        }
    }
}

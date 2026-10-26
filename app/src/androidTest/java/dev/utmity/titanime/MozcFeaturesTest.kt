package dev.utmity.titanime

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.PosType

/** Real arm64 JNI/dictionary tests on device; not physical keyboard verification. */
class MozcFeaturesTest {
    @Test fun predictionCorrectionAndDictionary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = ImePreferences(context)
        val oldPrediction = preferences.prediction
        val oldCorrection = preferences.correction
        val oldLearning = preferences.learning
        val store = UserDictionaryStore(context)
        val token = "TitanTest" + System.currentTimeMillis()
        val reading = "てすとじしょ"
        var engine: MozcEngine? = null
        try {
            preferences.prediction = true; preferences.correction = true; preferences.learning = false
            for (value in listOf(token, token + "B")) store.put(UserDictionaryStore.entry(reading, value, PosType.NOUN))
            val mozc = MozcEngine(context); engine = mozc
            fun type(text: String): Output {
                var output = Output.getDefaultInstance()
                text.forEach { output = mozc.key(KeyEvent.newBuilder().setKeyCode(it.code).build()) }
                return output
            }
            fun values(output: Output) = output.allCandidateWords.candidatesList.map { it.value }
            mozc.start()
            val first = type("niho")
            android.util.Log.i("TitanFeaturesTest", "prediction niho=${values(first)}")
            assertTrue("Input-time predictions", values(first).isNotEmpty())
            val next = type("ngo")
            assertEquals("にほんご", next.preedit.segmentList.joinToString("") { it.value })
            assertNotEquals(values(first), values(next))
            val candidate = next.allCandidateWords.candidatesList.first()
            val committed = mozc.command(SessionCommand.CommandType.SUBMIT_CANDIDATE, candidate.id)
            assertEquals(candidate.value, committed.result.value)
            assertEquals(0, committed.preedit.segmentCount)

            mozc.start()
            val typo = type("fuinki")
            val converted = mozc.key(KeyEvent.newBuilder().setSpecialKey(KeyEvent.SpecialKey.SPACE).build())
            android.util.Log.i("TitanFeaturesTest", "fuinki prediction=${values(typo)} conversion=${values(converted)}")
            assertEquals("ふいんき", typo.preedit.segmentList.joinToString("") { it.value })
            assertTrue("Mozc reading correction", "雰囲気" in values(converted))
            val correction = converted.allCandidateWords.candidatesList.first { it.value == "雰囲気" }
            assertEquals("雰囲気", mozc.command(SessionCommand.CommandType.SUBMIT_CANDIDATE, correction.id).result.value)
            for (roman in listOf("shi", "si")) {
                mozc.start(); assertEquals("し", type(roman).preedit.segmentList.joinToString("") { it.value })
            }
            for (roman in listOf("nihngo", "nihonngo", "nihonfo", "sunkai")) {
                mozc.start(); val literal = type(roman)
                val result = mozc.key(KeyEvent.newBuilder().setSpecialKey(KeyEvent.SpecialKey.SPACE).build())
                android.util.Log.i("TitanFeaturesTest", "coverage $roman reading=${literal.preedit.segmentList.joinToString("") { it.value }} prediction=${values(literal)} conversion=${values(result)}")
            }
            preferences.prediction = false; preferences.correction = false
            mozc.start()
            assertEquals(0, type("nihongo").candidateWindow.candidateCount)
            mozc.start(); type("fuinki")
            val noCorrection = mozc.key(KeyEvent.newBuilder().setSpecialKey(KeyEvent.SpecialKey.SPACE).build())
            assertFalse(noCorrection.allCandidateWords.candidatesList.any { it.attributesList.any { attribute -> attribute.name == "SPELLING_CORRECTION" } })

            mozc.reloadDictionary()
            fun dictionaryCandidates(): Output {
                mozc.start(); type("tesutojisyo")
                var result = mozc.key(KeyEvent.newBuilder().setSpecialKey(KeyEvent.SpecialKey.SPACE).build())
                repeat(reading.length) {
                    if (result.preedit.segmentCount > 1) result = mozc.key(KeyEvent.newBuilder()
                        .setSpecialKey(KeyEvent.SpecialKey.RIGHT).addModifierKeys(KeyEvent.ModifierKey.SHIFT).build())
                }
                return result
            }
            val dictionaryOutput = dictionaryCandidates()
            android.util.Log.i("TitanFeaturesTest", "dictionary test entries present=${values(dictionaryOutput).count { it.startsWith(token) }}")
            assertTrue("Dictionary candidates: ${values(dictionaryOutput)}", values(dictionaryOutput).containsAll(listOf(token, token + "B")))
            val original = store.list().first { it.entry.value == token }
            store.put(UserDictionaryStore.entry(reading, token + "Edited", PosType.PROPER_NOUN), original)
            mozc.reloadDictionary()
            val edited = values(dictionaryCandidates())
            assertTrue(token + "Edited" in edited); assertFalse(token in edited)
            store.delete(store.list().first { it.entry.value == token + "B" })
            mozc.reloadDictionary()
            assertFalse(token + "B" in values(dictionaryCandidates()))
            preferences.learning = true; mozc.setLearning(true)
            assertTrue(token + "Edited" in values(dictionaryCandidates()))
            val exported = store.exportText()
            assertEquals(0, store.importText(exported))
            val exportedTest = exported.lineSequence().first { it.contains(token + "Edited") }
            store.delete(store.list().first { it.entry.value == token + "Edited" })
            assertEquals(1, store.importText(exportedTest))
            mozc.reloadDictionary()
            assertTrue(token + "Edited" in values(dictionaryCandidates()))
            for ((key, value) in listOf("", "abc", "か\nな").map { it to "word" } + listOf("かな" to "")) {
                assertThrows(IllegalArgumentException::class.java) { UserDictionaryStore.entry(key, value, PosType.NOUN) }
            }
            assertEquals("かな", UserDictionaryStore.entry("ｶﾅ", "word", PosType.NOUN).key)
            val before = store.list()
            assertThrows(IllegalArgumentException::class.java) { store.importText("かな\tvalid\t名詞\nbad") }
            assertEquals(before, store.list())
        } finally {
            store.list().filter { it.entry.value.startsWith(token) }.sortedWith(compareByDescending<UserDictionaryStore.Item> { it.dictionary }.thenByDescending { it.index }).forEach(store::delete)
            engine?.reloadDictionary(); engine?.close()
            preferences.prediction = oldPrediction; preferences.correction = oldCorrection; preferences.learning = oldLearning
            engine?.setLearning(oldLearning); engine?.configureSuggestions()
            check(preferences.store.edit().putBoolean("prediction", oldPrediction)
                .putBoolean("correction", oldCorrection).putBoolean("learning", oldLearning).commit())
        }
    }
}

package dev.utmity.titanime

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.PosType

/** Run prepare, force-stop the application, then verify in a separate instrumentation process. */
class DictionaryPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun prepare() {
        val metadata = context.getSharedPreferences("dictionary_test", 0)
        check(!metadata.contains("token")) { "Finish the previous persistence test before preparing another." }
        val token = "TitanPersist" + System.currentTimeMillis()
        UserDictionaryStore(context).put(UserDictionaryStore.entry("たいほ", token, PosType.NOUN))
        check(metadata.edit().putString("token", token).commit())
        MozcEngine(context).reloadDictionary()
    }
    @Test fun verify() {
        val metadata = context.getSharedPreferences("dictionary_test", 0)
        val token = checkNotNull(metadata.getString("token", null))
        val store = UserDictionaryStore(context)
        val mozc = MozcEngine(context)
        try {
            assertTrue(store.list().any { it.entry.value == token })
            mozc.start()
            "taiho".forEach { mozc.key(KeyEvent.newBuilder().setKeyCode(it.code).build()) }
            val output = mozc.key(KeyEvent.newBuilder().setSpecialKey(KeyEvent.SpecialKey.SPACE).build())
            assertTrue(output.allCandidateWords.candidatesList.any { it.value == token })
        } finally {
            store.list().filter { it.entry.value == token }.forEach(store::delete)
            mozc.reloadDictionary(); mozc.close()
            metadata.edit().remove("token").commit()
        }
    }
}

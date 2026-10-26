package dev.utmity.titanime

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real activity/dialog actions via Android accessibility, not human touch. */
class DictionaryUiTest {
    @Test fun createSearchEditDelete() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = UserDictionaryStore(context)
        val token = "TitanUi" + System.currentTimeMillis()
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else
            listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        fun click(label: String) {
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                var node = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString() == label }
                while (node != null && !node.isClickable) node = node.parent
                if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { Thread.sleep(250); return }
                Thread.sleep(100)
            }
            fail("Missing UI control: $label")
        }
        fun fill(hint: String, value: String) {
            val node = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.hintText?.toString() == hint }
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }))
        }
        val activity = instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            click("ユーザー辞書"); click("単語を登録")
            fill("単語", token); fill("読み（ひらがな）", "テスト")
            click("保存")
            assertTrue(store.list().any { it.entry.key == "てすと" && it.entry.value == token })
            // Exercise the Android document picker, not just the TSV parser.
            click("エクスポート（UTF-8 TSV）")
            val pickerDeadline = android.os.SystemClock.uptimeMillis() + 5000
            var filename: AccessibilityNodeInfo? = null
            while (filename == null && android.os.SystemClock.uptimeMillis() < pickerDeadline) {
                filename = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                    it.packageName?.toString()?.contains("documentsui") == true && it.className?.toString() == "android.widget.EditText"
                }
                if (filename == null) Thread.sleep(100)
            }
            assertNotNull("System document create UI", filename)
            assertTrue(filename!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "$token.tsv")
            }))
            click("保存")
            Thread.sleep(400)
            fill("単語・読みを検索", token)
            click("$token / てすと / 名詞"); click("削除"); click("削除")
            assertFalse(store.list().any { it.entry.value == token })
            click("インポート（UTF-8 TSV）")
            click("$token.tsv")
            Thread.sleep(500)
            assertTrue("Document picker round trip", store.list().any { it.entry.value == token })
            android.util.Log.i("TitanFeaturesTest", "SAF test file: $token.tsv")
            fill("単語・読みを検索", token)
            click("$token / てすと / 名詞")
            fill("単語", token + "Edited"); click("保存")
            assertFalse(store.list().any { it.entry.value == token })
            assertTrue(store.list().any { it.entry.value == token + "Edited" })
            click("${token}Edited / てすと / 名詞"); click("削除"); click("削除")
            assertFalse(store.list().any { it.entry.value.startsWith(token) })
        } finally {
            store.list().filter { it.entry.value.startsWith(token) }.sortedByDescending { it.index }.forEach(store::delete)
            MozcEngine(context).reloadDictionary()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}

package dev.utmity.titanime

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real activity navigation/recreation; no IME selection or device settings changed. */
class SettingsNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun views(activity: SettingsActivity) = views(activity.window.decorView)
    private fun intent(page: SettingsPage = SettingsPage.HOME) = Intent(context, SettingsActivity::class.java).putExtra("page", page.name)

    @Test fun homeOpensIndependentPagesAndBackKeepsSettings() {
        val before = ImePreferences(context).store.all.toMap()
        ActivityScenario.launch<SettingsActivity>(intent()).use { scenario ->
            scenario.onActivity { activity ->
                val labels = views(activity).filterIsInstance<Button>().map { it.text.toString() }
                assertTrue(labels.contains("QWERTY入力設定"))
                assertTrue(labels.contains("共通設定"))
                assertEquals(true, labels.contains("ガラケー入力（12キー）設定"))
                assertFalse(labels.any { it.startsWith("＋ ") })
                assertTrue(views(activity).none { it is RadioButton })
            }
            for (page in SettingsPage.homePages()) {
                val monitor = instrumentation.addMonitor(SettingsActivity::class.java.name, null, false)
                try {
                    scenario.onActivity { activity ->
                        views(activity).filterIsInstance<Button>().single { it.text.toString() == page.title }.performClick()
                    }
                    val child = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? SettingsActivity
                    assertNotNull("Separate page opened: ${page.title}", child)
                    instrumentation.runOnMainSync {
                        assertEquals(page.title, child!!.title.toString())
                        views(child).filterIsInstance<Button>().single { it.text.toString() == "戻る" }.performClick()
                    }
                    instrumentation.waitForIdleSync()
                } finally { instrumentation.removeMonitor(monitor) }
            }
            scenario.onActivity { assertTrue(views(it).filterIsInstance<Button>().any { b -> b.text.toString() == "共通設定" }) }
        }
        assertEquals("Navigation must not migrate/reset settings", before, ImePreferences(context).store.all)
    }

    @Test fun choicePersistsAcrossNavigationAndActivityRecreation() {
        val preferences = ImePreferences(context)
        val store = preferences.store
        val key = "twelve_key_swap_cursor_axes"
        val original = store.all[key]
        val unrelated = store.getBoolean("prediction", true)
        val page = SettingsPage.TWELVE_KEY
        val choice = "$key:swapped"
        fun assertChoice(activity: SettingsActivity) {
            assertEquals(page.title, activity.title.toString())
            assertTrue(views(activity).filterIsInstance<RadioButton>().single { it.tag == choice }.isChecked)
            assertEquals(unrelated, store.getBoolean("prediction", true))
        }
        try {
            ActivityScenario.launch<SettingsActivity>(intent(page)).use { scenario ->
                scenario.onActivity { activity ->
                    views(activity).filterIsInstance<RadioButton>().single { it.tag == choice }.performClick()
                    assertChoice(activity)
                }
                assertEquals(true, store.all[key])
                scenario.recreate()
                scenario.onActivity { assertChoice(it) }
            }
            ActivityScenario.launch<SettingsActivity>(intent(page)).use { scenario -> scenario.onActivity { assertChoice(it) } }
        } finally {
            instrumentation.runOnMainSync {
                val edit = store.edit()
                when (original) {
                    null -> edit.remove(key)
                    is Boolean -> edit.putBoolean(key, original)
                    is String -> edit.putString(key, original)
                }
                check(edit.commit())
            }
        }
    }

    @Test fun displayedModeTracksSessionTransitionsWhileSettingsIsOpen() {
        val original = InputModeStatus.active
        try {
            ActivityScenario.launch<SettingsActivity>(intent()).use { scenario ->
                scenario.onActivity { activity ->
                    val status = views(activity).filterIsInstance<TextView>().single { it.tag == "mode-status" }
                    InputModeStatus.active = "12キー英字"
                    assertEquals("現在の入力モード：12キー英字", status.text.toString())
                    InputModeStatus.active = "QWERTY・日本語"
                    assertEquals("現在の入力モード：QWERTY・日本語", status.text.toString())
                    InputModeStatus.active = null
                    assertTrue(status.text.toString().startsWith("入力欄未接続"))
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val status = views(activity).filterIsInstance<TextView>().single { it.tag == "mode-status" }
                    assertTrue(status.text.toString().startsWith("入力欄未接続"))
                }
            }
        } finally { instrumentation.runOnMainSync { InputModeStatus.active = original } }
    }

    @Test fun pagesOwnSettingsOnceAndGuidesMatchAvailableInputMethods() {
        val ownership = mutableMapOf<String, SettingsPage>()
        for (page in SettingsPage.homePages()) {
            ActivityScenario.launch<SettingsActivity>(intent(page)).use { scenario ->
                scenario.onActivity { activity ->
                    views(activity).filter { it is android.widget.RadioGroup }.forEach {
                        val key = it.tag as String
                        assertNull("Setting duplicated: $key", ownership.put(key, page))
                    }
                    if (page == SettingsPage.GUIDE) {
                        val labels = views(activity).filterIsInstance<Button>().map { it.text.toString() }
                        assertTrue(labels.contains("QWERTYの操作ガイド"))
                        assertEquals(true, labels.contains("12キーの操作ガイド"))
                    }
                }
            }
        }
        assertEquals(SettingsPage.QWERTY, ownership["editing_layout"])
        assertEquals(SettingsPage.COMMON, ownership["prediction"])
        assertEquals(SettingsPage.TWELVE_KEY, ownership["twelve_key_swap_cursor_axes"])
        assertEquals(SettingsPage.COMMON, ownership["input_mode"])
        assertEquals(SettingsPage.QWERTY, ownership["japanese"])
    }
}

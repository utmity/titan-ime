package dev.utmity.titanime

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

/** Setup only, not a physical verification result. Run explicitly before the requested human trial. */
class PhysicalPredictionSetup {
    @Test fun prepare() {
        val preferences = ImePreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        check(preferences.store.edit().putBoolean("prediction", true).putBoolean("correction", true)
            .putBoolean("japanese", false).commit())
    }
}

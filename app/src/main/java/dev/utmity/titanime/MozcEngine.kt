package dev.utmity.titanime

import android.content.Context
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJni
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config
import java.io.File

/** One main-thread session. Mozc owns romaji, preedit, segmentation and candidates. */
class MozcEngine(private val context: Context) {
    private var session = 0L
    private var learningEnabled = ImePreferences(context).learning
    init {
        val data = File(context.filesDir, "mozc-c4b2aef.data")
        if (!data.exists()) {
            val temp = File(context.filesDir, "mozc.data.tmp")
            context.assets.open("mozc.data").use { input -> temp.outputStream().use(input::copyTo) }
            check(temp.renameTo(data)) { "Cannot install Mozc dictionary" }
        }
        val profile = File(context.noBackupFilesDir, "mozc").apply { mkdirs() }
        synchronized(MozcEngine::class.java) {
            check(MozcJni.onPostLoad(profile.path, data.path))
            check(MozcJni.getDataVersion().isNotBlank()) { "Mozc dictionary did not load" }
        }
        // Sensitive fields bypass the engine; ordinary conversion may learn locally.
        val config = evaluate(Input.newBuilder().setType(Input.CommandType.GET_CONFIG)).config.toBuilder()
            .setSessionKeymap(Config.SessionKeymap.MSIME).setIncognitoMode(false)
            .setHistoryLearningLevel(if (learningEnabled) Config.HistoryLearningLevel.DEFAULT_HISTORY else Config.HistoryLearningLevel.NO_HISTORY)
            .setAutoSwitchCompositionMode(false)
        config.customRomanTable = context.assets.open("romanji-hiragana.tsv").use {
            com.google.protobuf.ByteString.readFrom(it)
        }
        val rules = config.characterFormRulesList.filter { it.group != "A" }
        config.clearCharacterFormRules().addAllCharacterFormRules(rules)
            .addCharacterFormRules(Config.CharacterFormRule.newBuilder().setGroup("A")
                .setPreeditCharacterForm(Config.CharacterForm.HALF_WIDTH)
                .setConversionCharacterForm(Config.CharacterForm.HALF_WIDTH))
        evaluate(Input.newBuilder().setType(Input.CommandType.SET_CONFIG).setConfig(config))
    }

    private fun evaluate(input: Input.Builder): Output = synchronized(MozcEngine::class.java) {
        Command.parseFrom(MozcJni.evalCommand(Command.newBuilder().setInput(input).build().toByteArray())).output
    }

    fun setLearning(enabled: Boolean) {
        if (enabled == learningEnabled) return
        val config = evaluate(Input.newBuilder().setType(Input.CommandType.GET_CONFIG)).config.toBuilder()
            .setIncognitoMode(false).setHistoryLearningLevel(if (enabled) Config.HistoryLearningLevel.DEFAULT_HISTORY else Config.HistoryLearningLevel.NO_HISTORY)
        evaluate(Input.newBuilder().setType(Input.CommandType.SET_CONFIG).setConfig(config))
        learningEnabled = enabled
    }

    fun sync() { evaluate(Input.newBuilder().setType(Input.CommandType.SYNC_DATA)) }

    fun reloadDictionary() { evaluate(Input.newBuilder().setType(Input.CommandType.RELOAD_AND_WAIT)) }

    fun configureSuggestions() {
        val preferences = ImePreferences(context)
        val config = evaluate(Input.newBuilder().setType(Input.CommandType.GET_CONFIG)).config.toBuilder()
            .setUseHistorySuggest(preferences.prediction).setUseDictionarySuggest(preferences.prediction)
            .setUseRealtimeConversion(preferences.prediction).setSuggestionsSize(8)
            .setUseSpellingCorrection(preferences.correction).setUseTypingCorrection(preferences.correction)
        evaluate(Input.newBuilder().setType(Input.CommandType.SET_CONFIG).setConfig(config))
    }

    fun start() {
        close()
        configureSuggestions()
        session = evaluate(Input.newBuilder().setType(Input.CommandType.CREATE_SESSION)).id
        check(session != 0L) { "Mozc session creation failed" }
        command(SessionCommand.CommandType.TURN_ON_IME)
    }

    fun close() {
        if (session != 0L) evaluate(Input.newBuilder().setType(Input.CommandType.DELETE_SESSION).setId(session))
        session = 0L
    }

    fun key(key: KeyEvent): Output = evaluate(Input.newBuilder().setId(session)
        .setType(Input.CommandType.SEND_KEY).setKey(key))

    fun moveCursor(position: Int): Output = evaluate(Input.newBuilder().setId(session)
        .setType(Input.CommandType.SEND_COMMAND).setCommand(SessionCommand.newBuilder()
            .setType(SessionCommand.CommandType.MOVE_CURSOR).setCursorPosition(position)))

    fun command(type: SessionCommand.CommandType, candidateId: Int? = null): Output {
        val command = SessionCommand.newBuilder().setType(type).setCompositionMode(CompositionMode.HIRAGANA)
        if (candidateId != null) command.id = candidateId
        return evaluate(Input.newBuilder().setId(session).setType(Input.CommandType.SEND_COMMAND).setCommand(command))
    }
}

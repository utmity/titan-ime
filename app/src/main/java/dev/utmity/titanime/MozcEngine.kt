package dev.utmity.titanime

import android.content.Context
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJni
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config
import java.io.File

/** One main-thread session. Mozc owns romaji, preedit, segmentation and candidates. */
class MozcEngine(context: Context) {
    private var session = 0L
    init {
        val data = File(context.filesDir, "mozc-c4b2aef.data")
        if (!data.exists()) {
            val temp = File(context.filesDir, "mozc.data.tmp")
            context.assets.open("mozc.data").use { input -> temp.outputStream().use(input::copyTo) }
            check(temp.renameTo(data)) { "Cannot install Mozc dictionary" }
        }
        val profile = File(context.noBackupFilesDir, "mozc").apply { mkdirs() }
        check(MozcJni.onPostLoad(profile.path, data.path))
        check(MozcJni.getDataVersion().isNotBlank()) { "Mozc dictionary did not load" }
        // Keep learning disabled for the initial integration, including sensitive fields.
        val config = evaluate(Input.newBuilder().setType(Input.CommandType.GET_CONFIG)).config.toBuilder()
            .setSessionKeymap(Config.SessionKeymap.MSIME).setIncognitoMode(true)
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

    private fun evaluate(input: Input.Builder): Output = Command.parseFrom(
        MozcJni.evalCommand(Command.newBuilder().setInput(input).build().toByteArray())).output

    fun start() {
        close()
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

    fun command(type: SessionCommand.CommandType, candidateId: Int? = null): Output {
        val command = SessionCommand.newBuilder().setType(type).setCompositionMode(CompositionMode.HIRAGANA)
        if (candidateId != null) command.id = candidateId
        return evaluate(Input.newBuilder().setId(session).setType(Input.CommandType.SEND_COMMAND).setCommand(command))
    }
}

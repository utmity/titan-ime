package dev.utmity.titanime

import android.content.Context
import android.util.AtomicFile
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryStorage
import java.io.File
import java.text.Normalizer

/** Mozc's public storage protocol; native RELOAD_AND_WAIT loads this exact file. */
internal class UserDictionaryStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "mozc/user_dictionary.db")
    private val atomic = AtomicFile(file)
    data class Item(val dictionary: Int, val index: Int, val entry: UserDictionary.Entry)
    companion object {
        val positions = linkedMapOf(
            UserDictionary.PosType.NOUN to "名詞", UserDictionary.PosType.PROPER_NOUN to "固有名詞",
            UserDictionary.PosType.PERSONAL_NAME to "人名", UserDictionary.PosType.ORGANIZATION_NAME to "組織",
            UserDictionary.PosType.PLACE_NAME to "地名", UserDictionary.PosType.SYMBOL to "記号",
            UserDictionary.PosType.EMOTICON to "顔文字", UserDictionary.PosType.ADVERB to "副詞")
        fun entry(reading: String, word: String, pos: UserDictionary.PosType): UserDictionary.Entry {
            val key = Normalizer.normalize(reading.trim(), Normalizer.Form.NFKC).map {
                if (it in 'ァ'..'ヶ') (it.code - 0x60).toChar() else it
            }.joinToString("")
            require(key.isNotEmpty() && key.length <= 100 && key.all { it in 'ぁ'..'ゖ' || it == 'ー' }) {
                "読みは100文字以内のひらがなで入力してください（カタカナは自動変換）。"
            }
            val value = word.trim()
            require(value.isNotEmpty() && value.length <= 100 && value.none { it.isISOControl() }) {
                "単語は空欄・改行を含まない100文字以内で入力してください。"
            }
            require(value.toByteArray(Charsets.UTF_8).size <= 300) { "単語はUTF-8で300バイト以内にしてください。" }
            require(pos in positions) { "対応する品詞を選んでください。" }
            return UserDictionary.Entry.newBuilder().setKey(key).setValue(value).setPos(pos).build()
        }
    }
    private fun load(): UserDictionaryStorage = if (!file.exists() && !File(file.path + ".bak").exists())
        UserDictionaryStorage.getDefaultInstance() else atomic.openRead().use { UserDictionaryStorage.parseFrom(it) }
    fun list(): List<Item> = load().dictionariesList.flatMapIndexed { d, dictionary ->
        dictionary.entriesList.mapIndexed { i, entry -> Item(d, i, entry) }
    }
    private fun save(storage: UserDictionaryStorage) {
        file.parentFile!!.mkdirs()
        val previous = file.lastModified()
        val stream = atomic.startWrite()
        try { storage.toBuilder().setVersion(1).build().writeTo(stream); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
        // Mozc skips reload when modification timestamps compare equal.
        check(file.setLastModified(maxOf(System.currentTimeMillis(), previous + 1000)))
    }
    fun put(value: UserDictionary.Entry, original: Item? = null) {
        val storage = load().toBuilder()
        if (original != null) check(storage.getDictionaries(original.dictionary).getEntries(original.index) == original.entry) {
            "辞書が更新されました。画面を開き直してください。"
        }
        require(list().none { it != original && it.entry.key == value.key && it.entry.value == value.value && it.entry.pos == value.pos }) {
            "同じ読み・単語・品詞が登録済みです。"
        }
        require(original != null || list().size < 10000) { "登録上限は10000件です。" }
        val d = original?.dictionary ?: storage.dictionariesList.indexOfFirst { it.name == "Titan IME" }.let {
            if (it >= 0) it else { storage.addDictionaries(UserDictionary.newBuilder().setId(1).setName("Titan IME")); storage.dictionariesCount - 1 }
        }
        val dictionary = storage.getDictionaries(d).toBuilder()
        if (original == null) dictionary.addEntries(value) else dictionary.setEntries(original.index,
            original.entry.toBuilder().setKey(value.key).setValue(value.value).setPos(value.pos))
        storage.setDictionaries(d, dictionary)
        save(storage.build())
    }
    fun delete(item: Item) {
        val storage = load().toBuilder()
        val dictionary = storage.getDictionaries(item.dictionary).toBuilder()
        check(dictionary.getEntries(item.index) == item.entry) { "辞書が更新されました。画面を開き直してください。" }
        dictionary.removeEntries(item.index)
        storage.setDictionaries(item.dictionary, dictionary)
        save(storage.build())
    }
    fun exportText(): String = list().joinToString("\n", postfix = "\n") {
        "${it.entry.key}\t${it.entry.value}\t${positions[it.entry.pos] ?: it.entry.pos.name}\t${it.entry.comment}"
    }
    fun importText(text: String): Int {
        require(text.length <= 2_000_000) { "ファイルは2MB以内にしてください。" }
        val entries = text.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.mapIndexed { i, line ->
            val fields = line.split('\t')
            require(fields.size in 3..4) { "${i + 1}行目：読み・単語・品詞をタブで区切ってください。" }
            val pos = positions.entries.firstOrNull { it.value == fields[2] }?.key
                ?: throw IllegalArgumentException("${i + 1}行目：未対応の品詞です。")
            val comment = fields.getOrElse(3) { "" }
            require(comment.toByteArray(Charsets.UTF_8).size <= 300 && comment.none { it.isISOControl() }) {
                "${i + 1}行目：コメントは改行を含まないUTF-8で300バイト以内にしてください。"
            }
            entry(fields[0], fields[1], pos).toBuilder().setComment(comment).build()
        }.toList().distinctBy { Triple(it.key, it.value, it.pos) }
        val storage = load().toBuilder()
        val existing = list().map { Triple(it.entry.key, it.entry.value, it.entry.pos) }.toSet()
        val additions = entries.filter { Triple(it.key, it.value, it.pos) !in existing }
        require(existing.size + additions.size <= 10000) { "登録上限は10000件です。" }
        if (additions.isEmpty()) return 0
        val d = storage.dictionariesList.indexOfFirst { it.name == "Titan IME" }
        if (d < 0) storage.addDictionaries(UserDictionary.newBuilder().setId(1).setName("Titan IME").addAllEntries(additions))
        else storage.setDictionaries(d, storage.getDictionaries(d).toBuilder().addAllEntries(additions))
        save(storage.build())
        return additions.size
    }
}

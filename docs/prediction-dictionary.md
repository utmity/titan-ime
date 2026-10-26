# Prediction, corrections and user dictionary (2026-10-26)

## Pinned upstream APIs

Mozc remains at `c4b2aef7c65e61be394bacadaec200eb5b97f91c`; the native engine and OSS dictionary were not replaced. The existing BSD license and packaged notices still apply. Source references below are for that exact revision.

- [commands.proto](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/protocol/commands.proto): SEND_KEY already requests suggestions by default. SUBMIT_CANDIDATE commits an input-time suggestion using its native ID; HIGHLIGHT_CANDIDATE remains for ordinary segmented conversion. No Android-side text substitution is used for predictions.
- [candidate_window.proto](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/protocol/candidate_window.proto): category distinguishes SUGGESTION/PREDICTION/CONVERSION; all_candidate_words supplies USER_DICTIONARY, USER_HISTORY, SPELLING_CORRECTION and TYPING_CORRECTION attributes. Titan uses native IDs and correction attributes and retains Mozc's ordering. Annotation descriptions are exposed to accessibility.
- [config.proto](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/protocol/config.proto): dictionary/history suggestions, realtime conversion, suggestions_size=8, spelling/typing correction flags. Prediction off disables the three suggestion sources. Correction off disables spelling/typing correction. No automatic replacement is added.
- [engine/modules.cc](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/engine/modules.cc): the OSS supplemental typing model is a stub. A true use_typing_correction flag alone does not implement general QWERTY typo repair. No proprietary model is included.
- [correction_rewriter.cc](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/rewriter/correction_rewriter.cc): standard reading corrections use the bundled OSS reading-correction data. Verified example is ふいんき → 雰囲気. Romaji alternatives such as shi/si are composer behavior, not evidence for arbitrary missing/extra-key correction.
- Context and learned ranking remain within the same Mozc session and native history predictor. Titan does not independently rerank user dictionary/history candidates. Existing learning persistence tests remain separate from prediction tests.

## User dictionary integration

The former SEND_USER_DICTIONARY_COMMAND and IMPORT_USER_DICTIONARY commands are reserved/deprecated in this revision. Adding Java calls to them would not work. The supported storage is [user_dictionary_storage.proto](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/protocol/user_dictionary_storage.proto). Titan copies that exact protocol into its Java-lite generation and writes `noBackupFilesDir/mozc/user_dictionary.db` with Android AtomicFile. The official iOS bridge similarly serializes this storage and reloads the engine; see [ios_engine.cc](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/ios/ios_engine.cc).

Registration/update/deletion change the protobuf storage, then the existing JNI evalCommand bridge sends RELOAD_AND_WAIT. Native UserDictionary reads that file and the converter uses its tokens. No separate Android-only candidate overlay, new JNI binary, process restart or history deletion is required. Changes affect subsequent input. Dictionary IDs, other dictionaries and unrelated entries are preserved. Empty dictionary files are retained when the last entry is deleted, since upstream treats a missing file as “keep current tokens.” Modification times are advanced to prevent upstream's unchanged-file optimization from missing rapid edits.

An integration test initially failed because Titan's old learning-off mode set incognito_mode. Upstream DictionaryImpl deliberately removes the user dictionary in incognito mode. Learning off now uses NO_HISTORY with incognito=false: history lookup/writes are disabled while explicit dictionary entries remain available. Password and non-text fields still bypass the engine entirely.

The management activity is internal (not exported) and is opened from IME settings. It validates and normalizes readings, prevents duplicates, supports eight named POS types, preserves comments during edits, and uses the Storage Access Framework for UTF-8 TSV input/output. Invalid imports are parsed completely before any write. Atomic persistence protects the previous file on write failure. Existing unknown POS entries are retained and are not silently changed to nouns. See README for limits and interchange format.

## Editing and UI

Prediction taps use SUBMIT_CANDIDATE and apply its native result/preedit together in the existing Android batch edit. The reading “そのまま” control preserves access to the original input. Space continues to convert and cycle; candidate taps in conversion still highlight rather than commit. No prediction callback rewrites composition asynchronously.

Pending range-editing work is included: an Android selection wholly inside a reading preedit is tracked as UTF-16 bounds, then converted to Mozc code points. MOVE_CURSOR plus native BACKSPACE deletes the selected reading before insertion. Selection outside the composing range or during conversion still finalizes visible text. Long-press selection, reverse selection, emoji offsets, small kana and punctuation replacement are covered in the harness.

## Verification types

- `MozcFeaturesTest`: real arm64 JNI and OSS data on the T2E; prediction update/commit, standard correction and correction-off, romaji alternatives, diagnostic unsupported typo cases, dictionary add/edit/delete, multiple same-reading entries, learning-on/off coexistence, UTF-8 TSV round trip, duplicate handling and invalid-input rejection. It removes only uniquely named test entries and restores its preference baseline synchronously.
- `DictionaryUiTest`: Android accessibility operations on the actual settings/dictionary activities; registration, search, editing and confirmed deletion. This is automation, not human touch.
- `DictionaryPersistenceTest#prepare` followed by a separate process and `#verify`: registered storage and native conversion after restart; only its uniquely named entry is removed afterward.
- `InputDeviceTest`: Android key/touch injection through the active IME, including prediction/correction taps and all earlier conversion, punctuation, segment and cursor/range regressions. It is not proof of physical key delivery.
- `LearningPersistenceTest`: native learned ranking after restarting the IME.
- `PhysicalPredictionSetup`: optional setup only, **not a test success**. Enables prediction/correction and leaves English mode before a requested human trial.

Run the named instrumentation classes explicitly. App tests use `dev.utmity.titanime.test/androidx.test.runner.AndroidJUnitRunner`; editor tests use `dev.utmity.titanime.harness.test/androidx.test.runner.AndroidJUnitRunner`. CI builds both test APKs, but has no T2E and cannot claim device execution. Do not run native app instrumentation while the user is entering text; it owns the same application process.

Limitations: no general adjacent-key/deletion/insertion correction, no automatic replacement, no custom T2E keyboard error model, only eight dictionary POS choices, no cloud/network, other editors and paste during range selection unverified. File-format round trips do not alone verify the system file-picker UI. Physical verification is recorded separately in device-validation.md.

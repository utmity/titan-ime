# Preview: physical 12-key mode switching (draft)

This branch builds **on top of Draft PR #8**; it does not modify the main branch.
The production Titan IME retains its existing shortcuts, Romaji/Mozc table and normal input behavior.

## Controls (Preview app only)

* Tap the left input bar's mode button (label Q / あ / abc / 123 / １２３)
  **or press the existing configured language toggle (default Fn+Space)**.
* Modes cycle: **QWERTY → 12-key kana → 12-key English → 12-key half-width digits → 12-key full-width digits+symbols → QWERTY**.
* QWERTY uses the usual Titan IME physical routing and previously selected Japanese/English language.
* Kana uses the already-tested stock Mozc 12-key table, without changing its cycles.
* English uses Mozc's stock `TWELVE_KEYS_TO_HALFWIDTHASCII` table; the key
  mapping accepts both W/E/R,S/D/F,Z/X/C and Y/U/I,H/J/K,B/N/M
  at all times; T/G/V remain */0/#. The stock table
  cycles lower/uppercase letters and numbers, like a mobile phone.
* Half-width numbers emit 1–9 on their row keys, 0 on G; repeated taps
  append multiple digits instead of cycling. T inserts *, V inserts #.
* Full-width numbers use the same row key mapping. The digit is the first
  choice on each key, with a Gboard-style symbol sequence on repeated taps;
  The four cursor keys move the Android editor caret after finishing the
  active numeric composing span. T cycles brackets, V punctuation.
  This mode has no time-based timeout; to enter two consecutive identical
  fullwidth digits, use the configured Right key to separate the presses.

## Both halves and cursor settings

In **all four twelve-key modes**, both halves are always active, with no hand
selection: W/Y→1, E/U→2, R/I→3, S/H→4, D/J→5, F/K→6, Z/B→7,
X/N→8, C/M→9. T/G/V remain */0/#. Alternating equivalent left/right
keys counts as another tap of the same Mozc token (or fullwidth numeric key).
Halfwidth digits still append on every deliberate tap.

Preview Settings → **物理キー** → **12キーのカーソル配置**:

| Setting | Q | A | O | L |
| --- | --- | --- | --- | --- |
| Default | Left | Right | Up | Down |
| Swapped | Up | Down | Left | Right |

The setting persists and updates the running service through its cached
preferences. No mode switch or restart is required. Neither the cursor setting
nor the dual twelve-key row mapping applies in **QWERTY mode** or the production
application. Existing modifier/direct-field bypasses are retained.

Kana/English navigation uses the existing DPAD handling: Left/Right move the
reading cursor and separate same-row taps, collapse reading selections to the
appropriate edge, and operate conversion segments. Up/Down use Mozc's normal
candidate operations while composing/converting. With committed text, all four
are ordinary editor arrows. Numeric navigation first finishes its composing
span and then sends an editor arrow. Stock Mozc kana cycles are unchanged.

## Verification of the revised mapping

- JVM: `TwelveKeyKeymapTest` checks both halves, common keys, all four default
  and swapped arrows, and excludes DPAD itself (preventing recursive remapping).
- Instrumentation: `ImePreferencesCacheTest` checks default/persistence and
  cross-instance cache refresh for the cursor setting.
- Harness: `TwelveKeyPreviewTest` now detects and restores the Preview mode,
  and reads the current cursor setting. It checks mixed-hand kana/English
  toggling and both numeric modes; four-way committed-text navigation in all
  four modes; selected reading editing; conversion navigation against actual
  DPAD input; QWERTY exclusion; and existing editing cases.

Build/lint/JVM checks use the existing Verify workflow. Instrumentation suites
are **compiled, not executed** by that workflow. No device is attached here.
Run the harness suite on T2E once with each cursor setting, using the default
Fn+Space mode shortcut:

```bash
adb shell am instrument -w -e class dev.utmity.titanime.TwelveKeyPreviewTest dev.utmity.titanime.harness.test/androidx.test.runner.AndroidJUnitRunner
```

Manually verify settings changes without restarting the IME, long paused taps,
long-press repeats, selection replacement, middle insertion/Backspace,
Space→candidate Up/Down→segment Left/Right→Enter, same-row separation, multiline
committed text, and QWERTY letter input with both cursor settings. Actual T2E
key metadata, app-specific editor behavior and candidate display remain
unverified until this is run on the device.

## Symbol candidates

These are an **initial approximation of the symbols printed on Gboard's Japanese
12-key numeric layout** (different app versions/settings may vary). They are
not validated as a perfect transcription of every Gboard flick direction:

| Physical left-hand key | Numeric token | Fullwidth toggle sequence |
| --- | --- | --- |
| W | 1 | １ ☆ ♪ → |
| E | 2 | ２ ￥ ＄ € |
| R | 3 | ３ ％ ＃ |
| S | 4 | ４ ○ ＊ ・ |
| D | 5 | ５ ＋ × ÷ |
| F | 6 | ６ ＜ ＝ ＞ |
| Z | 7 | ７ 「 」 ： |
| X | 8 | ８ 〒 々 〆 |
| C | 9 | ９ ＾ ｜ ＼ |
| G | 0 | ０ ～ … ＠ |
| T | * | （ ） ［ ］ |
| V | # | 、 。 ？ ！ … |

Source/reference: Android Gboard Japanese 12-key numeric keyboards and three-state layout;
https://www.android.com/intl/ja_jp/articles/466/

**Note**: The symbols on T/V are a pragmatic grouping for the physical
keyboard, not an assertion of exact stock Gboard layout. Adjust after
comparing against the user's actual Android keyboard.

## Known risks / to validate on T2E

1. Fn+Space on Preview cycles five modes; on production it still toggles Japanese/English.
   If the user's configured shortcut differs, the configured shortcut cycles Preview modes.
2. Stock Mozc English 12-key toggle table is selected and entered in HALF_ASCII
   composition mode, but still needs on-device JNI input tests.
3. The fullwidth digit+symbol cycling uses `InputConnection.setComposingText`
   for the current character, not the Mozc session; verify Android cursor and
   candidate strip synchronization on real editors.
4. Preview numeric mode needs verification for quick repeated taps, A/Q caret movement,
   Backspace, selection replacement, space/enter, and field changes.
5. The ordinary application must not change behavior or latency.
6. This branch should remain a **Draft** until CI and T2E tests pass.

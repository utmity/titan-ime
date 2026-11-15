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
  mapping remains W/E/R,S/D/F,Z/X/C; T/G/V remain */0/#. The stock table
  cycles lower/uppercase letters and numbers, like a mobile phone.
* Half-width numbers emit 1–9 on their row keys, 0 on G; repeated taps
  append multiple digits instead of cycling. T inserts *, V inserts #.
* Full-width numbers use the same row key mapping. The digit is the first
  choice on each key, with a Gboard-style symbol sequence on repeated taps;
  Q/A move the Android editor caret. T cycles brackets, V punctuation.
  This mode has no time-based timeout; to enter two consecutive identical
  fullwidth digits, use A to separate the presses.

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

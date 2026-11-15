# Physical twelve-key input: pinned Mozc probe (2026-11-15)

Status: **experimental Preview IME input + device-only native probe**.
The separate `dev.utmity.titanime.preview` application now uses the stock Mozc
12-key table when its Japanese mode is active. Production/debug `dev.utmity.titanime`
still uses its original QWERTY path. The user-facing mode-switch UI is deferred.
Only LEFT hand is currently enabled in Preview; RIGHT hand translation is implemented
and unit-tested, but not selectable in the UI yet.

## Why verify Mozc first?

Titan IME already uses Mozc's native JNI conversion sessions. The pinned revision
`c4b2aef7c65e61be394bacadaec200eb5b97f91c` contains
`Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HIRAGANA`, a stock 12-key input table,
and `SessionCommand.STOP_KEY_TOGGLING`. The table is selected by `SET_REQUEST`
and overrides `Config.customRomanTable` while active (see upstream
`src/composer/table.cc`). `Config.composing_timeout_threshold_msec` defaults
to 0, documented by Mozc as **not** generating timeout-triggered
`STOP_KEY_TOGGLING`.

`TwelveKeyKeymap.translate` translates physical Android `KEYCODE` values
into stock table tokens, and Preview sends them into the ordinary Mozc session.
Only Mozc's returned preedit/result text is written to the Android editor.

| Function | Left hand | Right hand | Mozc token |
| --- | --- | --- | --- |
| あ / か / さ | W / E / R | Y / U / I | 1 / 2 / 3 |
| た / な / は | S / D / F | H / J / K | 4 / 5 / 6 |
| ま / や / ら | Z / X / C | B / N / M | 7 / 8 / 9 |
| わ row | G | G | 0 |
| Modifiers (voicing/small) | T | T | * |
| Symbols | V | V | # |
| Cursor left | Q | O | Mozc LEFT (while composing) |
| Cursor right / same-row separator | A | L | Mozc RIGHT (while composing) |

Outside a reading composition, Q/A map to Android `InputConnection` arrow
navigation. When Mozc owns the preedit, Q/A send Mozc LEFT/RIGHT (which also
breaks the same-row toggle). Backspace and Space continue through the existing
Mozc service path. The right-hand keys O/L are available in the mapping but
are not selected by Preview yet. Ctrl/Alt/Shift/SYM/Fn combinations bypass this
experimental mapping; physical long-press repeat events are ignored.

## Differences between built-in Mozc and requested behavior

These are grounded in the **exact pinned source table** at
`src/data/preedit/12keys-hiragana.tsv`, not guesses about a generic IME:

* Stock `1` cycles あいうえお **ぁぃぅぇぉ**. Desired: あいうえお only;
  small characters are requested via T.
* Stock `8` cycles やゆよ **ゃゅょ**, and `4` cycles たちつてと **っ**.
  These likewise need review to keep T as the modifier key.
* Stock `0` cycles わ→を→ん→**ゎ**→ー. Desired: わ→を→ん→ー.
* Stock `#` cycles 、→。→？→！→・→全角空白.
  Desired minimum: 、→。→？→！→… (ellipsis is missing).
* Stock `*` already cycles は→ば→ぱ→は and toggles kana size,
  a good match for T.
* The upstream scenario tests explicitly demonstrate that Mozc RIGHT
  separates a repeated 12-key tap (e.g. `1, RIGHT, 1` → ああ).

For this first testable Preview, **use the Mozc built-in cycles unchanged**.
Do not customize the table, punctuation sequence, or small-kana order yet.
After the real device verifies basic editing, we can choose whether any stock
cycles are worth changing. This follows the user's decision to customize only
the physical key handling for now.

## Run the probes

* Host/JVM test: `./gradlew :app:testDebugUnitTest --tests '*TwelveKeyKeymapTest'`
* Device native probe: install the **matching debug application and androidTest
  APKs** on an arm64 Android device, then run:

```bash
adb shell am instrument -w -e class dev.utmity.titanime.TwelveKeyMozcProbeTest dev.utmity.titanime.test/androidx.test.runner.AndroidJUnitRunner
```

The normal verification GitHub workflow assembles instrumentation tests but
does **not** execute them (no attached device). The native behavior is therefore
**unverified on Titan 2 Elite until the instrumented probe runs**.
Do not treat an APK compile or upstream source test as an actual device test.

## Preview test workflow

Build with the repository Verify workflow, download its `titan-ime-preview-safe-install`
artifact and install the separately named Preview APK. The preview IME must be
manually selected in Android's input method picker, with Japanese input enabled.
It initially uses the left-hand layout (W/E/R, S/D/F, Z/X/C; Q/A arrows; T/G/V
special keys). All kana and punctuation follow **Mozc defaults**, including
the extra small characters and the different stock symbol cycle.

Only after native verification: test cursor insertion within composing text,
committed-text navigation, Backspace, Space/candidates, modifier ordering, and
absence of regressions in the existing QWERTY build. Add hand/mode switching UI
later. This is a preliminary preview and has **not been tested on T2E hardware**.

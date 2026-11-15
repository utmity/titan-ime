# Physical twelve-key input: pinned Mozc probe (2026-11-15)

Status: **dormant mapping + device-only probe**, not a selectable IME input mode yet.
This branch does **not** wire the mapping into `TitanImeService`, change language toggles,
rewrite candidate logic, or change the existing QWERTY path.

## Why verify Mozc first?

Titan IME already uses Mozc's native JNI conversion sessions. The pinned revision
`c4b2aef7c65e61be394bacadaec200eb5b97f91c` contains
`Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HIRAGANA`, a stock 12-key input table,
and `SessionCommand.STOP_KEY_TOGGLING`. The table is selected by `SET_REQUEST`
and overrides `Config.customRomanTable` while active (see upstream
`src/composer/table.cc`). `Config.composing_timeout_threshold_msec` defaults
to 0, documented by Mozc as **not** generating timeout-triggered
`STOP_KEY_TOGGLING`.

The new `TwelveKeyKeymap.translate` only translates physical Android
`KEYCODE` values into stock table tokens. It does not commit those tokens to
the Android editor.

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

Outside the reading-composition state, left/right must instead use Android
`InputConnection` navigation. Backspace should remove the preceding character;
Space should use Mozc conversion. Those parts are **not wired yet**.

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

We should test the native table before choosing between (a) a customized
12-key TSV supplied through Mozc's `customRomanTable` with the request's
`specialRomanjiTable` reset to DEFAULT_TABLE, and (b) small local
preprocessing. A custom table is possible in principle, but must be validated
through the pinned JNI and packaged dictionary.

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

## Next step after native verification

If JNI table selection works, add a custom 12-key table matching the exact
cycles and a separately enabled experimental frontend. Test cursor insertion
within composing text, committed-text navigation, Backspace, Space/candidates,
modifier ordering, and absence of regressions in existing QWERTY. Add mode
switching / UI later.

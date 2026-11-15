# Physical twelve-key input: pinned Mozc probe (2026-11-15)

Status: **experimental Preview IME input + device-only native probe**.
The separate `dev.utmity.titanime.preview` application now uses the stock Mozc
12-key table when its Japanese mode is active. Production/debug `dev.utmity.titanime`
still uses its original QWERTY path. The user-facing mode-switch UI is deferred.
PR #9 enables both physical halves at once in all twelve-key modes.
See `twelve-key-preview-modes.md` for the current four-way cursor settings.

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
| Cursor left / right | Q / A by default | O / L when axes swapped | Mozc LEFT / RIGHT while composing |
| Cursor up / down | O / L by default | Q / A when axes swapped | Mozc UP / DOWN while composing |

Outside a reading composition, Q/A map to Android `InputConnection` arrow
navigation. When Mozc owns the preedit, Q/A send Mozc LEFT/RIGHT (which also
breaks the same-row toggle). A selected reading collapses to its left/right edge;
a mapped character replaces that selection through Mozc before updating the editor.
During conversion Q/A use the existing segment-navigation path. Backspace and Space continue through the existing
Mozc service path. Both character halves are active. O/L now provide Up/Down by default;
the Preview setting can swap their axes with Q/A. Ctrl/Alt/Shift/SYM/Fn combinations bypass this
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

## Review and verification update

The service configures the stock table before creating each native session,
including after editor resets. Preview explicitly sets the composing timeout to
zero. The production request and config are preserved, covered by
`MozcInputProfileTest`. No custom kana tables, mode UI or main merge are added.

Review found the early Preview routing bypassed `selectedReading` handling:
a mapped character inserted without replacing the editor's selected reading,
and Q/A moved from the native caret rather than collapsing the selection.
Both now use the native cursor/deletion operations and the existing `applyOutput`
batched InputConnection writer. Conversion arrows also reuse `moveSegment`.
Mozc remains the owner of the complete composing text; the editor receives only
returned preedit/result text. Existing synchronization handles editor caret
changes, stale batch snapshots, and externally rewritten text. These source-level
checks are not evidence that every Android editor behaves correctly.

## Run the checks

```bash
bash gradlew :app:testDebugUnitTest :app:testPreviewUnitTest :app:lintDebug :app:lintPreview :harness:lintDebug :app:assembleDebug :app:assembleRelease :app:assemblePreview :app:assembleDebugAndroidTest :harness:assembleDebug :harness:assembleDebugAndroidTest
bash gradlew -PandroidTestBuildType=preview :app:assemblePreviewAndroidTest
```

The Verify workflow runs these checks and publishes both Preview and matching
instrumentation APKs in `debug-and-preview-device-test-apks`. Native tests now
exercise `MozcEngine` itself (table setup, session restarts and zero timeout),
rather than reproducing JNI request setup in the test.

On an arm64 device, install Preview and its matching Preview test APK. Run:

```bash
adb shell am instrument -w -e class dev.utmity.titanime.TwelveKeyMozcProbeTest dev.utmity.titanime.preview.test/androidx.test.runner.AndroidJUnitRunner
```

For actual service/InputConnection integration, also install the harness debug
and harness test APKs, manually select **Titan IME Preview**, and run:

```bash
adb shell am instrument -w -e class dev.utmity.titanime.TwelveKeyPreviewTest dev.utmity.titanime.harness.test/androidx.test.runner.AndroidJUnitRunner
```

The harness suite refuses to run with a different IME selected. It switches to
Japanese with the existing default Fn+Space binding if needed and restores the
initial language afterward. Use default shortcut settings for this suite.
Do not run the old QWERTY harness suites against Preview.

## T2E manual acceptance (still unverified)

Select the separate Preview IME and Japanese mode. Keep the normal IME installed.

| Operation | Expected stock-Mozc behavior |
| --- | --- |
| E, pause at least 1.5 s, E | き, a single composing character; also try a much longer pause |
| Hold E | No extra cycles from repeat events; release and press advances once |
| E, A, E | かか; A separates same-row taps without committing |
| W, E, Q, R | あさか, with caret between さ and か |
| Backspace at that caret | あか, with caret between あ and か |
| Touch a caret inside あか; R | Insert さ at the touched position, no duplicated text |
| Select か in あかさ; F; T | あばさ; both editor and Mozc selection are replaced |
| Select part of reading; Q or A | Collapse to selection start or end |
| W, E, Space, candidate selection, Enter | Conversion candidates, then committed text with no composing span |
| Q/A after Enter | Move the Android editor caret through committed text |
| F,T,T,T; W,T; G repeats; V repeats | Stock voicing, small kana, わ-row and punctuation cycles |
| Switch field, restart input, return to Preview | New composition still uses the stock 12-key table |
| English, password/number fields, Ctrl/Alt/Shift/SYM/Fn | Experimental mapping bypassed; existing field/shortcut policy applies |

Also check long readings and multi-segment conversion with Q/A, external edits,
app switching and the actual target editors. Use the normal debug/production IME
for QWERTY regression verification. Both character halves are active and unit tested; physical operation remains
unverified. The cursor-axis setting is described in the PR #9 mode document.

No Android device or emulator is attached in this task environment. Native and
service integration suites are compiled by CI, **not executed**. Passing host
unit tests/build/lint does not establish T2E hardware or InputConnection results.

# Device validation — 2026-10-23 (JST)

## Environment

Baseline remote/local HEAD: `1da9d397663e947894b305941e95cc72fc810cec`.
Branch: `codex/device-test-harness`.
USB ADB reports `device`; model `Titan 2`, product/device `Titan_2`, Android 16. These values do not independently verify the user's Elite variant designation.
Java 17.0.20.1, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, ADB 37.0.1. SDK 35 was installed using an existing accepted SDK license. SDK XML warning was non-fatal.

## Results and fixes

- Composer instrumentation initially failed: `konnichiha` produced `こんいちは`. Deferred nn resolution fixed this; greeting, doubled consonant, final n and nn/nna/nnka/kanna regressions pass on the device.
- Ctrl+Space changed the default Android IME. Reproduced in an input-only run, excluding reinstall as a necessary cause. The per-key selection guard stopped further input. Replaced internal toggle with Ctrl+Shift+J.
- Input tests now run in separate package `dev.utmity.titanime.harness`, avoiding instrumentation of the selected IME's package. Tests wait for focused editor and the Debug IME onStartInput notification, explicitly request IME input and allow connection delivery to settle. Soft IME visibility alone was not a valid readiness condition on this hardware-keyboard device.
- A final standalone harness run passed English `a`, deletion, EN→JA, pending `k`, deleting the last composing character, `ka`→`か` with composing span, Enter→`か\n` with no composing span, pending `n`, JA→EN committing `ん`, and English `b`. IME diagnostic logs confirmed key delivery and both mode changes.
- Backspace now replaces composing text with empty text before finishing composition. The last-character deletion assertion passed through the actual InputConnection.

These are injected Android key events on the physical device, not physical keyboard presses. No claim is made about arbitrary editors, passwords, cursor movement, Japanese conversion candidates, or all romaji spellings.

## IME settings

Initial default: `com.iqqijni.bbkeyboard/.keyboard_service.view.HDKeyboardService`.
PhysiBoard dev3, this service and FluxKeyboard were enabled initially. Titan IME was registered by APK installation, then enabled/selected manually by the user.
The user subsequently authorized automated default-IME changes. `-SelectTitanIme` now explicitly selects the already-enabled Titan IME and restores the selection recorded at run start in a finally block. No enable/disable/uninstall commands or USB-debugging setting changes are used. Current test baseline is Titan IME itself.

## Hardware evidence

`getevent -lp` reports `/dev/input/event5` named `TitanKey`; event numbers may change after reboot. Advertised Linux capabilities include letters, BACKSPACE, ENTER, RIGHTSHIFT, LEFTALT, SPACE, F1/F10/F11/F13, arrows, vendor codes `00fb`/`00fd`. These are capabilities, not observed mappings for labeled physical keys.

Injected events observed by the IME: A=29, J=38, K=39, N=42, B=30, DEL=67, ENTER=66; Ctrl+Shift meta=4097. ScanCode=0/device=-1 identifies these as synthetic, not hardware evidence.

The initial observation window contained no presses; the labeled physical capture below subsequently completed the requested mapping. The probe logs key metadata, not editor contents; metadata can still reveal keys, so use only test input. IME-side probe logging is restricted to debuggable builds and the harness editor package. The harness activity probe is confined to that app.

## Reproduce

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\scripts\test-device.ps1 -Serial <serial> -InputSequence -SelectTitanIme -FallbackIme <enabled-other-ime-id>
# Existing APKs only:
.\scripts\test-device.ps1 -Serial <serial> -InputSequence -SelectTitanIme -FallbackIme <enabled-other-ime-id> -SkipInstall
```

Start in English mode with screen unlocked. A successful sequence returns to English; a failed sequence may leave internal mode in Japanese. The run saves build output, JVM composer test build output/input results, key capabilities and filtered logs in ignored `test-results/`. Raw serials and other-app logs are not committed.

Open the physical-key test editor with:
`adb shell am start -n dev.utmity.titanime.harness/dev.utmity.titanime.InputTestActivity`.
Correlate labeled presses with `adb shell getevent -lt` and `TitanKeyProbe`/`TitanImeProbe` logs. System-consumed keys may not reach the activity. A previous unrelated WebView crash in logcat was not attributed to Titan IME.

## Final pipeline result

PASS: the final runner built the APKs, passed the JVM composer regressions, temporarily selected the already-enabled original keyboard service, installed the IME/harness/harness-test APKs, selected Titan, received its input-start notification, and passed `OK (1 test)` for the full input sequence. It restored the run-start selection (Titan) without an enabled/default mismatch warning.

Composer tests were initially executed on the device to reproduce the bug, then moved to JVM tests so running them no longer instruments/restarts the IME package. Device input tests remain separate and exercise the real service and InputConnection.

On this device, reinstalling while Titan remained selected sometimes left its service disconnected despite the selection setting. An explicit `-FallbackIme` before installation resolved this in the final end-to-end run. The fallback must already be enabled and requires `-SelectTitanIme`; the script never chooses an unknown fallback itself. One stalled Android package-install session was explicitly abandoned and retried successfully; no package or security setting was removed/disabled.

## 2026-10-24 resume

USB device remained connected. Titan was installed/registered but absent from enabled_input_methods, and `ime set` returned `Unknown input method`. After the user's authorization to manage default-IME switching, Titan alone was re-enabled and selected for continued testing. Other enabled IMEs were retained. The runner still does not enable/disable IMEs automatically. Physical TitanKey capture was started for labeled user presses; this is not yet a mapping result.

## Physical key capture completed — 2026-10-24

The user confirmed pressing A, Sym, Fn, Sym+A, Fn+A in that order. Linux events from TitanKey and Android activity/IME logs matched all five groups. Device log timestamps were 05:36:06–05:36:24 JST; Linux monotonic timestamps were 39507.460946–39525.585390. These are physical events, deviceId=0 (not the earlier injected deviceId=-1).

| Physical operation | Linux EV_KEY | Android keyCode / scanCode | Observed metaState |
| --- | --- | --- | --- |
| A | KEY_A | 29 / 30 | 0 on down/up |
| Sym | 0x00fd (253) | 63 (KEYCODE_SYM) / 253 | down=4, up=0 |
| Fn | 0x00fb (251) | 403 / 251 | 0 on down/up |
| Sym held + A | 0x00fd down, KEY_A down/up, 0x00fd up | A=29 / 30 | A down/up=4 |
| Fn held + A | 0x00fb down, KEY_A down/up, 0x00fb up | A=29 / 30 | A down/up=0 |

SDK definitions identify meta=4 as META_SYM_ON; META_FUNCTION_ON is 8 and was not present in the Fn+A observation. The local SDK KeyEvent definitions do not name code 403; retain the numeric value rather than guessing a standard Fn key code.

All down events reached TitanImeService with japanese=false and connected=true, and both down/up events reached the harness activity. This verifies English-mode pass-through for this sequence. It does not establish Japanese-mode Sym/Fn semantics, the produced characters, other Fn chords, long presses or repeat behavior. Future Fn support needs the observed key's own press/release state (or further device mapping evidence), not only META_FUNCTION_ON. No Fn remapping is introduced by this PR.

Capture was stopped after collecting the sequence. Default IME remains Titan; previously enabled PhysiBoard, the original keyboard service and FluxKeyboard remain enabled.

## Japanese modifier routing follow-up — 2026-10-24

A new synthetic regression on the real device reproduced Sym+A being converted to あ in Japanese mode. The expected policy is to pass modified keys through, not feed them into romaji composition. The test uses META_SYM_ON and the previously observed vendor Fn keyCode=403/scanCode=251 down/A/up sequence; it is not a second physical capture.

The IME now bypasses romaji for Sym, standard META_FUNCTION_ON, and a held vendor Fn identified by that exact key/scan pair. Vendor Fn state is tracked per device ID, released on key-up and cleared on input lifecycle changes. No symbol/numeric key mapping is invented; events are forwarded to the editor/system.

Pending composition is committed before modified input. An expanded device test also exposed a cursor/order race when commitText was followed by normal key fallthrough: subsequent text was inserted before the modified letter. Routing that key through the same InputConnection after committing fixed the observed sequence.

Final build/install/select/test/restore pipeline: PASS, `OK (1 test)`, 6.420 seconds. The existing input sequence plus Japanese Sym+A, vendor Fn+A, Fn-release recovery, pending n followed by Sym+A, pending k followed by Fn+A, and standard function-modified A all passed. Composer JVM regressions passed as part of the build. The run restored its initial default, PhysiBoard dev3.

Physical Japanese-mode Sym/Fn presses and other editors remain unverified. The earlier physical capture established the mapping in English mode only. A lost Fn-up without an input lifecycle transition is not separately tested.

## Japanese-mode physical verification — 2026-10-24

PASS on commit a3a9d0e: the user physically pressed Sym+A, released both keys, pressed Fn+A, released both keys, then K, A, Enter. The harness was empty and the IME diagnostic probe confirmed japanese=true before and throughout this sequence.

Linux TitanKey timestamps 40629.849152–40648.563908 and Android logs 05:54:48–05:55:07 JST matched all requested operations. Sym+A reached the editor with keyCode=29, scanCode=30, meta=4; Fn+A reached it with the same key/scan and meta=0, following vendor Fn=403/251. DeviceId=0 identifies physical events. K used scanCode=37; Enter used scanCode=28. Normal K/A down events were consumed by the Japanese composer after Fn release.

Reading only the test EditText through UI Automator after Enter returned `aaか\n`. This confirms actual physical Japanese-mode Sym/Fn pass-through, release recovery into romaji conversion, and Enter commitment/newline in this editor. The preparation mode toggle and initial empty-field check were injected events, separate from the user-operated sequence.

Capture stopped; default IME restored to the pre-test PhysiBoard dev3. No source change was needed after this physical test. Pending-composition-plus-modifier cases remain covered by injection regressions; they were not repeated physically. Other editors, other Fn chords and lost-key-up behavior remain outside this verification.

## PR finishing checks — 2026-10-24

- `:app:lintDebug :harness:lintDebug :app:assembleRelease`: PASS after replacing the test receiver registration branches with ContextCompat.registerReceiver and an explicit exported flag (the sender is the separate IME app).
- Lint warnings remain for prototype metadata/backup rules, available dependency updates and diagnostic UI text; no lint errors. Release output is `app-release-unsigned.apk`: build-verified only, not signed for distribution or installed on the device.
- Fixed runner cleanup: a failed logcat collection now emits a warning instead of skipping IME restoration. Offline `scripts/test-runner-cleanup.ps1` mocks ADB and verifies restoration with both successful and failed instrumentation while logcat fails; PASS. It does not access a device.
- Rebuilt/reinstalled and reran the complete device regression after the receiver change: `OK (1 test)`, 6.357 seconds. The runner restored PhysiBoard dev3 successfully.

## Punctuation ordering regression — 2026-10-24

Device-injected Japanese `n`, period, `k`, `a` reproduced `んか.` instead of `ん.か`. The committed text initially looked correct after the period, but the next composing syllable exposed a stale insertion position. The ordinary printable-symbol path now sends the key through the same InputConnection after committing pending text, matching the ordered modifier path.

Regression cases cover period, comma and digit 1 after pending n, followed by ka and Enter. This tests actual Android editor/IME interaction on the USB device using injected events; these punctuation sequences were not physically pressed by the user.

Punctuation fix validation completed: full device regression PASS, `OK (1 test)`, 8.875 seconds. Period/comma/digit followed by ka and Enter passed together with all earlier input and modifier cases. `:app:lintDebug :harness:lintDebug :app:assembleRelease` also passed; release APK remains unsigned and was not installed. The resumed run recorded Titan as its initial selection and restored Titan at completion.

An installation pause was caused by Play Protect asking whether to send Titan IME Test to Google for analysis. The user handled this prompt; the agent did not choose upload consent or change Play Protect settings. The earlier interrupted run had saved only build output and was not counted as a successful device test.

## Mozc integration — 2026-10-24

Pinned upstream Mozc c4b2aef7 was built from source on Linux CI for arm64-v8a, with the OSS dictionary generated by host tools. Engine/dictionary hashes were verified before importing. JNI and Protobuf are integrated; the custom composer was removed and its input regressions moved to device instrumentation. See [build, license and design notes](mozc-integration.md).

Build/install/injected-device regression PASS: `OK (1 test)`, 25.740 seconds, then 17.267 seconds after candidate/editor layout changes. Actual native-engine cases include nihongo → にほんご → Space → 日本語 → Space → alternate candidate → Enter commitment, a second Enter for newline, Escape back to reading, konnichiha, kitte, nna and kanna, plus all prior modifier, deletion, language-switch and punctuation-ordering cases. Pending Latin width and final-n behavior were reconciled through Mozc configuration and commands. The default Mozc table produced こんいちは for konnichiha; five explicit doubled-n/vowel table entries preserve existing Titan behavior without a second conversion engine.

The first conversion test lost its initial n when editor clearing triggered an asynchronous input restart. The harness now explicitly restarts input and waits for the Debug IME connection notification after clearing; the test then passed. Enter expectations intentionally changed: composing/converting Enter commits without a newline; the following Enter reaches the editor.

Candidate UI was inspected through ADB screenshots: 日本語 and ニホンゴ candidates were visible. Initial inspection exposed the old harness ActionBar covering its editor and navigation buttons overlapping the candidate strip. The harness now uses an inset-aware layout without ActionBar; the IME reserves navigation clearance. Touch selection and new physical typing are not implied by the injection results above.

App/harness Debug Lint and unsigned Release build passed after adding the required superclass call in onEvaluateInputViewShown. Offline Windows cleanup tests passed. Existing IMEs remain installed and enabled; each scripted run restored its starting default (initially the original keyboard, later Titan selected for UI inspection). USB debugging and Play Protect settings were not changed.

Final injected regression on 047da89: PASS, 17.167 seconds. User-operated physical follow-up at 15:48 JST: typing included an initial `nihin`, two Backspaces, then `hongo`, followed by two Spaces and Enter. Linux TitanKey events captured the letters, corrections and both Spaces; the capture file was buffered/truncated near its tail and does not contain Enter. Android IME logs separately show Enter at 15:48:20.230 with japanese=true/connected=true, consistent with the user's confirmation. No synthetic keys were injected during this user sequence.

Afterwards UI Automator read exactly `ニホンゴ` with no newline; the screenshot shows no composing underline and no candidate row. This physically verifies correction, candidate cycling and commitment of the alternate candidate. There is no intermediate physical screenshot of the first `日本語` candidate; that exact first-candidate assertion is from the injected test, not inferred from the final physical screenshot. Capture was stopped and the original keyboard service was restored. Artifacts remain local under test-results/mozc-physical-*.

### Candidate visibility and touch follow-up (20:37–20:42 JST)

Padding/spacer adjustments alone did not solve the candidates-only window: it remained clipped around the navigation area, despite passing text-only tests. The final frontend renders a compact input view containing only the candidate strip, with navigation clearance and no software keyboard keys. Device screenshots confirm the row appears above navigation controls. It stays visible in Japanese mode after commitment; earlier physical screenshots above used the previous frontend.

Manual ADB touch validation: after Space cycling to ニホンゴ, tapping the visible 日本語 candidate changed the editor to 日本語; Enter committed exactly 日本語 without a newline. The expanded instrumentation now finds the visible/clickable 日本語 node in Titan's own accessibility window, asserts screen bounds, injects a touch at its center, and checks composing selection and final commitment. It also checks test-editor focus before/after keys to abort if another app takes focus. Full regression PASS: `OK (1 test)`, 19.209 seconds. This touch and typing run is automated device injection, not a second user physical-key run. Starting default Titan was restored.

Integrated Linux engine/dictionary build, Android packaging/Lint and Windows cleanup CI passed on 047da89 in [run 40071525377](https://github.com/utmity/titan-ime/actions/runs/40071525377). The final candidate-view changes are checked again on their own PR head.


## Segment navigation and editor lifecycle — 2026-10-25

The device reproduced stale cursor routing: after composing にほんご and selecting position 0, typing ka inserted at the old end (にほんごか). External selection changes now finish the visible composition in place and reset Mozc, preserving the editor cursor; the regression expects かにほんご.

A second regression reproduced loss of the composing span when switching between two EditText fields. Removing the asynchronous hide request from onFinishInput prevents a previous field's teardown from finishing the next field's new composition. The complete device sequence passed in 25.989 seconds (local test-results/20261024-205819), including cursor relocation, left/right segment focus, and independent composition/commitment across two fields.

Conversion now highlights the focused Mozc segment. Left/Right and the 前文節/次文節 touch controls change segment focus; Shift+Left/Right uses Mozc's segment resizing. No physical Fn mapping was changed. Cursor movement during unconverted reading commits it before forwarding the arrow. External cursor movement finalizes visible text rather than supporting editing within an existing Mozc composition.

These new cases use Android event injection and editor callbacks on the USB device, not user physical presses. Switching to other apps remains unverified. The harness contains two separate multiline editors for lifecycle testing.

Expanded device regression PASS: OK (1 test), 26.492 seconds, test-results/20261025-041926. Includes visible touch targets for previous/next segment, Shift+Right resizing, and all earlier input/conversion cases. Debug APK was rebuilt and installed; starting Titan IME selection was restored. Local app Lint, unsigned Release and harness test-APK builds passed. Play Protect requested sending the harness APK; the one-time Do not send action was selected, with no external upload or permanent setting change.


## Range replacement and reading cursor regression — 2026-10-25

Expanded USB-device instrumentation passed in 31.655 seconds (test-results/20261025-043123). Rebuilt and reinstalled Debug/test APKs; the runner restored the starting Titan selection. No additional IME implementation change was needed.

- Compose にほんご, select [0,2), type ka: かんご, with only the replacement composing; Enter commits it.
- Convert to 日本語, select [0,2), type ka: か語, with only the replacement composing; Enter commits it.
- Compose かな, press Left: reading commits; type ki inserts at the caret, producing かきな.
- Compose pending n, press Left: Mozc resolves it to ん; subsequent ka inserts before it, producing かん.

All prior conversion, candidate touch, segment navigation/resizing, modifier routing and two-field lifecycle assertions passed in the same run. This is injected Android input with programmatic editor range selection, not a physical keyboard/selection-gesture test or validation of other apps.


## Japanese punctuation and T2E segment chords — 2026-10-25

Japanese printable punctuation now enters Mozc instead of forcing commitment and forwarding an ASCII key. Injected native-engine tests establish minus → ー, comma → 、, period → 。, left/right brackets → 「/」, with composing spans retained through following ka and Enter. Digits retain the existing direct-input policy. Other printable punctuation/quotes uses Mozc's own handling rather than a second mapping table. English input remains direct.

Read-only device inspection of /system/usr/keylayout/TitanKey.kl found H scan 35, L scan 38, Shift scan 42, Sym scan 253 (AGUI_SYM), Fn scan 251 (FUNC3). Physical capture and Android logs confirm Fn=403/251, H=36/35, L=40/38; Fn has no standard meta flag, while held left Shift produces meta 65. The requested chords reached the harness/IME, with no observed app switch. This is evidence for this device/configuration, not a guarantee against every user-defined system shortcut.

Conversion-only Fn+H/L moves focus; Fn+Shift+H/L shrinks/expands. Fn and Shift down no longer trigger the modifier bypass commitment while converting. Outside conversion, prior modifier forwarding remains. SegmentBindings separates replaceable binding data and action resolution from Mozc routing; no settings UI is claimed.

The first expanded injected sequence passed in 35.499 seconds, including Fn-down then H/L, actual Shift-key events plus shifted H/L, segment-range assertions, release cleanup, and all previous regressions. Local app/harness Lint and unsigned Release passed.

Physical follow-up at 04:44 JST: user confirmed completing Fn/L/H/Shift chords and Enter. Logs show H/L events consumed by the IME, shifted H and L reaching meta 65, composing range retained during resizing, and Enter ending composition. Some H presses preceded Shift and several presses repeated, so this is not an exact scripted segment-range round trip. Exact movement/resize assertions are from instrumentation; no fabricated physical highlight-range claim is made. Local evidence: fn-hl-logcat.txt, fn-hl-fixed-logcat.txt, fn-hl-fixed-final.xml. Capture stopped. No existing IME was removed, and USB/debug/security settings were not changed.

Final rebuilt/reinstalled device regression PASS: OK (1 test), 37.432 seconds. Includes English half-width minus/comma/period/brackets/apostrophe and Japanese punctuation composition. Physical final editor text was 日本語を勉強する without a newline. Starting Titan default restored.


## Modified punctuation follow-up — 2026-10-25

A new device regression reproduced premature commitment on the vendor Fn down event alone: after ka, the composing-span assertion failed before punctuation was pressed (test-results/20261025-045015). Modifier-only Fn/Sym/Function/Shift presses now preserve an active composition. Actual ASCII punctuation reported by Android, including Sym/Function-modified punctuation, enters Mozc before the modified-letter bypass. Ctrl/Alt/Meta shortcuts retain their prior route; no speculative Fn-letter-to-symbol map was added.

Rebuilt/reinstalled regression PASS: OK (1 test), 39.036 seconds (test-results/20261025-045112). Vendor Fn down preserves か, then minus produces composing かー; Sym down preserves か, Sym+comma and Function+period produce composing か、。. All earlier English punctuation, modified-letter forwarding, Fn segment navigation, kana-kanji conversion, candidate selection and lifecycle tests passed in the same sequence. Local Lint and unsigned Release passed; starting Titan default restored.

These new symbol combinations are injected Android events, not new physical key observations. They verify handling of a punctuation character actually delivered by Android; physical Fn/Sym letter keys that still report letters are deliberately not remapped.


## IME settings and two-key mode switching — 2026-10-25

Before work, GitHub main remained 1da9d397 and PR #1 remained open/unmerged at 04ec52c9, matching the checkout. Changes continue on the existing PR branch. Previous complete native-device regression was 39.036 seconds with CI passing.

Physical candidate survey at 11:13 JST: Fn+Space reached the IME as vendor Fn 403/251 followed by Space 62/57 with meta 0; Sym+Space reached Space with meta 4; Alt+Space reached Space with meta 18. The user confirmed performing all three. Logs remained in the harness with Titan selected; no app switch was observed. This verifies delivery on this configuration, not every possible customized Android shortcut. Fn+Space was selected as default; Ctrl+Shift+J is removed.

SettingsActivity is registered through the input-method XML settingsActivity attribute. The actual Android screen-keyboard settings list opened Titan's dedicated settings when its label was tapped (not its enabled switch). The screen shows current saved EN/JA mode, three toggle choices, H/L or J/K segment profiles and main operations. Preferences save immediately; the running IME resolves them per key event. Segment movement and resizing change together as a collision-free profile, not arbitrary individual shortcuts. Mode and assignments are private SharedPreferences; no IME enable/default settings are modified by the screen.

Expanded full device instrumentation passed in 51.157 seconds: it opened the real settings UI, selected Sym+Space and Alt+Space, verified Japanese composition and commitment on switching, selected J/K and asserted segment movement plus resizing, then restored Fn+Space/H/L/English. Existing conversion, punctuation, candidate touch, modifier handling and lifecycle tests passed. Local app/harness Lint and unsigned Release passed.

Process restart persistence: selected Sym+Space/J/K through the UI, temporarily selected PhysiBoard, force-stopped Titan, reopened settings, and verified both radio selections and the saved preference XML survived. A subsequent immediate shell-keycombination input attempt ran before a usable connection and did not establish post-restart switching; it is not counted as a successful input test. Standard choices were restored through the settings UI and Titan reselected. Physical survey logs and settings XML snapshots are local under test-results/.


Physical default-switch check at 11:20 JST: Fn=403/251 and Space=62/57 were delivered, and subsequent key logs changed English → Japanese → English → Japanese. The user added/reordered presses; the final field was kka\nあ rather than the requested かa, so the exact requested sequence is not claimed. These logs verify physical Fn+Space changes mode, keeps the harness active, and commits composition on leaving Japanese. Settings were then used to return to the standard Fn/H-L profile and English mode for the final regression.

Final installed regression PASS: OK (1 test), 51.059 seconds, including the retired Ctrl+Shift+J chord leaving English mode unchanged. Standard Fn+Space/H-L and English mode restored at completion.


## In-composition editing and physical Alt punctuation — 2026-10-25

The prior frontend deliberately finished every selection away from the end. Pinned upstream Mozc session.cc MoveCursorTo (around line 2507) accepts MOVE_CURSOR/cursor_position only in COMPOSITION; conversion receives DoNothing. ProtoCommands.Preedit.cursor and segment lengths use character positions. The frontend now translates Android UTF-16 offsets to code-point positions and translates output cursor positions back, updates InputConnection selection in the same batch as composing text, and retains the session for internal movement. Outside-range movement or a non-collapsed selection still finalizes visible text. Conversion taps select a segment through Mozc LEFT/RIGHT without discarding candidate state. The new 読みに戻す control explicitly returns to reading before character editing.

Device regression PASS in 59.242 seconds, then final installed APK PASS in 59.635 seconds including the reading button. Tests inject actual touchscreen taps at editor layout offsets, insert ka before ご in にほんご, Backspace it and reconvert to 日本語. They repeat with a preceding supplementary emoji to exercise absolute UTF-16 positions, verify outside-range commitment and subsequent insertion, verify conversion segment taps, and retain previous candidate/toggle/settings/lifecycle tests. Local app/harness Lint and unsigned Release passed.

Physical symbol reproduction at 18:07 JST: after correcting mode to Japanese, the user entered ka then Alt+I/M. Android logs show ALT_LEFT 57/56 with meta 18, I=37/23 and M=41/50; the editor contained kaか-. The original Alt-down path committed before the printable key. Read-only dumpsys input identifies TitanKey as device 0 using /system/usr/keychars/TitanKey.kcm; this maps Alt+I to -, Alt+N to comma and Alt+M to period. Alt-only now preserves composition, and resolved ASCII punctuation (except Ctrl/Meta shortcuts) enters Mozc even with Alt.

Testing distinction: Instrumentation.sendKeySync rewrites the event to device -1/Virtual.kcm (Alt+I produced a different accent); direct device-0 injection was rejected. These failed attempts are not counted as physical Alt success. Automated tests separately assert the actual device-0 character-map values, then inject their resolved punctuation; physical Alt routing is checked separately with the user.

The device now has only Titan enabled. An old fallback-IME argument failed before installation and did not change enabled IMEs. Subsequent tests left that list unchanged, restarted Titan after installation, and explicitly restarted the harness input connection before waiting for the ready broadcast. Tests now read the debug-only ready mode flag to normalize a previously persisted Japanese mode. One Play Protect prompt was declined for this upload only, without changing settings.

Limitations: non-collapsed range replacement does not retain the Mozc composition; conversion-state character editing requires 読みに戻す (or Escape); editors that do not report composing ranges and other apps are not verified. An outside-range move commits the visible text rather than synthesizing another conversion. These are documented in README.


Physical fixed-APK verification at 18:23 JST: tapping before ご changed selection 4→3 while composing stayed [0,4). Physical K/A inserted within that range; Backspace restored it, Space converted and Enter committed 日本語. The user then accidentally used Fn+I/N, adding literal in (reported by the user and confirmed in events). Subsequent actual Alt+I/N/M (meta 18) created a single composing range growing [5,6) → [5,7) → [5,8), then Enter ended composition. Final UI text was 日本語inー、。. Thus physical cursor editing and physical Alt punctuation succeeded; the extra in is not omitted or attributed to conversion. Evidence: test-results/cursor-symbol-physical-final.txt and .xml. The initial にほんご was prepared by injection; tap/edit/Alt/Enter operations were user physical input. Titan remains selected; enabled IMEs were not altered.


## Two-key resizing and local conversion learning — 2026-10-25

Fn+U now shrinks and Fn+O expands the focused conversion segment, in both H/L and J/K navigation profiles. The physical Shift requirement is removed; Mozc still receives its normal Shift+Left/Right internally. Existing stored profile selections automatically use the new two-key resize bindings. Settings and README show the actual chords.

Learning is on by default, using Mozc DEFAULT_HISTORY with incognito disabled. The setting can turn learning off (incognito) without deleting saved data. Password/non-text fields continue bypassing Mozc. History remains in the app-private no-backup profile; input finish requests SYNC_DATA, whose pinned upstream handler performs Engine::Sync and Wait. No custom keystroke log or cloud service is added. Mozc's own storage policies manage history size.

Full device regression PASS: 64.711 seconds, then 64.113 seconds with persistence-test expectations recorded. Deterministic conversion cases run with learning off, then the settings UI enables learning and the test selects a non-first suisei candidate, commits it, starts a fresh session and verifies it becomes the next conversion's first candidate. The final saved test candidate was 水星. All cursor/tap/punctuation/settings tests and Fn+U/O expansion/contraction assertions passed. Local Lint and unsigned Release passed.

Cold persistence test: force-stopped both IME and harness, launched a fresh IME process, and separately ran LearningPersistenceTest. PASS, 3.05 seconds: suisei converted first to the recorded 水星 after restart. The app-private .history.db and segment.db files were present; their private contents were not published. The test class is intentionally separate from the full regression to allow a real process restart between runs. Settings were left at learning on, Fn+Space and H/L navigation.

An initial input-ready timeout was resolved by stopping the old harness process before the test; it is not counted as a passing run. The enabled IME list remained unchanged.


Physical two-key check at 23:19 JST: Fn+O delivered key 43 without Shift (meta 0), changed the composing range length 8→9; Fn+U delivered key 49/meta 0 and returned length 9→8. Enter then committed. No app switch occurred. Final candidate text was 日本語を便供する: segment resizing may reselect conversion candidates, so this is not claimed as unchanged wording. Exact segment-span expansion/contraction is separately asserted in instrumentation. Physical evidence: test-results/two-key-physical.txt and .xml. Learning remains enabled.

## Prediction, correction, dictionary and reading-range editing — 2026-10-26

Continued PR #1 on codex/device-test-harness; starting local/remote HEAD was 76a288638e55c0f61a711b7a081947ff394ce63f, main 1da9d397663e947894b305941e95cc72fc810cec. Device TITAN20000052435 remained connected. No IME was uninstalled, no enabled-IME list or USB debugging setting was changed. Only Titan is enabled.

The earlier pending reading-range work passed long-press injection (75.952s) before this task. It now ships with these changes: range selection entirely inside unconverted composition remains composing; replacement/deletion use native cursor/backspace operations. Both selection directions, emoji-prefixed offsets, small-kana replacement and punctuation replacement are exercised. This supersedes the previous range-editing limitation above; conversion-state ranges and selections crossing the composing boundary still finalize visible text.

| Validation | Result | What was actually exercised |
| --- | --- | --- |
| MozcFeaturesTest, final APK | PASS, 0.310s | Real arm64 JNI, suggestions while typing, candidate update/submit, correction-on/off, original reading retained, user dictionary add/edit/delete, same-reading entries, learning-on/off, invalid input, normalization, duplicate import and TSV export/delete/import round trip |
| DictionaryUiTest | PASS, 7.827s | Accessibility-driven actual settings/dictionary dialogs, search/edit/delete; standard Android create/open document pickers, UTF-8 TSV export and re-import |
| DictionaryPersistenceTest | PASS, prepare 0.088s / verify 0.105s | Separate instrumentation processes with force-stop in between; registered entry appears in native conversion after restart |
| Full InputDeviceTest | PASS, 88.383s | Injected keys/touches: prediction and correction tap commit, literal-reading control, prediction-off Space conversion, Fn toggle, Alt map/punctuation, segment movement/resizing, cursor/range editing, normal candidate selection, learning and direct password input followed by normal-field Japanese input |
| LearningPersistenceTest | PASS, 3.089s | Learned preferred candidate survives IME process restart after changing learning-off from incognito to NO_HISTORY |
| Local build | PASS | Debug app and both test APKs; app/harness Lint and unsigned Release |

These times describe test execution, not per-keystroke latency measurements. Raw results remain under ignored test-results/ (features-*, dictionary-persistence-*, physical-prediction-*). Test-added dictionary entries use unique names and only those entries were removed. The four document-picker test files were removed by exact path afterward; existing dictionary/history/user files were preserved. Settings finish with prediction, correction and learning on; Fn+Space; H/L navigation and U/O resizing.

Physical trial: the user replied 操作済み. At 11:11:36 JST the final fuinki sequence retained composing [0,4), and at 11:11:40 the selection moved to 3 with composing removed. The subsequent nihongo sequence at 11:11:58 retained composing [3,7), and at 11:12:01 committed at selection 6. There was no Space key in either final sequence. The final UI dump shows **雰囲気日本語**, not the requested 日本語雰囲気: the user entered the two words in reverse order. Earlier attempts included additional toggles, deletion and Space; these are not silently treated as the exact scripted sequence. Combined with the user's candidate-tap instruction and completion report, this supports physical input plus prediction/correction selection in the harness. It is separate from the exact injected-tap assertions. No human dictionary-form/file-picker trial or other-editor compatibility is claimed.

Measured correction coverage: fuinki → ふいんき remains uncommitted until selection; 雰囲気 has SPELLING_CORRECTION and is selectable before Space. shi/si both produce し. nihonngo produces にほんご via romaji nn handling. nihngo did not suggest 日本語, nihonfo suggested 日本フォ… alternatives rather than 日本語, and sunkai did not offer 深海. No general typo-model success is inferred from enabling a flag; OSS supplemental typing model is a stub. There is no automatic replacement or independent candidate reordering.

Issues found and fixed during this work:
- First build lacked ANDROID_HOME; rerun with the installed SDK. Android lint caught readNBytes requiring API 33; replaced with a bounded stream read supporting minSdk 28.
- Native dictionary assertions initially failed with learning off. Upstream incognito excludes the user dictionary; NO_HISTORY now disables learning without excluding explicit entries. Full-length segment checks distinguish segmentation from absent entries.
- Dictionary UI uppercased Latin words through Button's default all-caps styling; disabled it for entry rows.
- Document creation with text/plain added .txt to .tsv; changed MIME type to text/tab-separated-values. Test selection also needed to click the containing document row, not its non-clickable title.
- Instrumentation preference restoration now uses commit before process exit; production settings retain normal apply-based persistence. No failed attempt above is counted as a passing test.

Remaining limits: arbitrary adjacent-key mistakes, missing/extra-key correction, custom physical-layout correction, unsupported POS types, paste and other-editor compatibility. The JNI bridge uses canonical dictionary storage plus RELOAD_AND_WAIT because the old dictionary-edit command IDs are reserved in this pinned revision; it does not pretend those removed APIs still exist. See prediction-dictionary.md and README for operations and scope.

Final harness follow-up: prediction/correction-on password input remained literal fuinki- without composition; returning to the normal field restored か. A leftover DocumentsUI activity initially blocked the settings locator. UI tests now clear only their Titan settings task before opening it and close that task after dictionary tests; reruns passed. Physical trial setup stores the desired mode, but a live IME service also needs a restart to reload it; do not infer its starting mode from preferences alone.

## 2026-10-26: modifier routing and kana correction

- Debug APKs, both instrumentation APKs and Android lint: PASS (48 seconds).
- Release assembly: PASS. Offline runner cleanup tests: PASS, including simulated test/log failure.
- Titan native instrumentation: PhysicalKeyboardPolicyTest, KanaCorrectionTest, MozcFeaturesTest, 3 tests PASS (0.662 seconds). Policy tests construct events against the actual TitanKey KCM; they are not physical key presses.
- Full injected input suite: PASS (94.461 seconds), including held Ctrl/R Down/Up pairing, unchanged composition on modifier-only Down, existing punctuation/segments/editing/password checks, prediction taps for 学校 and 病院, and literal preservation. Two initial attempts timed out before input because the harness was not foreground; explicitly opening the harness resolved it. The runner now opens that isolated task before instrumentation. Failed attempts are not counted as successes.
- Physical baseline: the user's right Shift mapping delivered key 113/scan 54/meta 12288. The previous IME generated premature Up events for held Ctrl. The accidental Fn trial is excluded from Ctrl results.
- Termux 0.118.3: source confirms left-Alt is stripped from Unicode lookup and forwarded as Meta. New-session creation and drawer navigation via injected Ctrl+Alt shortcuts worked. Physical Alt+R appended 3 without losing the prepared line (echo TITANALT3 produced TITANALT3); right Shift mapped to Ctrl+C cancelled abc (abc^C and a new prompt). These results were verified from the terminal screen separately from injected tests. In the Meta setting, physical Alt+B moved the cursor from the end of echo METATEST to the M of METATEST. The result was inspected on October 27; Alt text mode was restored and its checked UI state verified. ChatGPT-specific clipboard behavior remains unverified.
- Mozc standard kana-modifier-insensitive search is enabled by the correction preference in both Config and Request. katukou -> 学校 and hiyouin -> 病院 appear as prediction candidates; han -> パン appears in Space conversion. Exact-reading user dictionary priority remains first with correction on/off. No automatic replacement or new native/JNI build is involved. A 40-key snapshot measured correction-off p50/p95 1168/2355 microseconds and correction-on 1144/2379 microseconds; this is not a general latency benchmark.

Details and remaining terminal limitations: [modifier compatibility](modifier-compatibility.md).

## 2026-10-27: clipboard and composing-range regression

Full injected suite PASS (100.721 seconds); harness test APK build and lint PASS.
New cases exercise the selected IME and a real EditText, not a mocked connection:

- Copy the selected ほん inside composing にほんご: composition ends, selection
  remains 1..3, clipboard contains only ほん, and paste at the end gives にほんごほん.
- Cut that same range gives にご; paste restores にほんご. Continuing ka at the
  restored cursor gives composing にほんかご; Enter commits without duplication.
- Ctrl+A/X/V on a whole Japanese reading restores にほんご.
- English Ctrl+A/C/V produces abcabc.

The test saves the existing ClipData and restores it in finally without logging
its content. The previous modifier implementation passed these cases unchanged;
this follow-up adds regression protection, not another production routing change.
These are injected Android events. ChatGPT-specific clipboard handling and
physical clipboard chords in other editors remain unverified.

## 2026-10-27: final Up after modifier changes during repeat

Reproduced with injected events on T2E before fixing: Japanese mode received
Ctrl+R Down (passed to the app), then unmodified R repeat Down (consumed by Mozc),
then R Up. The app observed zero Up events; the assertion expected one and failed
after 25.970 seconds. The previous consumed-key set remembered any consumed Down
and incorrectly suppressed Up even though the app had received another Down.

The per-device/key record now suppresses Up only when every Down of that press
was consumed. An initial Down resets the record; release and input lifecycle
transitions clear it. Repeat text/shortcut routing remains unchanged.

After fixing and reinstalling, the full injected suite PASS (101.357 seconds),
including both Ctrl release and Ctrl press during R repeat, existing held-modifier
checks, clipboard editing and Japanese conversion regressions. Debug/Release
builds and app/harness Lint PASS (23 seconds). This verifies Android event routing;
the precise physical hold/release sequence has not been manually reproduced.

## 2026-10-27: editor normalization, initial candidates and contrast

Starting HEAD: 5eef9d0be3ae81347a45f4ae479a522ba4941d7c, branch
codex/device-test-harness, PR #1. Its CI run 40427213091 was cancelled, not a pass;
the earlier b5ff1f7 CI passed. No Shift hardware or Discord uppercase fix is included.

Reproduction before fixes:
- Native asynchronous setText and restartInput cases passed unchanged.
- A composing InputConnection that synchronously changes k to 別 or 別名 lost
  that host text on the next a: actual か instead of 別あ / 別名あ. Both failed.
- Color ownership assertion failed: one fixed BackgroundColorSpan was present.
- After Space, native EditText and local WebView showed 日本語 but no selectable
  candidates. Screenshots confirmed the strip itself was present. Pinned Mozc
  filled all_candidate_words while leaving candidate_window absent on first Space.

Fixes and verification:
- Compare available actual editor text with the reading before continuing a
  session/candidate action, and immediately after writing composition before
  applying Mozc's cursor. A confirmed mismatch retains the host text/selection
  and discards the stale session. Same/different-length normalization now passes.
- Bold plus underline replaces the fixed segment background. Light/dark EditText
  span assertions pass and captured screens show readable black/white text.
- First conversion uses up to eight native all_candidate_words when the desktop
  candidate window is absent. Real JNI CandidatePresentationTest PASS (0.110s):
  first-Space window absent, native candidates present, valid IDs highlight/commit.
- EditorCompatibilityTest PASS, 8 tests (23.735s): setText, synchronous
  normalization, restartInput, hide/fresh-input/refocus, light/dark editor colors,
  local WebView conversion and a JavaScript value rewrite. No remote web content.

Failed attempts are separate: an APK-update input readiness failure preceded the
functional rerun; the fixture now waits for window focus before restartInput.
The first recovery test incorrectly expected another Space to convert after
Android hid the input view and finalized composition. It now checks fresh input
and candidate recovery rather than forcing reconversion of committed text.

These are automated operations on the actual T2E. The user clarified Discord's
symptom as “romaji does not become kana” and opened the affected channel-name
field. A read-only capture showed com.discord, inputType=0x90, imeOptions=0x6,
fieldId=1362, Titan selected, and Japanese mode enabled. The field has no text
class and contains visible-password variation bits, so Titan's existing direct
input policy applies. This establishes why this observed field bypasses Mozc;
it does not establish a React rewriting defect. No Discord name was typed/saved,
no package-specific exception was added, and no Discord conversion fix is claimed.
See editor-compatibility.md for implementation scope and limitations.

Additional results: full InputDeviceTest PASS (108.661s), including previous Fn,
Alt/Meta-policy, held/repeated Ctrl event, punctuation, segments, selection,
clipboard, prediction and learning coverage. Debug/Release and both Lints PASS.
MozcFeaturesTest, KanaCorrectionTest and CandidatePresentationTest also passed
in the combined native run; the initial picker test in that run failed before
opening UI because Android 16 blocks direct enabled_input_methods settings reads
for target SDK 35. It now uses InputMethodManager.enabledInputMethodList.

Manual ADB UI verification with Titan selected displayed the standard Android
picker with Kika and Titan entries; dismissed without selecting anything. The
Kika-selected picker test passed (1.643s), and the original Titan selection was
restored. A test in the IME's own instrumentation process was timing-sensitive
while restarting the selected IME, so the picker test was moved into the separate
harness process. No IME was enabled/disabled, no user dictionary was removed,
and the observed left Shift hardware issue remains outside this change.

Final isolated picker tests PASS: Titan selected 1.316s, Kika selected 1.701s;
both assert that selected/enabled IME state is unchanged by opening/dismissing.
Final EditorCompatibilityTest PASS, 9 tests (25.903s), now including the observed
0x90 direct-input flags and successful kana input after returning to a text field.

# 2026-10-29: batch synchronization and editing support

## Subsequent Niagara lifecycle work

- Latest baseline was 3085282. Real Niagara home/search metadata and ordered
  diagnostic events distinguished unsolicited UI display from native-session reset.
  See launcher-lifecycle.md. No package-name exception or native Mozc change.
- Actual-app ADB injection: he/ka/shi/konnichiha yielded へ/か/し/こんにちは
  after the final window-callback fix, with home window hidden on each repetition.
  A second pass without added per-key sleeps also passed. Earlier failed passes
  remain documented; an initial successful he trial was not generalized to all runs.
- LauncherHandoffTest final 3 PASS, 8.821 s; full input regression PASS, 105.348 s;
  EditorCompatibilityTest 12 PASS, 33.651 s. Debug/Release and app/harness Lint PASS.
- Physical Niagara trial confirmed by the user (recorded 2026-10-30): direct
  home typing produced へ and こんにちは; closing search with Android Back hid
  the white bar on home. ka/shi are injection-only. Fresh physical
  LINE/Discord/Termux trials remain unverified for this change.
- Prior human editing trial: user confirmed Fn+H middle insertion/correction,
  conversion/commit and Fn+V opening worked. Esc was not tested because T2E has
  no Escape key. Repeat Fn+V or the close button is the documented alternative.
- Test harness install prompted Play Protect to upload an unknown APK. Chose
  the one-time decline; persistent upload settings were not changed.

- Actual physical ChatGPT trial: user confirmed composition remained active and
  repeated Space (including more than twice) continued conversion. No message was
  sent by the agent. Debug metadata trace retained conversion state; diagnostic
  logging was then disabled. See batch-editor-synchronization.md for before/after.
- Candidate visibility: found input-view flag true with hidden IME window;
  checking decor visibility fixed the reproduced missing-show-request case.
  Compatibility suite before new features: 10 tests PASS, 28.224 s.
- New EditingSupportTest: 3 tests PASS, 0.061 s (isolated bounded history,
  opt-out/sensitive/size exclusion, duplicate/pin/delete/reopen, paired navigation
  events/modifier exclusions, unsupported inline UI request).
- ClipboardIntegrationTest: PASS, 6.351 s. Real ClipboardManager notification,
  physical-event injection to open/search/select/paste, pin/persistence/delete,
  and sensitive clip exclusion. Existing history was retained; temporary settings
  and the captured clipboard were restored. This is not human physical-key proof.
- Earlier integration attempts failed because app instrumentation replaced the IME
  process without rebinding. A failure-cleanup bug could clear the current clipboard
  before capturing it; it was corrected with an explicit capture guard and disclosed
  to the user. No dictionary or learning data was cleared. Test rebind uses only an
  already enabled fallback and restores Titan; no IME is enabled/uninstalled.
- New navigation/compatibility suite: 11 tests PASS, 32.728 s. Reading insertion,
  emoji navigation, anchored surrogate/combining selection and multiline movement.
  A test initially showed that synthetic Shift meta alone did not extend EditText
  selection; horizontal extension now uses full snapshots and ICU boundaries.
- Final compatibility suite including menu/history open-close composition
  preservation: 12 tests PASS, 37.450 s.
- Full injected input regression PASS, 105.299 s. An initial connection-not-ready
  failure after app instrumentation was retained; reconnecting before the retry
  resolved it. Existing modifier, password, native input and editing cases passed.
- Debug/test APK and unsigned Release builds, app/harness Lint passed. The merged
  application manifest adds no permissions and retains allowBackup=false.
- Actual new Fn navigation/panel keys, LINE/Discord/Termux physical regression,
  provider-backed inline autofill, and cross-app Undo/Redo remain unverified unless
  separately recorded below. Password-field inline autofill is not implemented.



# 2026-10-30: selection transforms, voice and password inline UI

Baseline e530e74277e6406aef93c720db63c8d7d14661f9, PR #1 remained open.
Device TITAN20000052435 connected. Read-only provider checks found Google Play
services autofill and Google TTS recognition. No provider settings were changed.
See input-extensions.md for APIs, supported operations and boundaries.

- JVM mapping tests: 2 PASS; voice cancellation/one-shot token tests: 2 PASS.
- EditorCompatibilityTest: 14 PASS, 34.529 s. Two new cases cover selected-only
  uppercase, no selection, cancellation and preservation of composing text.
- Additional all-nine-transform device test PASS, 10.517 s, including voiced
  kana, multiline/emoji and outside-selection preservation. The first attempt
  failed before testing because the editor connection was not ready after APK
  update. Reconnected the already-enabled Kika/Titan IMEs, ending on Titan.
- Existing full input sequence PASS, 104.891 s. Launcher handoff fixture 3 PASS,
  7.694 s. These are instrumentation/key injection, not physical human trials.
- Debug/unsigned Release, app/harness Lint and JVM tests passed. CI now runs the
  JVM tests as well. Native Mozc was not changed.
- App and test APKs installed. Test-harness Play Protect upload prompt was declined
  for this installation only; no persistent setting was changed.
- Human microphone recognition and Google/Bitwarden provider-backed filling are
  pending. Requested a non-secret test target and explicit Japanese/English voice
  operation. No microphone permission was silently granted and no recording was
  started by the agent. No credential contents were inspected.
- Fresh physical Niagara/ChatGPT/LINE/Discord/Termux regression is not claimed.
  Previously confirmed physical behavior and this run's mock regression results
  remain separate.

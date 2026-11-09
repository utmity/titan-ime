# Launcher input lifecycle (2026-10-29)

## Two independently observed failures

On Titan 2 Elite, Niagara's ordinary home keeps a text connection with inputType
0x800b1 (TEXT/FILTER/NO_SUGGESTIONS), fieldId -1 and selection 0,0. Its actual
search field uses 0x4001 and a different field ID. These are observed metadata,
not package-name conditions in Titan.

### A: empty candidate bar on home

Titan previously called refreshStrip with visibility updates from onStartInput,
requesting its window for any text connection, even with no composition. Closing
search could also carry the old window into the passive home connection. Android's
input-view flag and even DecorView.isShown were observed remaining true after a
window hide; neither alone reliably represents the current IME window.

The fix renders without requesting visibility on input start/view start, tracks
onWindowShown/onWindowHidden, and requests redisplay for composition or an opened
support panel. A fresh empty FILTER connection does not inherit the old window;
requestHideSelf is issued at that connection's start, not at an unrelated old
onFinishInput. Explicit application show requests still work. Ordinary editors
retain the menu/history bar when the application requests input UI, and Fn+V can
explicitly open history. No launcher package check or global suppression exists.

Public APIs do not expose whether an arbitrary application calls its focused
editor a “home screen.” FILTER is an input-type policy, not a universal invisible
view detector. Other filtering editors may need an application show request or
actual input before their bar appears; no app-private view lookup is used.

### B: first letter separated from romaji

The initial `h` **does reach Titan**. In the captured sequence it was consumed,
Mozc returned no committed result, and Titan wrote a one-character composition to
the proxy. Niagara then finished that connection, started an empty search editor,
copied the emitted character, and restarted input with selection 1,1. Titan reset
Mozc on both starts. The following `e` therefore produced `hえ`. This differs from
the ChatGPT stale-in-batch-snapshot failure; that correction remains intact.

InitialKeyHandoff retains only a receipt for Titan's own just-written character:

- Japanese mode, hidden IME window, FILTER source, empty complete source snapshot,
  first unmodified lowercase letter, non-repeat, accepted composing write;
- Mozc still exposes exactly that unresolved letter and no committed result;
- within 1.5 seconds, the same package starts a different field at selection 0,0,
  then restarts that target at 1,1;
- the complete target snapshot is exactly the one recorded character, with no
  prefix/suffix/selection change and no password/direct field.

Only then Titan marks that known region composing and replays its own key to the
new Mozc session. It never scans trailing letters or imports arbitrary existing
text. A consumed first-key Down retains its matching Up through this transition.
Receipts expire and are discarded on mismatches. No native engine changes.

This intentionally does not recover keys the IME never received, larger copied
prefixes, missing/partial snapshots, or transitions beyond the deadline. It is
not a general composition transfer between arbitrary editors.

## Evidence and reproduction

ADB key injection into the actual Niagara app first reproduced hえ. Early fixes
failed repeat-entry cases because stale view flags prevented the receipt from
being created; those failures were retained in ignored test-results. With window
callbacks, the actual-app sequence produced he→へ, ka→か, shi→し,
konnichiha→こんにちは, including a repeat with no extra per-key delay. Each home
snapshot reported mWindowVisible=false. These are actual-app automated results,
not physical-key confirmations.

LauncherProxyActivity models an invisible FILTER editor copying its first write
to a different visible field and restarting it. LauncherHandoffTest passed three
tests (final run 8.821 s): preserved first letter and Down/Up, existing prefix not reopened,
and expired receipt not reused. The fixture does not prove arbitrary launcher
compatibility. Existing delayed-batch, EditText, WebView and full input regressions
passed as well: full InputDeviceTest 105.348 s, 12 EditorCompatibilityTest cases
33.651 s. Debug/unsigned Release and app/harness Lint passed after replacing
literal fixture view IDs with View.generateViewId.

Physical Niagara confirmation (user report, recorded 2026-10-30): direct home
typing produced へ and こんにちは, and Android Back closed search with the white
bar disappearing on home. ka/shi remain injection-only results. Fresh physical
LINE/Discord/Termux regressions were not performed for this change.

For human confirmation: select Japanese mode, go to Niagara home, verify no bar,
type he without opening search first, verify へ; close search with Android Back
and repeat with ka/shi/konnichiha. Also open search explicitly and type normally.
Use Android Back/Home, not a nonexistent physical Escape key on T2E.

Opt-in debug: `adb shell setprop log.tag.TitanLifecycle DEBUG` and optionally
`log.tag.TitanState DEBUG`. Lifecycle logs contain generation, key Down/Up timing,
connection/UI flags, editor metadata and lengths, never character codes/text.
Disable both with INFO after the deliberate trial. Password text is never read
for handoff. Do not publish raw screenshots or logs containing unrelated app UI.

## Android selection menu

The input application owns TextView's selection/insertion ActionMode callbacks;
an IME has no public API to install arbitrary items into another app's menu.
ACTION_PROCESS_TEXT is a separate public activity-based extension for processing
selected text in supporting apps. It is not an IME menu injection API or a way to
force a password vault/picker into every editor. No such extension is added here.
Titan retains its own … menu, system IME picker, and host autofill request.

- [InputMethodService lifecycle](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [TextView selection callbacks](https://developer.android.com/reference/android/widget/TextView#setCustomSelectionActionModeCallback(android.view.ActionMode.Callback))
- [ACTION_PROCESS_TEXT](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT)

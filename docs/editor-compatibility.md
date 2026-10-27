# Editor state, candidates and conversion display

The engine session (reading/candidates), the host editor's text/selection and the
IME window are separate states. A candidate click must refer to a reading still
present in the host, not merely to a retained candidate ID.

## First-Space candidate visibility

Pinned Mozc EngineConverter::FillOutput emits all_candidate_words in conversion
even while candidate_list_visible_ is false and candidate_window is absent.
The previous renderer read only candidate_window, so the first Space could show
the converted text without selectable candidates. CandidatePresentation keeps
the ordinary window when present, otherwise shows at most eight native candidates
from all_candidate_words during conversion only. Native IDs/order and highlight
commands are retained; prediction-off does not create a fallback prediction list.
This is verified with the real engine and both EditText and local WebView.

Source: [pinned EngineConverter](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/engine/engine_converter.cc).
This explains one reproducible case, not every reported intermittent disappearance.

## Reproduced normalization defect

A test InputConnection normalizes a composing `k` to `別` or `別名` while keeping
the composing range. Before the fix, the next `a` replaced the host's normalized
text with `か`. Matching selection/range length was insufficient: same-length
normalization could leave every selection offset unchanged, while the old code
ignored differing composing lengths as obsolete notifications.

Before continuing composition or selecting a candidate, Titan compares the known
reading range with an available ExtractedText snapshot. If the text differs, it
finishes the host composition without replacing its text, discards the old Mozc
session/candidates and starts subsequent input afresh. A missing or partial
snapshot is not treated as proof of a mismatch. This is preservation/recovery,
not bypassing an editor's validation or guaranteeing kana conversion through an
editor that normalizes every individual composing update.
The comparison also happens after setComposingText and before applying Mozc's
cursor, so length-changing normalization keeps the host's resulting cursor.

Ordinary asynchronous setText and restartInput cases passed before this change;
no speculative restartInput rewrite was added. These simulations do not establish
Discord's implementation or demonstrate a Discord fix.
Android hiding the input view can finish composition; fresh input must show new
candidates, but a Space after that commit is an ordinary space, not reconversion.

## Conversion display

Previously Titan attached a fixed pale-blue BackgroundColorSpan to the selected
segment inside the host editor. White text in a dark editor could have poor
contrast against that background. Titan now marks the segment with bold and an
underline, leaving foreground/background colors to the host. The candidate bar
uses its own black-on-white rendering; Android/editor selection colors are
outside this change. Hosts may ignore or replace IME spans.

## Observed Discord channel-name field

The user opened the affected field; read-only dumpsys showed package com.discord,
inputType=0x90, imeOptions=0x6, fieldId=1362, with Titan selected and Japanese mode
enabled. 0x90 contains visible-password variation bits but no TYPE_CLASS_TEXT.
Titan consequently uses its existing non-text/password direct-input path, which
explains why romaji remains literal in this observed field. This is not evidence
of React-controlled rewriting, and this field was not used to test normalization.

No package-name exception or automatic override of password/non-text handling was
added. Japanese composition in this specific field remains unresolved; a host
input-type correction or a separately designed explicit compatibility override
would require further work. No channel name was typed or saved. A native fixture
using these exact raw flags checks direct input and recovery in a normal text field.

## Independent IME picker

Settings includes “入力方法を切り替える”, invoking Android's
InputMethodManager.showInputMethodPicker(). It has no dependency on Mozc, the
candidate view or an active Titan service. It does not enable another IME or
select one automatically. Android controls which enabled methods can be chosen;
if only Titan is enabled, another method must first be enabled in Android settings.

API references: [InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection),
[InputMethodManager](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager#showInputMethodPicker()).

## Verification boundaries

EditorCompatibilityTest exercises native EditText replacement, synchronous
InputConnection normalization, restartInput, candidate-window hide/refocus,
light/dark editor colors and an entirely local WebView textarea. Web content has
no remote scripts or network access. InputDeviceTest retains the conversion,
segment, editing, clipboard and physical-event-routing regression suite.

Record actual outcomes in device-validation.md. These are automated Android
tests, not human key trials or evidence that a real Discord channel-name editor
has been repaired. Shift hardware response and channel-name uppercasing are
explicitly outside this investigation.

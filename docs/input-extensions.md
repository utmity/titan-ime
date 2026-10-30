# Input extensions (2026-10-30)

## Selection transforms

Select committed text, press Fn+T or choose … → 文字変換. Fn+J/K selects an
operation, Enter applies, Android Back or repeat Fn+T closes. Settings supports
Fn+G or disabling the binding. These keys do not overlap the existing segment,
cursor, clipboard or language bindings. Physical T2E Fn+T/G delivery still needs
human verification; injected events are tested separately.

Nine operations: ASCII Latin upper/lower/toggle, alphanumeric full/half width,
hiragana/katakana, and katakana full/half width. Only the relevant character ranges
are normalized: unrelated compatibility symbols such as ① and ligatures survive.
Voiced/semi-voiced and combining kana marks are handled; unsupported small ヵ/ヶ
remain unchanged. Full-width katakana may expand into two half-width characters.
Latin case operations intentionally target A–Z/a–z, not language-dependent Unicode
case folding. Newlines, emoji and supplementary characters remain unchanged.

No selection means no operation. Password/direct fields are excluded. Pending
Mozc text is left untouched with an instruction to commit first. A complete
editor snapshot and matching selected text are required, with grapheme boundaries
and a 10,000 UTF-16-unit limit. Replacement uses one commitText in a batch. The host
places the cursor after replacement; the selection is collapsed. No stale absolute
selection is reapplied after host normalization. Editors withholding snapshots or
concurrently changing selection may reject the operation; InputConnection does
not provide a cross-process compare-and-swap transaction.

## Voice

… → 音声入力 exposes Japanese and English start buttons, status and cancellation.
SpeechRecognizer keeps the editor focused, unlike launching a recognition Activity
with RecognizerIntent. Switching to another voice IME would delegate UI and results
but cannot guarantee a return to Titan, so it is not used. The installed default
recognition service is used, without a Google package condition.

RECORD_AUDIO runtime permission is requested through a small Activity. Granting
permission never starts recording: return to the input field and explicitly start
again. No automatic restart, background recorder or audio file is created. The
service may send audio externally; the panel explains this before start. Titan
adds no INTERNET permission, but that does not make the recognition provider offline.

Only final nonempty results are inserted, once. Partial results are ignored.
Cancellation invalidates late callbacks before destroying the recognizer. Input
restart/finish, hidden IME window, changed selection and physical key input cancel
recognition. Results also require the original input generation and connection,
a visible IME window and a non-direct field. No password recognition or automatic
composition finalization: commit Mozc text explicitly before starting. Errors and
missing services display guidance and require another explicit start.

## Autofill

API 30+ Android-provided remote InlineContentView objects can now be displayed in
password/direct fields. Key input still bypasses Mozc; clipboard capture, selection
transforms and voice remain disabled there. Titan does not extract candidate text,
credentials or remote-view internals. Only successfully inflated provider views
cause the otherwise-hidden direct-field input UI to appear. Empty responses and
editor changes invalidate old callbacks and clear the row.

The safe password menu contains host autofill request, provider settings, system
IME picker and Titan settings. It does not expose history, transforms or Undo/Redo.
If no inline candidate is supplied, Android's own app-side autofill UI and Titan's
settings route remain available; an empty password bar is not forced to appear.
performContextMenuAction(autofill) is an app-dependent request, not proof of a
provider result. Settings never selects a provider or opens a private vault API.

Device configuration was read only: Google Play services autofill and Google TTS
recognition are configured. Actual Google/Bitwarden inline filling is not established
by that fact or by successful compilation. No provider settings or stored credentials
are changed by the tests.

## Validation

- JVM mappings: 2 tests passed, including voicing, small kana, combining marks,
  emoji and unrelated symbol preservation. An initial expectation incorrectly
  assumed ヷ could not map; corrected to the valid ﾜﾞ mapping.
- First device editor suite: 14 passed, 34.529 s. Added selected-only replacement,
  absent selection, Back cancellation and composing guard. Existing stale-batch,
  repeated Space, normalization, WebView and visibility cases passed.
- Launcher handoff: 3 passed, 7.694 s (injected fixture, not new human Niagara trial).
- Voice result-token tests and all nine device transform cases added subsequently;
  final results recorded in device-validation.md.
- Human microphone trial: Japanese recognition and insertion confirmed by the
  user; English reported as broadly working, without an accuracy assessment.
  This does not independently verify cancellation, duplicate callbacks or typing
  after recognition. Provider-backed inline filling is left to the user's own
  check and remains unverified. Permission denial and service-unavailable UI also
  require separate device validation.

## Public API references

- [IME autofill integration](https://developer.android.com/identity/autofill/ime-autofill)
- [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent)

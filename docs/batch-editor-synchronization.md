# Batch snapshot synchronization investigation

## Actual ChatGPT input trace

The user entered nihongo and Space twice in the Android ChatGPT app without
sending a message. On the diagnostic build based on 70a0828, the user observed
characters finalizing during typing, before useful conversion was possible.
The opt-in Debug TitanState trace records no text or letter keycodes, only
revisions, lengths, selection/composing bounds, output flags and reset reasons.

Observed sequence on October 27:

1. Editor started with package com.openai.chatgpt, inputType 180225 (0x2c001),
   Japanese mode enabled.
2. Mozc returned consumed=true, result=false and a new preedit. It did not request
   a commit. Titan called beginBatchEdit and setComposingText.
3. A getExtractedText inside that same batch returned the previous editor state.
   Where old and new readings had equal lengths, the substring comparison failed.
4. synchronizeEditor called finishComposingText and reset Mozc **before endBatchEdit**.
   This was recorded at revisions 2, 4, 6 and 9.
5. onUpdateSelection then reported composing=-1,-1. Subsequent Space events saw
   an empty native preedit and passed through as ordinary spaces.

Thus the reproduced early commit was initiated by Titan's mismatch recovery,
not by Mozc committing on Space. This trial finalized during typing, so the
original reported second-Space-only variant was not independently reproduced.
It is covered separately by a delayed-snapshot regression that requires both
first and second Space to retain composition and the second to cycle candidates.

## Generic correction

Never infer external normalization from an in-progress batch snapshot. The
post-write inline synchronization was removed, and synchronizeEditor defensively
ignores calls during a write batch. Ordinary end-of-reading caret placement uses
setComposingText(..., 1) without an extra setSelection. Only a middle-reading
cursor is updated within the same batch, without reading a stale snapshot.
Moving that command after endBatchEdit failed the existing middle-insertion
regression (にほんかご became にほんごか): the intermediate end selection could
move Mozc's cursor. The final change keeps text and caret atomic. Selection callbacks and
subsequent user operations continue to validate actual host text outside a batch.

The host-normalization protection remains: synchronous/asynchronous replacement
fixtures preserve the host's authoritative text and do not resurrect stale reads.
No package-specific branch, new app preference, Mozc Space override, password
override or physical modifier routing change is introduced.

Android documents batching and deferred selection notifications in
[InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection#beginBatchEdit()).
The real trace, rather than an assumption about ChatGPT's UI framework, motivated
this change. Other delays beyond the observed batch boundary are not claimed fixed.

## Reproduction and evidence boundaries

InputTestActivity can retain its pre-batch ExtractedText until endBatchEdit. The
new regression failed before the fix with composing start -1 instead of 0 after
typing. It checks whole-reading composition, first-Space conversion, second-Space
cycling and explicit Enter commitment. Existing normalization and WebView tests
remain separate. Candidate ranking assertions account for native learning rather
than deleting history or requiring a fixed top candidate.

Enable diagnostics only for a deliberate Debug trial with
`adb shell setprop log.tag.TitanState DEBUG`, then disable with
`adb shell setprop log.tag.TitanState INFO`. Direct/password fields are excluded.
No message content, clipboard text or typed letter keycodes are recorded by this
tag. Local captures are in ignored test-results; summarize metadata in reports.
Actual physical trials and automated fixture results are recorded separately in
device-validation.md. Do not infer LINE, Discord or Termux physical verification
from an EditText fixture.

## October 29 validation in progress

- Debug build/install succeeded; Release and app/harness Lint succeeded.
- Full injected InputDeviceTest passed (105.188 s), including middle insertion.
- EditorCompatibilityTest: 9/10 passed (34.640 s); candidate visibility failed
  before the hide/refocus step and failed again in isolation. The saved screenshot
  shows composed ニホンゴ but no candidate strip. This remains an unresolved UI
  visibility issue, not a successful regression run.
- The delayed-batch/second-Space fixture passed in that suite.
- Physical ChatGPT follow-up: the user confirmed composition stayed active and
  Space continued converting even when pressed more than twice. The metadata
  trace also retained conversion state across the subsequent Space events.
- The missing-strip failure was investigated separately: dumpsys showed
  mIsInputViewShown=true while mWindowVisible=false and mInputViewStarted=false.
  refreshStrip had incorrectly treated the view flag as proof of window visibility.
  It now also checks the decor view before requesting display. All ten compatibility
  tests then passed (28.224 s); an initial install-time connection failure was
  recorded separately. No app-package exception was added.

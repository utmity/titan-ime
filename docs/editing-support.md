# Physical editing and input support

## Routing

The default ordinary-editing layout is Fn+H/L/K/J (left/right/up/down). Settings
can disable it or select Fn+A/D/W/S. During Mozc conversion, existing segment
bindings win; no navigation translation runs. Fn+U/O still shrinks/extends a
segment. Password and TYPE_NULL/direct fields retain application key handling,
including Termux Ctrl/Alt/Meta. No terminal package check was added.

Within reading composition, plain horizontal movement uses Mozc without commit.
Shift+Fn+horizontal extends a range while preserving its anchor using an available
full ExtractedText snapshot and ICU character boundaries (UTF-16 offsets). This
does not split surrogate pairs or combining sequences. If a usable snapshot is
unavailable, a paired arrow Down/Up is sent to the editor. Ordinary arrows and
multiline movement are delegated to the editor that owns line layout.

Ctrl+Fn+left/right requests word movement. Vertical/word movement finalizes a
pending composition before delegating. Optional Fn+Backspace requests Ctrl+Backspace
(default off). App support determines word deletion and word/vertical selection;
the IME does not approximate screen geometry or guarantee these operations in
every editor. Right Shift remapped to Ctrl remains Ctrl. Synthetic pairs strip
Fn and retain Ctrl/Shift; physical consumed-key lifetime accounting is unchanged.

## Clipboard

Settings → clipboard history → enable saving (off by default). New one-item text
copies observed while an ordinary editor is active are saved locally. No initial
clipboard scrape, URI coercion, background polling, network or broad storage
permission. Sensitive-flag data, direct/password fields, no-personalized-learning
fields, blank text and text longer than 10,000 UTF-16 units are excluded.
Unflagged secrets cannot be reliably detected; keep saving off for those copies.

Fn+V opens the panel; settings allow Fn+B or no shortcut. Fn+J/K or Down/Up selects,
Enter pastes; tapping an item also pastes. Typing searches literal clipboard text
(ASCII physical search is supported; this panel does not run another Mozc session).
Backspace edits the query and Esc closes. No caret is moved by opening/closing.
Pasting explicitly commits any pending composition once, then inserts the entry.

Pin toggle/deletion affect the selected item. Full deletion requires a second
confirmation and includes pins. Deduplication uses exact text; pinned entries
sort first. Total capacity includes pins (10/20/50 via settings); reducing the
limit trims to it. Disabling saving retains existing data; disabled history cannot
be pasted through the panel. JSON is stored in app-private SharedPreferences,
with application backup disabled. Android may withhold clipboard access; missed
copies are not reconstructed. Pins cannot exceed the total limit.

## Menu, Undo and autofill

The compact ordinary-input bar exposes `…` in English as well as Japanese mode.
The panel includes history, standard autofill request, system IME picker,
Undo/Redo and settings. It does not modify Android's app-owned selection menu.
Opening/closing preserves composition. Undo/Redo uses performContextMenuAction
once; a rejected action shows an unsupported notice, with no fallback replay.
Physical Ctrl+Z/Ctrl+Shift+Z remains application-owned. There is no IME undo stack.

API 30+ inline suggestions use Android's InlineSuggestionsRequest and remote
InlineContentView, with AndroidX Autofill 1.1.0 V1 styles. No suggestion content is
read into text, persisted or logged. Old asynchronous callbacks are invalidated
when the editor changes. Unsupported UI versions or absent suggestions are safe.
Inline suggestions are now also requested for password/direct fields while the
normal key path remains direct. Only remote provider views are rendered; no text
is extracted. See input-extensions.md for the current scope and validation.
The standard host autofill UI can still operate independently of Titan.

The menu forwards android.R.id.autofill to the host; support is app-dependent.
Settings can open Android's autofill service selection screen, without selecting
a provider. Neither route forces a password manager vault to open. Google/Bitwarden
provider interoperability and actual inline display remain **device-unverified**.
Do not describe them as working merely because the API compiles.

## API references and dependency

- [Clipboard restrictions](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data)
- [Sensitive clipboard data](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling)
- [InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection)
- [InlineSuggestion](https://developer.android.com/reference/android/view/inputmethod/InlineSuggestion)
- [AndroidX inline UI](https://developer.android.com/reference/androidx/autofill/inline/v1/InlineSuggestionUi)

AndroidX Autofill is Apache-2.0 licensed. The library supplies the public inline
style format; it is not a password manager and does not add network permissions.

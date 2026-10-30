# Physical modifiers and terminal compatibility

## Scope and routing

The observed built-in TitanKey device has vendor/product IDs 0x2533/0x2533.
The device's own KeyCharacterMap supplies Alt digits/symbols; Titan IME does not
copy a letter-to-number table onto other keyboards. The default Alt text setting
commits those characters through InputConnection, including in direct fields.
Japanese punctuation in ordinary text fields still goes through Mozc.

The setting “Altをアプリへ渡す（Meta）” preserves original Alt shortcuts.
Ctrl/Meta, Ctrl+Alt, unmapped Alt keys and external keyboards bypass this Alt text
policy. Alt+Space remains available for the configured language toggle.
Modifiers alone do not commit a reading. App shortcuts finish an active reading
and pass the original Down/Up events. A selected reading is finished in place so
copy/cut can retain the selected editor range. Consumed character keys retain the
existing matching-Up suppression; no synthetic early Up is generated for shortcuts.
Up suppression is tracked for the complete key press, including auto-repeat.
If any Down reached the app, Up must reach it even if a modifier change caused
another Down in the same press to be consumed by Mozc. A new initial Down resets
this record; key release and input lifecycle transitions clear it.

## Findings on Titan 2 Elite

The user maps right Shift to Ctrl. Physical capture shows KEYCODE_CTRL_LEFT (113),
scan 54, meta 12288. No remapping is needed inside the IME. An earlier trial used
Fn accidentally and is explicitly excluded from Ctrl evidence.

Before this fix, Japanese-mode app shortcut forwarding sent Down and an immediate
synthetic Up, consumed the original Up, and repeated this for held modifiers.
The physical Ctrl capture reproduced that premature release. The new regression
checks that Ctrl and R receive no Up until release, then exactly one Down/Up pair.

The installed Termux is 0.118.3. Its TerminalView strips left-Alt when looking up
Unicode and passes left-Alt as Meta. Thus TitanKey Alt+R (KCM value `3`) becomes
Meta+r instead of a literal digit. A Bash revert-line binding can explain line
loss, but the actual shell binding must be checked separately.

Sources:
- [Termux TerminalView 0.118.3](https://github.com/termux/termux-app/blob/v0.118.3/terminal-view/src/main/java/com/termux/view/TerminalView.java)
- [Termux shortcut handling 0.118.3](https://github.com/termux/termux-app/blob/v0.118.3/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java)

Ctrl+C in a terminal normally interrupts, Ctrl+A moves to the start of a line,
and Ctrl+V quotes the next character; these are not EditText clipboard commands.
Termux provides Ctrl+Alt+V for paste. No package-name-specific routing is used.

## Limits and physical verification procedure

Termux's TYPE_NULL terminal remains a direct-input field. Japanese composing,
predictions and segment editing are not enabled in this field. Password fields
continue to bypass Mozc. This change addresses physical character/modifier routing,
not full terminal Japanese composition support.

Use a new disposable Termux session, never a terminal running the user's work.
In Alt text mode, type an unfinished harmless line, then physical Alt+R: it should
append `3` without reverting the line. Check actual remapped Ctrl+A and Ctrl+C.
In Meta mode, test an intentional terminal Meta binding. Restore Alt text mode.
Record physical results separately from injected events and native KCM assertions.
ChatGPT-specific behavior and other editors require their own checks.

Physical outcome (October 26 trial, final inspection October 27): Alt+R appended
`3` to `echo TITANALT`, and execution printed `TITANALT3`. The actual remapped
right Shift+ C cancelled `abc`, showing `abc^C` and a new prompt. After selecting
Meta mode in Settings, physical Alt+B moved from the end of `echo METATEST` to
the first `M`, without inserting the Alt-layer `!`. Alt text mode was restored
after inspection and verified checked in Settings. These observations confirm
these specific physical operations, not every Ctrl/Alt combination or clipboard
operation in other apps. The original Alt+R revert-line binding itself was not
queried; its precise shell action remains an inference from upstream routing.

The October 27 injected EditText regression additionally verifies Ctrl+C/X/V on a
selected reading, full-reading Ctrl+A/X/V, further Japanese input after paste,
and English Ctrl+A/C/V. It restores the previous clipboard in finally. This does
not establish clipboard compatibility with ChatGPT or all terminal applications.

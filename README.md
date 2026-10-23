# Titan IME

Physical-keyboard-first Android IME for Unihertz Titan 2 Elite.

## Goals
- Keep English key input on a minimal direct-commit path.
- Add Japanese romaji composition without switching Android IMEs.
- Preserve hardware Sym/Fn handling and allow configurable language toggle.
- Integrate a Japanese conversion engine later behind a non-blocking interface.
- Keep a distinct application ID so existing IMEs remain installed.

## Development stages
1. Independent Android InputMethodService and testable Kotlin romaji composer.
2. Composition editing, cursor, cancellation, hardware key routing and performance tests.
3. Mozc integration, candidate UI, dictionary and learning.

## Reference
PhysiBoard (https://github.com/brobata/physiboard) is a GPL-3.0 clean-room rewrite with pure Kotlin core modules. Any reuse of its source requires license compliance; this project currently contains no PhysiBoard code.

Status: initial scaffold; not yet an installable APK.

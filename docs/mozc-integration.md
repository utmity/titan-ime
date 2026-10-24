# Mozc integration and development priorities

## Decision (2026-10-24)

The [reference article](https://note.com/urabe_fujinawa/n/n4a8c92ebd452) describes a private physical-keyboard IME using an on-device Mozc engine, built on Linux under WSL2. Its application is not published for reuse. Titan adopts the engine/frontend separation, not the article's application or shortcut mappings. English completion, JMdict and custom fonts are not needed for basic kana-kanji conversion and are not imported.

Mozc owns romaji interpretation, preedit, segmentation, dictionary conversion and candidate state. Titan owns Android InputConnection updates, the candidate strip, language switching and physical modifier routing. The previous production RomajiComposer is removed; its relevant regression cases move to the real-device harness. The upstream romaji table is retained with five explicit nna/nni/nnu/nne/nno rules to preserve Titan's doubled-n spelling. This is Mozc configuration, not a second converter.

Priority order: (1) reliable kana → Space conversion → candidate selection → Enter commitment; (2) editor lifecycle and physical-key regression coverage; (3) segment navigation and conversion quality; (4) learning/user dictionary; (5) settings and additional shortcuts.

## Upstream and build conditions

- Pinned Mozc revision: `c4b2aef7c65e61be394bacadaec200eb5b97f91c`.
- [Official Android instructions](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/docs/build_mozc_for_android.md): Linux/macOS, Python 3.12+, C++ compiler, Bazelisk; update_deps downloads Android NDK r29. The old Android UI is removed, but JNI remains.
- `scripts/build-mozc.sh` pins Bazel 9.0.2, builds the arm64 JNI library with `oss_android`, and generates the portable dictionary separately with Linux host tools (`oss_linux`). Attempting to generate the dictionary with the Android configuration failed on incompatible host code-generation targets; separating these builds succeeded.
- WSL is absent on this Windows PC. GitHub Actions provides Linux compilation without changing Windows features or requiring a reboot. The same script can run in an existing Linux/WSL2 environment.
- Device reports only `arm64-v8a`. Initial scope is this ABI, not all Android devices.
- Windows packaging uses the existing JDK 17, Gradle 8.9, AGP 8.7.3 and SDK 35. Protobuf compiler/runtime are 4.34.1, matching native Protobuf 34.1.

Build on Linux: `bash scripts/build-mozc.sh`. Download the `mozc-arm64` artifact from a successful trusted run of this repository, extract it, then run `./scripts/import-mozc.ps1 -Source <extracted-directory>` on Windows. The importer checks the source revision and both file hashes before copying into app sources. Files are ignored by Git and can be rebuilt; APK builds fail if either is missing. CI builds/caches and imports them before Gradle.

Initial successful engine build: [Actions run 40051850529](https://github.com/utmity/titan-ime/actions/runs/40051850529), source branch commit `0805aa5`. Library: 16,101,960 bytes (unstripped); dictionary: 19,001,738 bytes. These sizes are this build's measurements, not the article's figures.

## OSS notices

Full notices ship in APK `assets/licenses/`; copied upstream proto definitions preserve their copyright headers.

| Component | License / notice | Use |
| --- | --- | --- |
| [Mozc](https://github.com/google/mozc/blob/c4b2aef7c65e61be394bacadaec200eb5b97f91c/LICENSE) | BSD 3-Clause; retains separate dictionary notices | JNI engine, protocol, romaji table, OSS dictionary |
| IPAdic / NAIST | Included in Mozc LICENSE | Dictionary source, attribution and disclaimer retained |
| Okinawa dictionary | Public-domain dedication in Mozc LICENSE | Dictionary source |
| [Japanese Usage Dictionary](https://github.com/hiroyuki-komatsu/japanese-usage-dictionary/blob/2025-01-25/LICENSE) | BSD 2-Clause | Usage data in generated dictionary |
| [Protocol Buffers](https://github.com/protocolbuffers/protobuf/blob/v34.1/LICENSE) | BSD 3-Clause | Native/Java message serialization |
| [Abseil](https://github.com/abseil/abseil-cpp/blob/20260107.1/LICENSE) | Apache 2.0 | Native engine dependency |
| utf8_range | MIT, included under Protobuf's third_party | Native Protobuf dependency |
| Tamachi Phonetic Kanji Alphabet | MIT notice from Mozc credits | Dictionary accessibility descriptions |

This is the OSS dictionary, not Google's proprietary Japanese Input dictionary. JNI symbols retain upstream's package name for compatibility; Titan's application ID and branding remain separate. No INTERNET permission is added. Initial conversion is incognito (learning disabled); password/non-text fields bypass Japanese handling.

## Initial interaction

- Ctrl+Shift+J: existing EN/JA toggle; Android retains Ctrl+Space.
- Roman letters: Mozc composing kana. Pending Latin letters retain half width.
- Space with preedit: convert / advance candidate. Candidate strip can also select by touch.
- The candidate strip uses a compact Android input view (no software keyboard keys). The candidates-only window was clipped on this device; reserving input-view space makes candidates visible and touchable above navigation controls. It remains visible in Japanese mode even with empty preedit.
- Enter with preedit: commit only. A subsequent Enter is passed to the editor.
- Escape: Mozc cancel/back-to-reading behavior. Backspace: Mozc editing behavior.
- Sym, standard function modifier and the observed Titan Fn pair (403/251): preserved bypass routing. Pending composition is committed before the modified event, with ordering on the same InputConnection.
- Plain punctuation/digits retain Titan's direct half-width behavior after composition commitment.

Injected device tests and actual physical-key observations must be reported separately. The earlier physical mapping evidence remains valid evidence about the hardware; it is not a physical test of this new Mozc frontend. Full segment cursor management, lost key-up recovery without lifecycle change, other editors, and startup latency remain follow-up work.

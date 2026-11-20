# ArchIME v1 — clean rewrite

This branch is the replacement implementation track. It does not treat the previous 0.7.2 keyboard shell as the product baseline.

## Product principles

1. **Input correctness before appearance.** A keyboard must never display a control that cannot perform its stated action.
2. **Fail-open input path.** If Rime is unavailable, letters, digits, punctuation, backspace, space, and enter must still work as a predictable English keyboard. Initialization must never block the IME main thread.
3. **One owner for native state.** Every Rime call, including initialization and destruction, is serialized on one worker. InputConnection and all Android views remain on the main thread.
4. **Secure-field handling.** Password and secure numeric fields bypass Chinese composition and do not retain composing text.
5. **Explicit feedback settings.** Sound and haptics are opt-in/configurable, persisted, and previewable. Do not copy WeChat's proprietary assets.
6. **Honest capability surface.** No voice, handwriting, translation, or cloud features are shown until their full interaction works.

## Visual and interaction contract

- Charcoal keyboard surface, dark-gray rounded keys, white labels, Arch blue as the only accent.
- The Arch Linux mark is permanently anchored at the top-left of the keyboard header, including candidate and utility panels.
- Header: Arch mark, compact status, settings/tools, dismiss.
- Candidate strip: horizontally scrollable candidates, page navigation, clear composing state.
- Main layout: QWERTY 26-key grid; digit hints above letters, Microsoft Shuangpin hints below letters; dedicated shift and backspace.
- Bottom row: symbols/numbers, comma, space, Chinese/English toggle, enter.
- Tool panels are real panels with a close/back path; do not use placeholder buttons.

## Delivery phases

### Phase A — reliable core
- Rebuild the Android IME service and setup/settings activity as clean Kotlin source.
- Keep the current Rime native bridge and Microsoft Shuangpin schema only as an isolated engine adapter/data source.
- Add deterministic English fallback and input lifecycle tests/checklist.
- Verify APK package contents and Android build in CI.

### Phase B — feedback and personalization
- Original short key-click sound with a preview and volume control.
- Haptics: off / follow system / custom; strength control with device capability fallback.
- Keyboard height, one-handed mode, candidate count, auto-capitalization and double-space period.
- Persist all settings and apply changes on the next keyboard render.

### Phase C — useful tools
- Clipboard history (local-only, user-controlled), snippets and emoji panel.
- Voice/handwriting/translation only after an actual provider/recognizer implementation is integrated and tested.

### Phase D — Linux
- Separate Fcitx5/Rime packaging and configuration. Android UI code is not reused as Linux frontend code.

## Release gates

A release APK is not called usable until:
- CI compiles and packages it successfully.
- APK contains ARM64 native engine and the Microsoft Shuangpin schema/dictionaries.
- Fresh install can enable and select the IME.
- Typing, candidate selection, backspace repeat, punctuation, mode toggle and editor action work.
- Switching apps/editors repeatedly does not freeze or retain stale composing text.
- Password fields do not invoke Chinese composition.
- Sound/haptic settings persist and preview correctly.
- Redmi K80 / Android 16 device test is recorded separately from CI results.

## Current status

This document defines the v1 rewrite contract. It is not a claim that the above features have already shipped. The old APK is a diagnostic build only and is not the v1 release candidate.
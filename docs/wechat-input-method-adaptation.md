# ArchIME reliability and input-method preferences

## Immediate stability fix

- Install Rime assets and run first-use schema/dictionary deployment on a dedicated serial worker, never on the IME or Activity main thread.
- Serialize all JNI engine operations on that same worker.
- Keep keyboard rendering and Android InputConnection interactions on the main thread.
- If native initialization fails, retain a usable English fallback and show a visible status instead of blocking the keyboard.
- Do not publish a new APK based on compilation success alone; verify APK contents and test keyboard open/close, first-run initialization, app launch, and repeated input switching on a physical device.

## WeChat Input Method-inspired preferences

The settings screen should remain compact and grouped by task:

### Feedback
- Key sound: Off / System / Soft click / Wooden mahjong tap / Mechanical.
- Sound volume slider and a preview button.
- Haptics: Off / Follow system / Adjustable.
- Haptic strength slider when the device supports amplitude control.
- Restore defaults.

Use original ArchIME sound assets, not extracted WeChat audio. Default to Follow system for haptics and Off for custom audio until the user chooses a profile. Never force a fixed vibration pulse on every key.

### Keyboard management
- Microsoft Shuangpin full keyboard as the default.
- Future layouts: full Pinyin, 9-key, handwriting. Only show a mode once its input engine is implemented and tested.
- Keyboard height/vertical position adjustment with live preview and reset.
- One-handed mode and optional floating mode.
- Number/symbol hint and swipe-number preferences.

### Typing preferences
- Double-space period.
- Automatic capitalization.
- Smart spaces where appropriate.
- Optional fuzzy Pinyin matching; do not alter the Microsoft Shuangpin key map.
- Candidate count and candidate text size.

### Shortcuts
- Clipboard, common phrases, emoji and voice dictation.
- Clearly mark unavailable tools as disabled; do not present placeholder toasts as working functions.

## Visual identity

- The Arch Linux logo is fixed at the keyboard's top-left in every keyboard/panel state.
- Borrow the uncluttered spacing and simple settings hierarchy, not WeChat logos, brand colors, or proprietary sound samples.
- Keep dark/light appearance and Arch blue as the only accent color.

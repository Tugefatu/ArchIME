# Keyboard feedback redesign

## Restored baseline

The keyboard UI has been rolled back to the pre-regression source snapshot. The Arch Linux logo resource (`ic_arch_logo`) remains the fixed top-left brand mark. The earlier acrylic QWERTY layout and Microsoft Shuangpin/Rime integration are retained.

## Feedback requirements for the next iteration

- Keep Android's native `KEYBOARD_TAP` haptic feedback; do not add fixed-duration vibration pulses.
- Keep Mahjong audio disabled by default until a suitable, user-approved tile-contact sample is selected and packaged.
- Do not synthesize a generic high-pitched click and label it Mahjong audio.
- Add independent settings for haptics and audio only after both are tested on a physical Android device.
- Preserve the Arch Linux logo in the keyboard's top-left position in every keyboard and panel state.

## Validation gate

Build success alone is not sufficient. Check the APK contents and test the keyboard on-device for logo placement, Microsoft Shuangpin composition/candidates, tactile feel, and audio before publishing a replacement APK.

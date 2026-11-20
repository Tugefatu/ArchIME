# Runtime validation checklist

Before publishing any APK from this pull request:

- [ ] Build completes and APK verifier confirms ARM64 native bridge and Microsoft Shuangpin schema assets.
- [ ] First launch on a clean install displays the keyboard without blocking while Rime assets are extracted and deployed.
- [ ] First-run Rime initialization failure leaves the keyboard responsive and reports a recoverable status.
- [ ] Repeatedly switch between ArchIME and the system keyboard without a frozen IME window.
- [ ] Open the ArchIME setup Activity repeatedly; confirm it never remains blank.
- [ ] Type, select candidates, backspace, press space, and switch editor fields.
- [ ] Confirm Arch Linux mark stays at the top-left of all keyboard panels.
- [ ] Verify feedback settings only after sound/haptics controls are implemented and tested on a physical device.

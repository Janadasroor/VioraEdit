# VioraEdit TODO

## Immediate Tasks
- [ ] Run unit tests (`./gradlew testDebugUnitTest`) to verify business logic.
- [ ] manual verification of the Video Editor UI on a device/emulator.

## Features to Complete
- [ ] **VideoEditorScreen**: Refactor large file (758 lines) into smaller sub-components.
- [ ] **AudioEditorPanel**: Implement real audio mixing logic (currently has placeholders).
- [ ] **StickerPickerPanel**: loading real images from gallery (currently uses only emojis/dummy data).

## Technical Debt / Cleanup
- [ ] Address remaining lint warnings (low priority).
- [ ] Add Dependency Injection for `VideoEditorViewModel` (Factory is currently hardcoded in `IntegratedPostCreation`).

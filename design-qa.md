# Selection mode visual QA

source visual truth path: `C:/Users/CAOGEN~1/AppData/Local/Temp/codex-clipboard-9b56a518-5c81-4f52-8de5-d5a0fd15bc40.png`
implementation screenshot path: unavailable (no Android emulator/device is connected)
viewport: source image 852 x 1838 px; implementation viewport unavailable
state: Android floating-ball long-press selection mode

## Comparison evidence

The source screenshot was opened and used as the visual target. A matching rendered Android screen could not be captured: no emulator process is available, and `adb` cannot create its `.android` directory in the current sandbox. The Gradle verification run also reached Kotlin compilation but could not be repeated after the final visibility fix because the environment usage limit rejected the required elevated cache access.

## Findings

- [P1] Runtime visual comparison blocked. The source and implementation cannot be placed in the same comparison input until an Android screen capture is available.

## Implementation checklist

- [x] Selection border uses a brighter blue core plus soft blue glow.
- [x] Top and bottom handles use compact 32dp x 14dp geometry.
- [x] Capture control uses the existing glass-ball asset and capture icon.
- [x] Capture control bounds are derived from the floating ball's real screen anchor and diameter.
- [x] Selection exit crossfades the overlay out while revealing the ball at the same anchor.
- [ ] Capture and compare a real Android selection-mode screenshot.

## Required fidelity surfaces

- Fonts and typography: no new text is introduced in the selection control.
- Spacing and layout rhythm: handle geometry and capture anchor are covered by unit tests.
- Colors and visual tokens: border and control colors are defined in `CaptureSelectionWindow`.
- Image quality and asset fidelity: existing `bg_ball_glass` and `ic_menu_capture` resources are reused.
- Copy and content: unchanged.

final result: blocked

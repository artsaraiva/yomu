# Yomu Phase 1 Design Spec

**Date:** 2026-01-11  
**Status:** Approved  
**Scope:** Phase 1 MVP — Japanese → English, Single-Page, Android, Local-Only

---

## 1. Overview

Yomu is a local-first AI-powered manga translation application that enables users to read manga in their native language directly on their mobile device. Unlike traditional OCR-based translators, Yomu understands panel structure, conversation flow, character interactions, and speech bubble context before generating translations.

**Core Philosophy:** Local when possible. Cloud when needed.

### Phase 1 Vision

Deliver a functional MVP that translates Japanese manga to English on Android devices using on-device AI. The app operates as a system-wide overlay that works on top of any manga reading app or browser, providing context-aware translations without requiring users to leave their preferred reading environment.

---

## 2. High-Level Architecture

The application consists of two main components:

### 2.1 Main App (Activity)

A Jetpack Compose-based Android app that provides:

- **Model Management:** Download, select, and manage AI models (vision models + LLM)
- **Settings:** Configure translation mode (local/cloud/hybrid), target language, overlay behavior
- **Credits:** View balance, purchase cloud credits, redeem codes (UI only in Phase 1, backend deferred)
- **History:** Browse past translations stored locally
- **Service Control:** Start/stop the overlay service

**Tech Stack:**
- Jetpack Compose + Material 3
- Compose Navigation
- Hilt / Dagger for dependency injection
- Room (SQLite) for local storage
- Retrofit + OkHttp for network requests (model downloads)
- Coil for image loading

### 2.2 Overlay Service (Foreground Service)

A background service that provides:

- **Floating Action Button (FAB):** Always-visible draggable button with three states:
  - **Idle:** Standard appearance, tap to translate current screen
  - **Manga Detected:** Pulses/glows when manga patterns are detected on screen
  - **Translating:** Shows spinner while pipeline runs
- **Manga Auto-Detection:** Lightweight periodic screen analysis (every 2-3 seconds on screen changes) using AccessibilityService and URL pattern matching for known manga sites
- **Screenshot Capture:** MediaProjection API to capture current screen
- **Translation Pipeline:** Executes the 6-stage translation pipeline
- **Transparent Overlay:** Renders translated text on a WindowManager overlay positioned precisely over original bubbles

**Key Android APIs:**
- `WindowManager` (TYPE_APPLICATION_OVERLAY) for floating button and translation overlay
- `MediaProjection` for screenshot capture
- `Foreground Service` for background operation
- `AccessibilityService` (optional) for screen analysis
- Permissions: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`

**Minimum SDK:** API 26 (Android 8.0)

---

## 3. Translation Pipeline

The pipeline consists of 6 stages that process a screenshot into translated overlay text:

### 3.1 Screenshot Capture

- **API:** MediaProjection
- **Output:** Bitmap of current screen
- **Speed:** ~50ms
- **Notes:** Requires user consent on first use. Session persists until revoked.

### 3.2 Bubble Detection

- **Model:** YOLOv11 Nano (ONNX format)
- **Runtime:** ONNX Runtime Mobile
- **Size:** ~6 MB
- **Speed:** ~50ms on mid-range phone
- **Output:** List of bubbles with bounding boxes `[x, y, w, h]` and confidence scores
- **Fallback:** Pre-trained manga bubble detector if YOLOv11 Nano unavailable

### 3.3 OCR (Optical Character Recognition)

- **Model:** MangaOCR (ONNX format)
- **Runtime:** ONNX Runtime Mobile
- **Size:** ~150 MB
- **Speed:** ~30ms per bubble
- **Output:** Extracted Japanese text per bubble with confidence scores
- **Capabilities:** Handles stylized manga fonts, vertical text, and sound effects

### 3.4 Context Assembly

- **Method:** Spatial clustering + reading order heuristics (pure logic, no ML)
- **Speed:** ~5ms
- **Algorithm:**
  1. Group bubbles by proximity (panel detection)
  2. Sort top-to-bottom, right-to-left (manga reading order)
  3. Build conversation blocks for the LLM
- **Output:** Structured conversation blocks ready for translation

### 3.5 Translation

- **Model:** Qwen 3 1.7B (4-bit quantized GGUF)
- **Runtime:** llama.cpp via JNI (C++)
- **Size:** ~1 GB
- **Speed:** ~8-15 tokens/second (~1-3 seconds per page)
- **Context Window:** ~2K tokens
- **Prompt Strategy:**
  ```
  System: "You are a manga translator. Translate the following conversation naturally, preserving tone and context."
  User: "Translate this conversation:
  [1] 日本語テキスト...
  [2] 日本語テキスト..."
  ```
- **Output:** Structured JSON with translated text per bubble

### 3.6 Typesetting & Overlay Rendering

- **Typesetting Logic:**
  - Dynamic font sizing based on bubble dimensions
  - Text wrapping to fit bubble shape
  - Multi-line balancing (avoid orphan words)
  - Semi-transparent background behind text for readability
  - Emphasis detection (bold, italic, ALL CAPS)
- **Overlay Rendering:**
  - Transparent WindowManager overlay
  - Positioned at exact bubble coordinates from detection stage
  - Canvas-based text drawing
  - Tap-to-dismiss interaction
  - Fade-in animation on render

**Total Pipeline Latency:** ~2-4 seconds per page (target: <3s)

**Note:** Text removal (inpainting) is deferred to Phase 2. Phase 1 renders translated text directly over the original with a semi-transparent background.

---

## 4. Model Management & Download Flow

### 4.1 First Launch Flow

1. User opens Yomu for the first time
2. Onboarding screen explains what Yomu does
3. Permission requests: overlay (SYSTEM_ALERT_WINDOW), MediaProjection consent, storage
4. Model download screen shows required models with sizes, warns about total download (~1.16 GB), requires Wi-Fi
5. Download with progress: per-model progress bars, resumable downloads, background-safe
6. Ready to translate: overlay service starts, floating button appears

### 4.2 Model Download Screen

Displays three models:
- **Vision Models (Required):** Bubble detection + OCR, ~160 MB
- **Qwen 3 1.7B 4-bit (Required):** Translation engine, ~1 GB
- **Qwen 3 1.7B 8-bit (Optional):** Higher quality translation, ~2 GB

Warning banner: "Total required: ~1.16 GB. We recommend downloading over Wi-Fi."

### 4.3 Storage Location

- **Path:** App-specific internal storage (not visible to user)
- **Format:** GGUF (LLM), ONNX (vision models)
- **Cleanup:** User can delete models from settings
- **Metadata:** Room DB tracks model version, path, size, status

### 4.4 Model Lifecycle States

- **Available:** Listed but not downloaded
- **Downloading:** Progress tracked, resumable
- **Ready:** Downloaded, verified (SHA-256 checksum), loadable
- **Error:** Download failed, retry option available
- **Update Available:** Newer version on server

Versioned model registry. Server hosts model manifests with SHA-256 checksums for integrity verification.

---

## 5. Main App UI & Overlay UX

### 5.1 Main App Tab Structure

Bottom navigation bar with 5 tabs:

1. **Home:** Service status (running/stopped), quick toggle (start/stop overlay), current mode (local/hybrid/cloud), today's stats (pages translated, time saved), active model status
2. **Models:** Download, select, manage AI models
3. **Credits:** Balance, purchase, redeem codes (UI only in Phase 1)
4. **History:** Past translations, re-translate option
5. **Settings:** Translation mode, target/source language, overlay behavior (auto-detect on/off, button position), font preferences, theme (light/dark)

### 5.2 Overlay UX — Floating Button States

**Idle State:**
- Always visible, draggable
- Orange circular button with "読" character
- Tap to translate current screen

**Manga Detected State:**
- Button pulses/glows with outer ring animation
- Indicates "Hey, I can translate this"
- User taps to trigger translation

**Translating State:**
- Button turns green with spinner
- Shows pipeline progress
- Overlay appears when complete

### 5.3 Manga Auto-Detection (Lightweight)

- **Method:** Periodic lightweight screen analysis (every 2-3 seconds when screen changes)
- **Implementation:** AccessibilityService reads current app + URL pattern matching for known manga sites. Optionally: quick screenshot → run bubble detection only (skip OCR/translation)
- **Battery Impact:** Minimal — only triggers on screen changes, not continuous polling
- **Known Manga Sites:** Configurable list (user can add sites). URL patterns like "manga", "chapter", "read" trigger detection

---

## 6. Tech Stack & Project Structure

### 6.1 Tech Stack

**App Layer:**
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Navigation: Compose Navigation
- DI: Hilt / Dagger
- Async: Kotlin Coroutines + Flow
- DB: Room (SQLite)
- Networking: Retrofit + OkHttp
- Image Loading: Coil

**ML Layer:**
- LLM: llama.cpp via JNI (C++)
- Vision: ONNX Runtime Mobile
- Build: Android NDK (CMake)
- Model Format: GGUF + ONNX
- GPU Acceleration: OpenCL (future optimization)

**Android System APIs:**
- Overlay: WindowManager (TYPE_APPLICATION_OVERLAY)
- Screenshot: MediaProjection API
- Background: Foreground Service
- Detection: AccessibilityService (optional)
- Permissions: SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION
- Min SDK: API 26 (Android 8.0)

### 6.2 Project Structure

```
yomu/
├── app/                          # Main application module
│   ├── ui/                       # Compose screens + viewmodels
│   │   ├── home/
│   │   ├── models/
│   │   ├── credits/
│   │   ├── history/
│   │   └── settings/
│   ├── service/                  # Overlay foreground service
│   ├── overlay/                  # Floating button + translation overlay views
│   ├── capture/                  # MediaProjection screenshot capture
│   ├── detection/                # Manga auto-detection logic
│   ├── db/                       # Room database (models, history, settings)
│   └── di/                       # Hilt dependency injection modules
├── pipeline/                     # Translation pipeline module
│   ├── bubble/                   # Bubble detection (ONNX)
│   ├── ocr/                      # OCR engine (ONNX)
│   ├── context/                  # Context assembly
│   ├── translation/              # Translation engine (llama.cpp)
│   └── typesetting/              # Text rendering logic
├── ml/                           # Native ML bindings
│   ├── llama/                    # llama.cpp JNI bridge (C++)
│   └── onnx/                     # ONNX Runtime wrapper
└── core/                         # Shared utilities, extensions, constants
```

Multi-module Gradle project. Pipeline and ML as separate modules for testability and isolation.

---

## 7. Error Handling & Edge Cases

### 7.1 Pipeline Failure Modes

**No Bubbles Detected:**
- Trigger: Screenshot has no manga panels
- Action: Show toast "No manga detected"
- Recovery: User can retry or dismiss

**OCR Fails:**
- Trigger: Text too stylized or low quality
- Action: Skip bubble, show "[unreadable]" placeholder
- Recovery: Translate remaining bubbles normally

**LLM Timeout / OOM:**
- Trigger: Model runs out of memory or takes too long
- Action: Kill inference, show error overlay
- Recovery: Offer cloud fallback if credits available, or retry with smaller context

**Permission Denied:**
- Trigger: User revokes overlay or screenshot permission
- Action: Stop service, show notification
- Recovery: Guide user to re-enable in Settings

**Model Not Downloaded:**
- Trigger: User tries to translate without models
- Action: Block translation, show download prompt
- Recovery: Redirect to Models tab

**Screenshot Capture Fails:**
- Trigger: MediaProjection session expired or denied
- Action: Re-request permission
- Recovery: Auto-restart capture session on next tap

### 7.2 Edge Cases

**Vertical Text (Japanese):**
- MangaOCR handles vertical text natively
- Typesetting renders horizontally (English target)
- Future: vertical text support for other target languages

**Sound Effects (SFX):**
- Phase 1: Detect but don't translate SFX (too stylized)
- Phase 2: Add SFX translation with artistic rendering

**Multiple Pages / Scrolling:**
- Phase 1: Single screen only. User scrolls manually, taps button again for next screen
- Phase 3: Batch/chapter mode

**Low-End Devices:**
- Detect RAM < 4GB on launch
- Warn user that local mode may be slow
- Suggest cloud mode or hybrid
- Show estimated performance before download

**Thermal Throttling:**
- Monitor device temperature
- If thermal throttling detected, reduce inference speed or suggest cloud fallback
- Show warning if device gets too hot

**App Killed by OS:**
- Foreground service with persistent notification
- If killed, auto-restart on next app open
- Save translation state to Room DB for recovery

---

## 8. Phase 1 Scope

### 8.1 What's In (Phase 1)

**Core Features:**
- Japanese → English translation
- Single-page (single screen) translation
- System-wide floating overlay
- Hybrid trigger (manual button + auto-detect)
- Full pipeline (bubble → OCR → context → translate → typeset)
- Local-only mode (on-device inference)
- Transparent overlay rendering
- Model download & management

**App Features:**
- Main app with 5 tabs (Home, Models, Credits, History, Settings)
- Background foreground service
- MediaProjection screenshot capture
- Translation history (local storage)
- Settings (mode, language, overlay position)
- Error handling & graceful degradation
- Android 8.0+ (API 26)

### 8.2 What's Deferred

**Not in Phase 1:**
- Multi-language support (Phase 2)
- Text removal / inpainting (Phase 2)
- Chapter / batch translation (Phase 3)
- PDF / CBZ support (Phase 3)
- Library management (Phase 3)
- Camera / live translation (Phase 4)
- Community features (Phase 5)

**Also Deferred:**
- iOS app (future phase)
- Cloud credit system backend (Phase 2+)
- Sound effects translation (Phase 2)
- Vertical text rendering (Phase 2)
- Manga-styled UI theme (polish pass after core features)
- GPU / NPU acceleration (optimization pass)
- Fine-tuned manga model (V2 model)

---

## 9. Success Criteria

### 9.1 Functional Requirements

- User can download models and start overlay service
- User can tap button to translate manga on any app
- Translations appear as overlay on original bubbles
- Translation quality is readable and context-aware
- App handles errors gracefully without crashing

### 9.2 Performance Requirements

- Translation latency < 5 seconds/page (target: 3s)
- OCR accuracy > 90% on clean manga
- App runs on mid-range phones (6GB RAM, Snapdragon 600+)
- Battery drain acceptable (< 10% per hour of use)
- No crashes on permission revocation or OOM

---

## 10. Future Considerations

### Phase 2: Multi-Language & Inpainting
- Add Portuguese, Spanish, French, German as target languages
- Implement text removal (inpainting) to erase original Japanese text
- Add sound effects translation
- Vertical text rendering for non-English targets

### Phase 3: Chapter Translation
- PDF and CBZ file support
- Batch translation of entire chapters
- Library management system

### Phase 4: Live Translation Mode
- Camera translation for physical manga
- Real-time overlays
- Instant manga reading

### Phase 5: Community Features
- Shared translations
- Translation corrections
- Community dictionaries
- Character-specific translation memory

---

## 11. Visual Design Notes

**Current Phase:** Functional UI with Material 3 theming (light/dark modes)

**Future Polish Pass:** Manga-styled UI with:
- Papery textures and backgrounds
- Cherry blossom motifs
- Japanese aesthetic elements
- Themed around the app name "Yomu" (読む = to read)

This visual polish will be applied after core features are working and stable.

---

## 12. Technical Risks & Mitigations

### Risk 1: LLM Performance on Low-End Devices
- **Mitigation:** Detect device capabilities on launch, warn users, suggest cloud mode
- **Fallback:** Offer 8-bit quantized model as optional upgrade for better devices

### Risk 2: MediaProjection Permission Complexity
- **Mitigation:** Clear onboarding flow, persistent notification, auto-restart on failure
- **Fallback:** Guide user through permission re-enablement

### Risk 3: Bubble Detection Accuracy on Varied Manga Styles
- **Mitigation:** Use YOLOv11 Nano trained on diverse manga dataset
- **Fallback:** Allow user to manually select bubbles if detection fails (Phase 2)

### Risk 4: Translation Quality for Slang/Cultural References
- **Mitigation:** Context-aware translation using conversation blocks
- **Fallback:** Fine-tuned manga model in V2, community corrections in Phase 5

### Risk 5: Battery Drain from Continuous Overlay
- **Mitigation:** Lightweight detection (no continuous polling), pause on screen off
- **Fallback:** User can disable auto-detect, use manual-only mode

---

## 13. Testing Strategy

### Unit Tests
- Pipeline components (bubble detection, OCR, context assembly, translation, typesetting)
- Model download and lifecycle management
- Settings and preferences logic

### Integration Tests
- End-to-end pipeline with sample manga pages
- Overlay service lifecycle (start, stop, permission changes)
- MediaProjection capture and overlay rendering

### Manual Testing
- Test on 3-5 different Android devices (low-end, mid-range, high-end)
- Test with 10+ different manga sites/apps
- Test with varied manga styles (shonen, shojo, seinen, josei)
- Test edge cases (vertical text, SFX, complex layouts)

### Performance Testing
- Measure translation latency across device tiers
- Monitor battery drain during extended use
- Track memory usage and OOM scenarios
- Profile thermal throttling behavior

---

## 14. Deployment & Distribution

### Phase 1 Distribution
- **Platform:** Android only
- **Store:** Google Play Store (primary), APK direct download (secondary)
- **Target:** Android 8.0+ (API 26+)
- **Size:** ~50 MB app + ~1.16 GB models (downloaded post-install)

### Future Distribution
- iOS App Store (Phase 2+)
- F-Droid (open-source alternative)
- Direct APK for sideloading

---

## 15. Conclusion

This design spec defines a focused Phase 1 MVP that delivers the core value proposition of Yomu: context-aware manga translation on Android devices using on-device AI. The architecture prioritizes simplicity, performance, and user experience while deferring complex features to future phases.

The system-wide overlay approach allows users to continue using their preferred manga reading apps while benefiting from Yomu's translation capabilities. The hybrid trigger (manual + auto-detect) provides flexibility for different user preferences.

Success in Phase 1 will validate the core technology and user experience, providing a foundation for multi-language support, advanced features, and cross-platform expansion in future phases.

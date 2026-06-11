# Yomu Phase 1 Completion Plan

> **Goal:** Close remaining gaps so Phase 1 MVP is functional end-to-end.

**Gaps:**
1. MediaProjection consent not wired (Activity → Service)
2. Translation overlay renders empty FrameLayout (no text drawn)
3. Home screen toggle doesn't start/stop OverlayService
4. Models screen doesn't refresh model list on init
5. OverlayService translation runs but needs proper capture flow

---

### Fix 1: Wire MediaProjection Consent

**Files:**
- Modify: `app/src/main/java/com/yomu/app/MainActivity.kt`
- Modify: `app/src/main/java/com/yomu/app/service/OverlayService.kt`

Add ActivityResultLauncher for screen capture consent. On result, bind to OverlayService and call `startProjection()`.

### Fix 2: Render Translated Text on Overlay

**Files:**
- Modify: `app/src/main/java/com/yomu/app/service/OverlayService.kt`

Replace empty FrameLayout with Canvas drawing: iterate TypesetBubble, draw text at computed positions with correct font size, background, and bubble bounds.

### Fix 3: Toggle Actually Starts Overlay Service

**Files:**
- Modify: `app/src/main/java/com/yomu/app/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/yomu/app/ui/home/HomeScreen.kt`

Add context-aware start/stop. OverlayService.start/stop methods call through Context.

### Fix 4: Refresh Models on Init

**Files:**
- Modify: `app/src/main/java/com/yomu/app/ui/models/ModelsViewModel.kt`

Already calls `refreshModels()` in init. Verify model list seeding works end-to-end.

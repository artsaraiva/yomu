package com.yomu.app.overlay

import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** A transparent, full-screen overlay window that leaves input focus with the reading app. */
internal fun translatedPageWindowParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
        WindowManager.LayoutParams.TYPE_PHONE,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSPARENT
).apply {
    // A reader that hides the status bar puts bubbles beside the camera cutout (#377). Bars are
    // fitted only while shown, so a bubble card never sits under the clock.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        fitInsetsTypes = WindowInsets.Type.systemBars()
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
}

/**
 * Gives [view]'s window the keyboard while [typing], lifting its content clear of it, and hands input
 * focus back to the reading app after (#553). [params] are the ones the window was added with.
 */
internal fun WindowManager.setTyping(view: View, params: WindowManager.LayoutParams, typing: Boolean) {
    val focusable = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
    if (typing == focusable) return
    params.flags = params.flags xor WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
        if (typing) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE else WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        params.fitInsetsTypes = WindowInsets.Type.systemBars() or if (typing) WindowInsets.Type.ime() else 0
    }
    updateViewLayout(view, params)
}

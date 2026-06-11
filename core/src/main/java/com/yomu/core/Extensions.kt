package com.yomu.core

import android.content.Context
import android.widget.Toast

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Long.toFileSizeString(): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    
    return when {
        this >= gb -> String.format("%.2f GB", this / gb)
        this >= mb -> String.format("%.2f MB", this / mb)
        this >= kb -> String.format("%.2f KB", this / kb)
        else -> "$this B"
    }
}

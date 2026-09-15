package com.yomu.app.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock

data class ResourceReadout(
    /** Proportional set size of the whole process, which counts the model weights paged in. */
    val appPssBytes: Long,
    val nativeHeapBytes: Long,
    val deviceAvailableBytes: Long,
    val deviceTotalBytes: Long,
    /** Null on the first sample: CPU is averaged over the time since the previous one. */
    val cpuPercent: Int?,
    val lastPageMs: Long?
)

/** Samples this process for the Performance screen (#79). The overlay runs in the same process, so it is counted. */
class ResourceMonitor(private val context: Context) {

    private var previousCpuMs = 0L
    private var previousWallMs = 0L

    fun sample(lastPageMs: Long?): ResourceReadout {
        val cpuMs = Process.getElapsedCpuTime()
        val wallMs = SystemClock.elapsedRealtime()
        val cpu = if (previousWallMs == 0L) null
        else cpuPercent(cpuMs - previousCpuMs, wallMs - previousWallMs, Runtime.getRuntime().availableProcessors())
        previousCpuMs = cpuMs
        previousWallMs = wallMs
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        return ResourceReadout(
            appPssBytes = Debug.getPss() * 1024,
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
            deviceAvailableBytes = memory.availMem,
            deviceTotalBytes = memory.totalMem,
            cpuPercent = cpu,
            lastPageMs = lastPageMs
        )
    }
}

/** Share of every core this process used between two samples, or null when no time passed. */
internal fun cpuPercent(cpuDeltaMs: Long, wallDeltaMs: Long, cores: Int): Int? =
    if (wallDeltaMs <= 0 || cores <= 0) null
    else (cpuDeltaMs * 100 / (wallDeltaMs * cores)).coerceIn(0, 100).toInt()

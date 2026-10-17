package com.simplereso.app

import android.content.Context
import android.content.pm.PackageManager

import rikka.shizuku.Shizuku

const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
const val PERMISSION_REQUEST_CODE = 1001
private const val MIN_SERVER_VERSION = 11

/**
 * Lima kemungkinan kondisi Shizuku. Hanya [Ready] yang membuka kontrol resolusi.
 */
sealed class ShizukuState {
    object Ready : ShizukuState()
    object NotInstalled : ShizukuState()
    object NotRunning : ShizukuState()
    data class TooOld(val version: Int) : ShizukuState()
    object PermissionDenied : ShizukuState()

    val isReady: Boolean get() = this is Ready
}

object ShizukuGate {

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Urutan pemeriksaan sengaja dari yang paling murah ke yang paling mahal,
     * supaya tidak pernah memanggil binder yang belum tentu ada.
     */
    fun check(context: Context): ShizukuState {
        if (!Shizuku.pingBinder()) {
            return if (isInstalled(context)) ShizukuState.NotRunning else ShizukuState.NotInstalled
        }

        val version = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        if (version < MIN_SERVER_VERSION) return ShizukuState.TooOld(version)

        if (Shizuku.isPreV11()) return ShizukuState.TooOld(version)

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) return ShizukuState.PermissionDenied

        return ShizukuState.Ready
    }

    fun requestPermission() {
        if (Shizuku.isPreV11()) return
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    /** uid layanan yang sedang melayani kita: 2000 = shell, 0 = root. */
    fun serviceUid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    fun serviceLabel(): String = when (serviceUid()) {
        0 -> "root"
        2000 -> "shell"
        else -> "uid ${serviceUid()}"
    }
}

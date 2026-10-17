package com.simplereso.app

import android.graphics.Point
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Satu set ukuran layar: piksel dan kerapatan. */
data class DisplayConfig(val width: Int, val height: Int, val density: Int) {
    val widthDp: Int get() = (width * 160f / density).toInt()
    val heightDp: Int get() = (height * 160f / density).toInt()
    val megapixels: Float get() = width * height / 1_000_000f
    override fun toString() = "${width}x${height}@${density}"
}

/**
 * Mengubah ukuran layar lewat IWindowManager, dipanggil dengan hak akses shell
 * yang dipinjamkan Shizuku. Semua pemanggilan memakai refleksi karena
 * IWindowManager adalah hidden API dan tanda tangannya berbeda antar versi Android.
 *
 * Kalau refleksi gagal (sebagian ROM menyembunyikan kelasnya), kelas ini
 * jatuh ke perintah shell `wm` lewat Shizuku.newProcess.
 */
object DisplayController {

    private const val DISPLAY_ID = 0

    private val userId: Int get() = android.os.Process.myUid() / 100_000

    private fun service(): Any {
        val binder: IBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("window"))
        val stub = Class.forName("android.view.IWindowManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IWindowManager tidak tersedia")
    }

    private val iface: Class<*> get() = Class.forName("android.view.IWindowManager")

    private fun size(method: String): Point {
        val point = Point()
        val m = iface.getMethod(method, Int::class.javaPrimitiveType, Point::class.java)
        m.invoke(service(), DISPLAY_ID, point)
        return point
    }

    private fun density(method: String): Int {
        val m = iface.getMethod(method, Int::class.javaPrimitiveType)
        return m.invoke(service(), DISPLAY_ID) as Int
    }

    /** Ukuran panel dari pabrik. Tidak ikut berubah walau resolusi sudah di-override. */
    fun readFactory(): Result<DisplayConfig> = runCatching {
        val p = size("getInitialDisplaySize")
        DisplayConfig(p.x, p.y, density("getInitialDisplayDensity"))
    }.recoverCatching {
        parseWmSize(line = 0) ?: throw it
    }

    /** Ukuran yang sedang aktif, termasuk override yang pernah diterapkan. */
    fun readCurrent(): Result<DisplayConfig> = runCatching {
        val p = size("getBaseDisplaySize")
        DisplayConfig(p.x, p.y, density("getBaseDisplayDensity"))
    }.recoverCatching {
        parseWmSize(line = 1) ?: parseWmSize(line = 0) ?: throw it
    }

    fun applySize(width: Int, height: Int): Result<Unit> = runCatching {
        val m = iface.getMethod(
            "setForcedDisplaySize",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        m.invoke(service(), DISPLAY_ID, width, height)
        Unit
    }.recoverCatching {
        shell("wm size ${width}x$height").getOrThrow()
    }

    fun applyDensity(density: Int): Result<Unit> = runCatching {
        val m = iface.getMethod(
            "setForcedDisplayDensityForUser",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        m.invoke(service(), DISPLAY_ID, density, userId)
        Unit
    }.recoverCatching {
        shell("wm density $density").getOrThrow()
    }

    fun resetSize(): Result<Unit> = runCatching {
        iface.getMethod("clearForcedDisplaySize", Int::class.javaPrimitiveType)
            .invoke(service(), DISPLAY_ID)
        Unit
    }.recoverCatching {
        shell("wm size reset").getOrThrow()
    }

    fun resetDensity(): Result<Unit> = runCatching {
        iface.getMethod(
            "clearForcedDisplayDensityForUser",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).invoke(service(), DISPLAY_ID, userId)
        Unit
    }.recoverCatching {
        shell("wm density reset").getOrThrow()
    }

    fun applyConfig(config: DisplayConfig, factory: DisplayConfig): Result<Unit> = runCatching {
        if (config.width == factory.width && config.height == factory.height) {
            resetSize().getOrThrow()
        } else {
            applySize(config.width, config.height).getOrThrow()
        }
        if (config.density == factory.density) {
            resetDensity().getOrThrow()
        } else {
            applyDensity(config.density).getOrThrow()
        }
    }

    fun resetAll(): Result<Unit> = runCatching {
        resetSize().getOrThrow()
        resetDensity().getOrThrow()
    }

    // ---- jalur cadangan: perintah shell lewat Shizuku ----

    private fun shell(command: String): Result<Unit> = runCatching {
        val output = exec(command)
        if (output.second != 0) error("Perintah ditolak: $command\n${output.first.trim()}")
    }

    private fun exec(command: String): Pair<String, Int> {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        val text = process.inputStream.bufferedReader().use { it.readText() } +
            process.errorStream.bufferedReader().use { it.readText() }
        return text to process.waitFor()
    }

    /**
     * `wm size` mencetak dua baris:
     *   Physical size: 1080x2400
     *   Override size: 720x1600
     * line 0 = fisik (pabrik), line 1 = override yang aktif.
     */
    private fun parseWmSize(line: Int): DisplayConfig? {
        val sizeOut = runCatching { exec("wm size").first }.getOrNull() ?: return null
        val densOut = runCatching { exec("wm density").first }.getOrNull() ?: return null

        val sizes = Regex("""(\d+)x(\d+)""").findAll(sizeOut).toList()
        val densities = Regex(""":\s*(\d+)""").findAll(densOut).toList()
        if (sizes.isEmpty() || densities.isEmpty()) return null

        val s = sizes.getOrNull(line) ?: sizes.first()
        val d = densities.getOrNull(line) ?: densities.first()
        return DisplayConfig(
            width = s.groupValues[1].toInt(),
            height = s.groupValues[2].toInt(),
            density = d.groupValues[1].toInt()
        )
    }
}

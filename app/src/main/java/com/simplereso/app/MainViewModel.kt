package com.simplereso.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val REVERT_SECONDS = 10

data class UiState(
    val shizuku: ShizukuState = ShizukuState.NotRunning,
    val factory: DisplayConfig? = null,
    val current: DisplayConfig? = null,
    val inputWidth: String = "",
    val inputHeight: String = "",
    val inputDensity: String = "",
    val autoDensity: Boolean = true,
    val pendingRevert: DisplayConfig? = null,
    val secondsLeft: Int = 0,
    val message: String? = null,
    val busy: Boolean = false,
) {
    val ready: Boolean get() = shizuku.isReady && factory != null

    /** dpi yang disarankan agar ukuran teks dan ikon tetap wajar setelah resolusi berubah. */
    fun suggestedDensity(width: Int): Int {
        val f = factory ?: return 160
        return (f.density.toFloat() * width / f.width).toInt().coerceIn(80, 900)
    }

    fun target(): DisplayConfig? {
        val w = inputWidth.toIntOrNull() ?: return null
        val h = inputHeight.toIntOrNull() ?: return null
        if (w !in 240..4096 || h !in 320..8192) return null
        val d = if (autoDensity) suggestedDensity(w) else (inputDensity.toIntOrNull() ?: return null)
        if (d !in 80..900) return null
        return DisplayConfig(w, h, d)
    }

    val aspectMismatch: Boolean
        get() {
            val f = factory ?: return false
            val t = target() ?: return false
            return kotlin.math.abs(t.height.toFloat() / t.width - f.height.toFloat() / f.width) > 0.02f
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var revertJob: Job? = null

    fun refresh() {
        val shizuku = ShizukuGate.check(getApplication())
        if (!shizuku.isReady) {
            _state.update { it.copy(shizuku = shizuku, factory = null) }
            readCurrentOnly()
            return
        }
        val factory = DisplayController.readFactory().getOrNull()
        val current = DisplayController.readCurrent().getOrNull() ?: factory
        _state.update {
            it.copy(
                shizuku = shizuku,
                factory = factory,
                current = current,
                message = if (factory == null) "Resolusi pabrik tidak terbaca di perangkat ini." else null,
                inputWidth = it.inputWidth.ifEmpty { current?.width?.toString().orEmpty() },
                inputHeight = it.inputHeight.ifEmpty { current?.height?.toString().orEmpty() },
                inputDensity = it.inputDensity.ifEmpty { current?.density?.toString().orEmpty() },
            )
        }
    }

    /** Tanpa Shizuku kita masih bisa membaca ukuran yang sedang aktif dari sistem. */
    private fun readCurrentOnly() {
        val m = getApplication<Application>().resources.displayMetrics
        _state.update { it.copy(current = DisplayConfig(m.widthPixels, m.heightPixels, m.densityDpi)) }
    }

    fun setWidth(v: String) = _state.update { it.copy(inputWidth = v.filter(Char::isDigit).take(4)) }
    fun setHeight(v: String) = _state.update { it.copy(inputHeight = v.filter(Char::isDigit).take(4)) }
    fun setDensity(v: String) = _state.update { it.copy(inputDensity = v.filter(Char::isDigit).take(3)) }
    fun setAutoDensity(on: Boolean) = _state.update { it.copy(autoDensity = on) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun pickScale(percent: Int) {
        val f = _state.value.factory ?: return
        val w = (f.width * percent / 100 / 2) * 2
        val h = (f.height * percent / 100 / 2) * 2
        _state.update {
            it.copy(
                inputWidth = w.toString(),
                inputHeight = h.toString(),
                autoDensity = true,
                inputDensity = it.suggestedDensity(w).toString(),
            )
        }
    }

    fun applyResolution() {
        val s = _state.value
        val target = s.target() ?: return
        val factory = s.factory ?: return
        val before = s.current ?: factory

        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = DisplayController.applyConfig(target, factory)
            result.onFailure { e ->
                _state.update { it.copy(busy = false, message = friendly(e)) }
                return@launch
            }
            _state.update {
                it.copy(busy = false, current = target, pendingRevert = before, secondsLeft = REVERT_SECONDS)
            }
            startRevertCountdown()
        }
    }

    /**
     * Jaring pengaman: kalau resolusi baru membuat layar tidak terbaca, pengguna
     * cukup menunggu dan sistem kembali sendiri ke keadaan sebelumnya.
     */
    private fun startRevertCountdown() {
        revertJob?.cancel()
        revertJob = viewModelScope.launch {
            repeat(REVERT_SECONDS) {
                delay(1_000)
                _state.update { it.copy(secondsLeft = it.secondsLeft - 1) }
            }
            undo()
        }
    }

    fun keep() {
        revertJob?.cancel()
        _state.update { it.copy(pendingRevert = null, secondsLeft = 0) }
    }

    fun undo() {
        revertJob?.cancel()
        val back = _state.value.pendingRevert ?: return
        val factory = _state.value.factory ?: return
        viewModelScope.launch {
            DisplayController.applyConfig(back, factory)
            _state.update { it.copy(current = back, pendingRevert = null, secondsLeft = 0) }
        }
    }

    fun resetToFactory() {
        revertJob?.cancel()
        val factory = _state.value.factory ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = DisplayController.resetAll()
            _state.update {
                it.copy(
                    busy = false,
                    current = if (result.isSuccess) factory else it.current,
                    pendingRevert = null,
                    secondsLeft = 0,
                    inputWidth = factory.width.toString(),
                    inputHeight = factory.height.toString(),
                    inputDensity = factory.density.toString(),
                    autoDensity = true,
                    message = result.exceptionOrNull()?.let(::friendly),
                )
            }
        }
    }

    private fun friendly(e: Throwable): String = when {
        e.message?.contains("SecurityException", true) == true ->
            "Ditolak sistem. Sebagian ROM (MIUI, ColorOS) mengunci perubahan ukuran layar."
        e is NoSuchMethodException || e is ClassNotFoundException ->
            "Versi Android ini memakai antarmuka berbeda. Jalur perintah shell juga gagal."
        else -> e.message ?: "Gagal menerapkan resolusi."
    }
}

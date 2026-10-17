@file:OptIn(ExperimentalMaterial3Api::class)

package com.simplereso.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplereso.app.*

@Composable
fun MainScreen(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simple Reso", fontWeight = FontWeight.Bold) },
                actions = {
                    Text(
                        if (s.current != null && s.current == s.factory) "Standar" else "Diubah",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (s.current == s.factory) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShizukuBanner(s.shizuku)

            if (!s.shizuku.isReady) {
                GateCard(
                    state = s.shizuku,
                    onRequest = { ShizukuGate.requestPermission() },
                    onOpenShizuku = {
                        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                            ?.let { context.startActivity(it) }
                    },
                    onInstall = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            CurrentCard(s)

            // Seluruh kontrol di bawah ini hanya hidup kalau Shizuku lolos pemeriksaan.
            val enabled = s.ready && !s.busy
            FactoryCard(s, enabled, vm::resetToFactory)
            ScaleCard(s, enabled, vm::pickScale)
            CustomCard(s, enabled, vm)
        }
    }

    s.pendingRevert?.let { back ->
        RevertDialog(seconds = s.secondsLeft, back = back, onKeep = vm::keep, onUndo = vm::undo)
    }

    s.message?.let {
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            title = { Text("Tidak bisa dilanjutkan") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("Mengerti") } }
        )
    }
}

@Composable
private fun ShizukuBanner(state: ShizukuState) {
    val ok = state.isReady
    val bg = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val fg = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(fg))
        Text(
            when (state) {
                is ShizukuState.Ready -> "Shizuku aktif · ${ShizukuGate.serviceLabel()}"
                is ShizukuState.PermissionDenied -> "Shizuku berjalan, izin belum diberikan"
                is ShizukuState.NotRunning -> "Shizuku terpasang, layanan belum berjalan"
                is ShizukuState.NotInstalled -> "Shizuku tidak ditemukan di perangkat"
                is ShizukuState.TooOld -> "Shizuku v${state.version} terlalu lama"
            },
            color = fg,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun GateCard(
    state: ShizukuState,
    onRequest: () -> Unit,
    onOpenShizuku: () -> Unit,
    onInstall: () -> Unit,
) {
    val (title, body, label, action) = when (state) {
        is ShizukuState.PermissionDenied -> Quad(
            "Izin Shizuku diperlukan",
            "Simple Reso perlu izin untuk membaca dan mengubah ukuran layar. Izin ini hanya berlaku di Shizuku, bukan izin sistem.",
            "Minta izin sekarang", onRequest
        )
        is ShizukuState.NotRunning -> Quad(
            "Layanan Shizuku belum jalan",
            "Buka aplikasi Shizuku lalu mulai lewat ADB nirkabel. Perlu diulang setiap kali HP dinyalakan ulang.",
            "Buka aplikasi Shizuku", onOpenShizuku
        )
        is ShizukuState.NotInstalled -> Quad(
            "Shizuku belum terpasang",
            "Simple Reso bekerja tanpa root, tapi butuh Shizuku sebagai jembatan ke izin shell.",
            "Unduh Shizuku", onInstall
        )
        is ShizukuState.TooOld -> Quad(
            "Versi Shizuku terlalu lama",
            "Dibutuhkan Shizuku v12 ke atas. Versi lama tidak mendukung pemanggilan IWindowManager.",
            "Perbarui Shizuku", onInstall
        )
        else -> return
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Fitur terkunci", MaterialTheme.colorScheme.error)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}

private data class Quad(val a: String, val b: String, val c: String, val d: () -> Unit)

@Composable
private fun CurrentCard(s: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("Resolusi saat ini")
            val c = s.current
            Text(
                if (c != null) "${c.width} × ${c.height}" else "—",
                fontFamily = FontFamily.Monospace,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (c != null) "${c.density} dpi · ${c.widthDp} × ${c.heightDp} dp" else "tidak terbaca",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (c != null) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Metric("Rasio", aspectLabel(c.width, c.height))
                    Metric("Piksel", String.format("%.2f MP", c.megapixels))
                }
            }
        }
    }
}

@Composable
private fun FactoryCard(s: UiState, enabled: Boolean, onReset: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Resolusi standar pabrik")
            val f = s.factory
            Text(
                if (f != null) "${f.width} × ${f.height} · ${f.density} dpi"
                else "— tidak terbaca tanpa Shizuku",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = onReset,
                enabled = enabled && f != null && s.current != f,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pulihkan ke standar") }
        }
    }
}

@Composable
private fun ScaleCard(s: UiState, enabled: Boolean, onPick: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Skala cepat")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(100, 90, 80, 75, 66, 50).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            val f = s.factory
                            val w = f?.let { (it.width * p / 100 / 2) * 2 }
                            val h = f?.let { (it.height * p / 100 / 2) * 2 }
                            val selected = w != null &&
                                s.inputWidth == w.toString() && s.inputHeight == h.toString()
                            FilterChip(
                                selected = selected,
                                onClick = { onPick(p) },
                                enabled = enabled && f != null,
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        if (p == 100) "Standar" else "$p%",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }
                    }
                }
            }
            s.factory?.let { f ->
                Text(
                    "Ketuk untuk mengisi kolom di bawah. 100% = ${f.width} × ${f.height}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomCard(s: UiState, enabled: Boolean, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("Resolusi khusus")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberField("Lebar (W)", s.inputWidth, enabled, Modifier.weight(1f), vm::setWidth)
                Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField("Tinggi (H)", s.inputHeight, enabled, Modifier.weight(1f), vm::setHeight)
            }
            NumberField(
                "Kerapatan (dpi)",
                if (s.autoDensity) s.target()?.density?.toString() ?: s.inputDensity else s.inputDensity,
                enabled && !s.autoDensity,
                Modifier.fillMaxWidth(),
                vm::setDensity
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(s.autoDensity, { vm.setAutoDensity(it) }, enabled = enabled)
                Text(
                    "Hitung dpi otomatis mengikuti rasio lebar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            s.target()?.let { t ->
                Text(
                    "${t.widthDp} × ${t.heightDp} dp · ${String.format("%.2f", t.megapixels)} MP · rasio ${aspectLabel(t.width, t.height)}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (s.aspectMismatch) {
                Text(
                    "Rasio target berbeda dari panel. Android akan melakukan letterbox dan sebagian antarmuka bisa terpotong.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = vm::applyResolution,
                enabled = enabled && s.target() != null && s.target() != s.current,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (s.busy) "Menerapkan…" else "Terapkan resolusi")
            }
        }
    }
}

@Composable
private fun RevertDialog(seconds: Int, back: DisplayConfig, onKeep: () -> Unit, onUndo: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Simpan resolusi ini?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kalau tampilan tidak terbaca, cukup tunggu.")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "$seconds",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Kembali otomatis ke ${back.width} × ${back.height} bila tidak dikonfirmasi.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onKeep) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onUndo) { Text("Batalkan") } }
    )
}

// ---- potongan kecil yang dipakai berulang ----

@Composable
private fun Label(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Label(label)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        enabled = enabled,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private fun aspectLabel(w: Int, h: Int): String {
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(w, h).coerceAtLeast(1)
    val a = h / g
    val b = w / g
    return if (a > 40 || b > 40) String.format("%.1f:1", h.toFloat() / w) else "$a:$b"
}

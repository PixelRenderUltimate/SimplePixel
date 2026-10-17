# Shizuku memanggil balik lewat binder, kelas-kelasnya tidak boleh di-obfuscate.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Nama method hidden API dipanggil lewat refleksi.
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-dontwarn android.view.IWindowManager
-dontwarn android.view.IWindowManager$Stub

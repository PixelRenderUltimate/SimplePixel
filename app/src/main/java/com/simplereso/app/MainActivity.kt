package com.simplereso.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.simplereso.app.ui.MainScreen
import com.simplereso.app.ui.SimpleResoTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    /** Status berubah sendiri begitu layanan Shizuku menyala, tanpa perlu ketuk apa pun. */
    private val binderReceived = Shizuku.OnBinderReceivedListener { vm.refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { vm.refresh() }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> vm.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)

        setContent {
            SimpleResoTheme {
                MainScreen(vm)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() }
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        super.onDestroy()
    }
}

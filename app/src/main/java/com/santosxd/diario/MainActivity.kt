package com.santosxd.diario

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.santosxd.diario.data.DiaryDb
import com.santosxd.diario.data.Repo
import com.santosxd.diario.security.Prefs
import com.santosxd.diario.ui.DiaryTheme
import com.santosxd.diario.ui.LockScreen
import com.santosxd.diario.ui.MainScreen
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var prefs: Prefs
    private var locked by mutableStateOf(false)

    /** Se activa justo antes de abrir un selector de archivos, para no bloquear la app al volver. */
    var suppressLock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Oculta el contenido en capturas de pantalla y en la vista de apps recientes.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        prefs = Prefs(this)
        locked = prefs.hasPin()
        val repo = Repo(DiaryDb.get(this))
        lifecycleScope.launch { repo.seedIfEmpty() }

        setContent {
            var theme by remember { mutableStateOf(prefs.theme) }
            DiaryTheme(theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (locked) {
                        LockScreen(prefs, this@MainActivity) { locked = false }
                    } else {
                        MainScreen(repo, prefs, this@MainActivity, theme) {
                            theme = it
                            prefs.theme = it
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!suppressLock && prefs.hasPin()) locked = true
    }

    override fun onResume() {
        super.onResume()
        suppressLock = false
    }
}

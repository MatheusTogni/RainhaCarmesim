package com.rainhacarmesim.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rainhacarmesim.app.ui.navigation.RainhaRoot
import com.rainhacarmesim.app.ui.theme.RainhaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as RainhaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            RainhaTheme {
                RainhaRoot()
            }
        }

        // Monitoramento SOMENTE com o app visível: ao ir para segundo plano estes laços são cancelados.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { container.trafficMonitor.run() }
                launch { container.scanCoordinator.runAutoScanLoop(container.settings) }
            }
        }

        if (savedInstanceState == null) container.updateManager.checkIfDue()
    }

    override fun onResume() {
        super.onResume()
        // Volta da tela "instalar apps desconhecidos": libera a instalação se a permissão foi dada.
        container.updateManager.onReturnedFromSettings()
    }
}

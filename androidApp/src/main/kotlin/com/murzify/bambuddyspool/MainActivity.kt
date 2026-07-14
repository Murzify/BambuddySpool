package com.murzify.bambuddyspool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.murzify.bambuddyspool.app.App
import com.murzify.bambuddyspool.app.bootstrap.createRootGraph
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.core.platform.mockPlatformServices

/** Thin Android launcher that owns lifecycle wiring and renders the shared root. */
class MainActivity : ComponentActivity() {
    private val nfcIntentAdapter = AndroidNfcIntentAdapter()
    private val root by lazy {
        createRootGraph(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            platformServices = mockPlatformServices().copy(nfc = AndroidNfcService(applicationContext))
        ).rootComponent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Parse the cold-launch scan before graph construction and render Processing without waiting for bootstrap IO.
        routeNfcIntent(intent)

        setContent {
            App(root)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeNfcIntent(intent)
    }

    private fun routeNfcIntent(intent: android.content.Intent?) {
        nfcIntentAdapter.read(intent)?.let { observation ->
            root.accept(RootIntent.BeginNfcScan(observation))
        }
    }
}

@Preview
@Composable
@Suppress("FunctionNaming") // Compose entry points use UpperCamelCase by convention.
private fun AppAndroidPreview() {
    val root = createRootGraph(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        platformServices = mockPlatformServices()
    ).rootComponent
    App(root)
}

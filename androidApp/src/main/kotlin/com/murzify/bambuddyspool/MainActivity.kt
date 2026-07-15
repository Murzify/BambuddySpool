package com.murzify.bambuddyspool

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.murzify.bambuddyspool.app.App
import com.murzify.bambuddyspool.app.bootstrap.createRootGraph
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.core.performance.ColdLaunchProcessingTiming
import com.murzify.bambuddyspool.core.performance.MonotonicClock

/** Thin Android launcher that owns lifecycle wiring and renders the shared root. */
class MainActivity : ComponentActivity() {
    private val nfcIntentAdapter = AndroidNfcIntentAdapter()
    private var coldLaunchProcessingTiming: ColdLaunchProcessingTiming? = null
    private val root by lazy {
        createRootGraph(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            nfcService = AndroidNfcService(applicationContext),
            secureTokenStore = (application as BambuddyApplication).secureStorage
        ).rootComponent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Parse the cold-launch scan before graph construction and render Processing without waiting for bootstrap IO.
        nfcIntentAdapter.read(intent)?.let { observation ->
            coldLaunchProcessingTiming = ColdLaunchProcessingTiming(
                startedAtMillis = observation.monotonicTimestampMillis,
                clock = MonotonicClock(SystemClock::elapsedRealtime)
            )
            root.accept(RootIntent.BeginNfcScan(observation))
        }

        setContent {
            App(root, onProcessingComposed = { coldLaunchProcessingTiming?.markProcessingComposed() })
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

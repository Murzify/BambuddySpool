package com.murzify.bambuddyspool

import android.os.Bundle
import android.os.SystemClock
import android.nfc.NfcAdapter
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
    private val liveTagMutationBridge = AndroidLiveTagMutationBridge()
    private var coldLaunchProcessingTiming: ColdLaunchProcessingTiming? = null
    private val root by lazy {
        createRootGraph(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            nfcService = AndroidNfcService(applicationContext),
            secureTokenStore = (application as BambuddyApplication).secureStorage,
            liveTagMutationBridge = liveTagMutationBridge
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
        liveTagMutationBridge.onRead = root::onTagMutationRead
        liveTagMutationBridge.onArmedChanged = { enabled ->
            runOnUiThread { setTagMutationReaderEnabled(enabled) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (liveTagMutationBridge.isArmed) setTagMutationReaderEnabled(true)
    }

    private fun setTagMutationReaderEnabled(enabled: Boolean) {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        if (!enabled) {
            adapter.disableReaderMode(this)
            return
        }
        adapter.enableReaderMode(
            this,
            { tag -> runOnUiThread { liveTagMutationBridge.accept(tag) } },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            null
        )
    }

    override fun onPause() {
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
        super.onPause()
    }

    private fun routeNfcIntent(intent: android.content.Intent?) {
        nfcIntentAdapter.read(intent)?.let { observation ->
            root.accept(RootIntent.BeginNfcScan(observation))
        }
    }
}

package com.murzify.bambuddyspool

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.murzify.bambuddyspool.app.App
import com.murzify.bambuddyspool.app.bootstrap.createRootGraph
import com.murzify.bambuddyspool.core.platform.mockPlatformServices

/** Creates the shared Compose controller exported to the Swift shell. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming") // Stable Swift interop entry-point name.
fun MainViewController() = ComposeUIViewController {
    val root = createRootGraph(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        platformServices = mockPlatformServices()
    ).rootComponent
    App(root)
}

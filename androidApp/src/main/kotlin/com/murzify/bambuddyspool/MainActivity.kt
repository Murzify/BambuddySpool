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
import com.murzify.bambuddyspool.core.platform.mockPlatformServices

class MainActivity : ComponentActivity() {
    private val root by lazy {
        createRootGraph(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            platformServices = mockPlatformServices(),
        ).rootComponent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(root)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val root = createRootGraph(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        platformServices = mockPlatformServices(),
    ).rootComponent
    App(root)
}

package com.murzify.bambuddyspool.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.app.root.RootComponent
import com.murzify.bambuddyspool.app.root.RootIntent

@Composable
fun App(root: RootComponent) {
    val state by root.state.collectAsState()
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(state.title)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RootDestination.entries.forEach { destination ->
                    Button(onClick = { root.accept(RootIntent.Select(destination)) }) {
                        Text(destination.title)
                    }
                }
            }
        }
    }
}

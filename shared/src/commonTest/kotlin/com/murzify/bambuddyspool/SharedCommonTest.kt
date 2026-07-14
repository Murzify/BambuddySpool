package com.murzify.bambuddyspool

import com.murzify.bambuddyspool.app.navigation.RootDestination
import kotlin.test.Test
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun everyRootDestinationHasAUniqueStableName() {
        val names = RootDestination.entries.map(RootDestination::name)

        assertTrue(names.all(String::isNotBlank))
        assertTrue(names.size == names.distinct().size)
    }
}

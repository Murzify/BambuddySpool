package com.murzify.bambuddyspool

import com.murzify.bambuddyspool.app.navigation.RootDestination
import kotlin.test.Test
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun everyRootDestinationHasANonEmptyUniqueTitle() {
        val titles = RootDestination.entries.map(RootDestination::title)

        assertTrue(titles.all(String::isNotBlank))
        assertTrue(titles.size == titles.distinct().size)
    }
}

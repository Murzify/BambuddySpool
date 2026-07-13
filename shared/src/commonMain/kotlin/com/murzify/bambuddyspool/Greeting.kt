package com.murzify.bambuddyspool

class Greeting {
    private val platform = getPlatform()

    fun greet(): String = sayHello(platform.name)
}

package com.murzify.bambuddyspool

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

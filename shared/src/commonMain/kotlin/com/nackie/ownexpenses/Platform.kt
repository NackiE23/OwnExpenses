package com.nackie.ownexpenses

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
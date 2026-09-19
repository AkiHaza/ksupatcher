package org.akuatech.ksupatcher.data

object UpdateConfig {
    const val appOwner = "akuatech"
    const val appRepo = "ksupatcher"

    /** Official upstream release sources used when a user has not selected a custom .ko. */
    const val ksuLkmOwner = "tiann"
    const val ksuLkmRepo = "KernelSU"
    const val ksunLkmOwner = "KernelSU-Next"
    const val ksunLkmRepo = "KernelSU-Next"
    const val resukiSuOwner = "ReSukiSU"
    const val resukiSuRepo = "ReSukiSU"
    const val backslashxxOwner = "backslashxx"
    const val backslashxxRepo = "KernelSU"

    val supportedKmis = listOf(
        "android12-5.10",
        "android13-5.10",
        "android13-5.15",
        "android14-5.15",
        "android14-6.1",
        "android15-6.6",
        "android16-6.12",
        "android17-6.18"
    )

    const val defaultKmi = "android14-6.1"
}

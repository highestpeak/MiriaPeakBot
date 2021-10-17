package com.highestpeak

import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.enable
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.load
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader

/**
 * 只需要启动这个类来测试
 */
suspend fun main() {
    MiraiConsoleTerminalLoader.startAsDaemon()

    //如果是Kotlin
//    PluginMain.load()
//    PluginMain.enable()
    //如果是Java
    PeakBot.INSTANCE.load()
    PeakBot.INSTANCE.enable()

    val bot = MiraiConsole.addBot(111, "mima") {
        fileBasedDeviceInfo()
    }.alsoLogin()

    MiraiConsole.job.join()
}
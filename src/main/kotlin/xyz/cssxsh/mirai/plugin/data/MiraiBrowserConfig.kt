package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.util.*

object MiraiBrowserConfig : AutoSavePluginConfig("MiraiBrowserConfig") {

    @ValueDescription("Chrome/Chromium 二进制文件路径")
    var chrome by value("")

    @ValueDescription("Edge 二进制文件路径")
    var edge by value("")

    @ValueDescription("Firefox 二进制文件路径")
    var firefox by value("")

    @OptIn(ConsoleExperimentalApi::class)
    override fun shouldPerformAutoSaveWheneverChanged(): Boolean = false
}
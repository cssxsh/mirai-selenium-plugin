package xyz.cssxsh.mirai.selenium.data

import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.util.*

/**
 * 浏览器相关设置
 */
public object MiraiBrowserConfig : AutoSavePluginConfig("MiraiBrowserConfig") {

    @ValueDescription("Chrome/Chromium 二进制文件路径")
    public var chrome: String by value("")

    @ValueDescription("Edge 二进制文件路径")
    public var edge: String by value("")

    @ValueDescription("Firefox 二进制文件路径")
    public var firefox: String by value("")

    @OptIn(ConsoleExperimentalApi::class)
    override fun shouldPerformAutoSaveWheneverChanged(): Boolean = false
}
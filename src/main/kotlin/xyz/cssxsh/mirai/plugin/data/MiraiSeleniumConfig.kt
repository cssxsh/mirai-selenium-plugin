package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.selenium.*

object MiraiSeleniumConfig : ReadOnlyPluginConfig("MiraiSeleniumConfig"), RemoteWebDriverConfig {

    @ValueName("expires")
    @ValueDescription("驱动文件过期时间，默认一星期 (单位：天)")
    val expires by value( 7)

    @ValueName("destroy")
    @ValueDescription("清理浏览器驱动，默认30 (单位：分钟)")
    val destroy by value( 30)

    @ValueName("user_agent")
    @ValueDescription("截图UA")
    override val userAgent: String by value(UserAgents.IPAD)

    @ValueName("width")
    @ValueDescription("截图宽度")
    override val width: Int by value(768)

    @ValueName("height")
    @ValueDescription("截图高度")
    override val height: Int by value(1024)

    @ValueName("pixel_ratio")
    @ValueDescription("截图像素比")
    override val pixelRatio: Int by value(3)

    @ValueName("headless")
    @ValueDescription("无头模式（后台模式）")
    override val headless: Boolean by value(true)

    @ValueName("proxy")
    @ValueDescription("代理地址")
    override val proxy: String by value("")

    @ValueName("log")
    @ValueDescription("启用日志文件")
    override val log: Boolean by value(false)

    @ValueName("browser")
    @ValueDescription("指定使用的浏览器，Chrome/Chromium/Firefox/Edge")
    override val browser: String by value("")

    @ValueName("factory")
    @ValueDescription("指定使用的Factory")
    override val factory: String by value("ktor")

    @ValueName("arguments")
    @ValueDescription("自定义 arguments")
    override val arguments: List<String> by value(emptyList())
}
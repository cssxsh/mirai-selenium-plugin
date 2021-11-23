package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.selenium.*

object MiraiSeleniumPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "xyz.cssxsh.mirai.plugin.mirai-selenium-plugin",
        name = "mirai-selenium-plugin",
        version = "1.0.0",
    ) {
        author("cssxsh")
    }
) {

    private var installed = false

    /**
     * 初始化 Selenium
     * @see [setupSelenium]
     */
    fun setup(flush: Boolean = false): Boolean = synchronized(this) {
        if (!flush && installed) return true

        SeleniumToolConfig.reload()
        installed = false

        try {
            setupSelenium(dataFolder, SeleniumToolConfig.browser)
            installed = true
        } catch (exception: UnsupportedOperationException) {
            logger.warning { "请安装 Chrome 或者 Firefox 浏览器 $exception" }
        } catch (cause: Throwable) {
            logger.warning({ "初始化浏览器驱动失败 $cause" }, cause)
        }

        return installed
    }

    /**
     * 创建一个 RemoteWebDriver
     * @param config 配置
     * @see RemoteWebDriver
     */
    fun driver(config: RemoteWebDriverConfig = SeleniumToolConfig) = RemoteWebDriver(config)

    override fun onEnable() {
        SeleniumToolConfig.reload()
    }
}
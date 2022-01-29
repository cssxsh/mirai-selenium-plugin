package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.utils.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.selenium.*
import java.io.*
import java.util.logging.*

public object MiraiSeleniumPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "xyz.cssxsh.mirai.plugin.mirai-selenium-plugin",
        name = "mirai-selenium-plugin",
        version = "2.0.5",
    ) {
        author("cssxsh")
    }
) {

    private var installed = false

    /**
     * 初始化 Selenium
     * @param flush 是否重新安装
     * @see setupWebDriver
     */
    public fun setup(flush: Boolean = false): Boolean = synchronized(this) {
        if (!flush && installed) return@synchronized true

        if (RemoteWebDriverConfig.arguments.isNotEmpty()) {
            logger.info { "额外参数: ${RemoteWebDriverConfig.arguments}" }
        }
        installed = false
        try {
            setupWebDriver(browser = RemoteWebDriverConfig.browser)
            installed = true
        } catch (exception: UnsupportedOperationException) {
            logger.warning({ "浏览器 ${RemoteWebDriverConfig.browser} 不受支持" }, exception)
        } catch (cause: Throwable) {
            logger.warning({ "初始化浏览器驱动失败" }, cause)
        }

        return@synchronized installed
    }

    /**
     * 创建一个 RemoteWebDriver
     * @param config 驱动配置
     */
    public fun driver(config: RemoteWebDriverConfig = RemoteWebDriverConfig.INSTANCE): RemoteWebDriver {
        return RemoteWebDriver(config)
    }

    /**
     * 清理驱动文件，会在插件 [onDisable] 时执行一次，如非必要，无需主动执行
     * @see clearWebDriver
     */
    public fun clear(): Unit = synchronized(this) {
        if (!installed) return@synchronized

        val deleted = clearWebDriver()

        if (deleted.isEmpty()) return@synchronized
        logger.info { "以下文件已清理: ${deleted.joinToString { it.name }}" }
    }

    /**
     * 下载解压 firefox, [版本列表](https://archive.mozilla.org/pub/firefox/releases/)
     * @param version 浏览器版本
     * @see setupFirefox
     */
    public fun firefox(version: String): File = setupFirefox(folder = dataFolder, version = version)

    /**
     * 下载解压 chromium
     * @param version 浏览器版本
     * @see setupChromium
     */
    public fun chromium(version: String): File = setupChromium(folder = dataFolder, version = version)

    override fun PluginComponentStorage.onLoad() {
        SeleniumContext = childScopeContext(name = "Selenium", context = Dispatchers.IO)
        SeleniumLogger.level = Level.OFF
        System.setProperty(CHROME_DRIVER_MIRRORS, "https://npm.taobao.org/mirrors/chromedriver")
        System.setProperty(FIREFOX_DRIVER_MIRRORS, "https://npm.taobao.org/mirrors/geckodriver")
        System.setProperty(SEVEN7Z_MIRRORS, "https://downloads.sourceforge.net/sevenzip")
        System.setProperty(SELENIUM_FOLDER, dataFolder.resolve("selenium").absolutePath)
    }

    /**
     * 清理驱动进程，会在 [onEnable] 后定期执行，如非必要，无需主动执
     * @param enable 判断是否进程是否正常工作，false 时清除所有进程
     */
    public fun destroy(enable: Boolean = true) {
        DriverCache.entries.removeIf { (driver, service) ->
            if (enable && driver.sessionId != null && service.isRunning) return@removeIf false

            logger.info { "Destroy driver, session: ${driver.sessionId}, process: ${service.getProcess()}" }

            try {
                driver.quit()
            } catch (cause: Throwable) {
                logger.warning({ "Driver ${service.getProcess() ?: service.url} stop failure." }, cause)
            }
            try {
                service.stop()
            } catch (cause: Throwable) {
                logger.warning({ "Service ${service.getProcess() ?: service.url} stop failure." }, cause)
            }

            true
        }
    }

    override fun onEnable() {
        MiraiSeleniumConfig.reload()
        MiraiBrowserConfig.reload()
        SeleniumCommand.register()

        System.setProperty(SELENIUM_DOWNLOAD_EXPIRES, "${MiraiSeleniumConfig.expires}")
        with(MiraiBrowserConfig) {
            if (chrome.isNotBlank()) System.setProperty(CHROME_BROWSER_BINARY, chrome)
            if (edge.isNotBlank()) System.setProperty(EDGE_BROWSER_BINARY, edge)
            if (firefox.isNotBlank()) System.setProperty(FIREFOX_BROWSER_BINARY, firefox)
        }

        launch(Dispatchers.IO) {
            while (isActive) {
                delay(MiraiSeleniumConfig.destroy * 60_000L)
                try {
                    logger.info { "DriverCache: ${DriverCache.status()}" }
                } catch (cause: Throwable) {
                    logger.warning { "DriverCache: $cause" }
                }
                destroy()
            }
        }
    }

    override fun onDisable() {
        SeleniumCommand.unregister()
        destroy(enable = false)
        clear()
    }
}
package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScopeContext
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.selenium.*
import java.util.logging.*

object MiraiSeleniumPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "xyz.cssxsh.mirai.plugin.mirai-selenium-plugin",
        name = "mirai-selenium-plugin",
        version = "2.0.2",
    ) {
        author("cssxsh")
    }
) {

    private var installed = false

    /**
     * 初始化 Selenium
     *
     * @see [setupWebDriver]
     */
    fun setup(flush: Boolean = false): Boolean = synchronized(this) {
        if (!flush && installed) return@synchronized true

        if (RemoteWebDriverConfig.arguments.isNotEmpty()) {
            logger.info { "额外参数: ${RemoteWebDriverConfig.arguments}" }
        }
        installed = false
        val folder = dataFolder.resolve("selenium")
        folder.mkdirs()
        System.setProperty(SELENIUM_FOLDER, folder.absolutePath)
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
     * @param config 配置
     * @see RemoteWebDriver
     */
    fun driver(config: RemoteWebDriverConfig = RemoteWebDriverConfig.INSTANCE) = RemoteWebDriver(config)

    fun clear(): Unit = synchronized(this) {
        if (!installed) return@synchronized

        val deleted = clearWebDriver(expires = MiraiSeleniumConfig.expires)

        if (deleted.isEmpty()) return@synchronized
        logger.info { "以下文件已清理: ${deleted.joinToString { it.name }}" }
    }

    @OptIn(ConsoleExperimentalApi::class)
    override fun PluginComponentStorage.onLoad() {
        SeleniumContext = childScopeContext(name = "Selenium", context = Dispatchers.IO)
        SeleniumLogger.level = Level.OFF
    }

    private fun destroy(enable: Boolean = true) {

        DriverCache.entries.removeIf { (driver, service) ->
            if (enable && driver.sessionId != null && service.isRunning) return@removeIf false

            logger.info { "Destroy driver, sessionId: ${driver.sessionId}, ${service.url}" }

            try {
                service.stop()
            } catch (cause: Throwable) {
                logger.warning({ "Service ${service.url} stop failure." }, cause)
            }

            true
        }
    }

    override fun onEnable() {
        MiraiSeleniumConfig.reload()

        launch(SeleniumContext) {
            while (isActive) {
                delay(MiraiSeleniumConfig.destroy * 60_000L)
                logger.info { "DriverCache: $DriverCache" }
                destroy()
            }
        }
    }

    override fun onDisable() {
        destroy(enable = false)
        clear()
    }
}
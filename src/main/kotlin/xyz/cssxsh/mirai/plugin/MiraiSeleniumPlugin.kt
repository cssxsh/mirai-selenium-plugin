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
        version = "2.0.0-M1",
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
        if (!flush && installed) return true

        MiraiSeleniumConfig.reload()
        installed = false
        val folder = dataFolder.resolve("selenium")
        System.setProperty(SELENIUM_FOLDER, folder.absolutePath)
        try {
            setupWebDriver(browser = MiraiSeleniumConfig.browser)
            installed = true
        } catch (exception: UnsupportedOperationException) {
            logger.warning({ "浏览器 ${MiraiSeleniumConfig.browser} 不受支持" }, exception)
        } catch (cause: Throwable) {
            logger.warning({ "初始化浏览器驱动失败" }, cause)
        }

        return installed
    }

    /**
     * 创建一个 RemoteWebDriver
     * @param config 配置
     * @see RemoteWebDriver
     */
    fun driver(config: RemoteWebDriverConfig = MiraiSeleniumConfig) = RemoteWebDriver(config)

    @OptIn(ConsoleExperimentalApi::class)
    override fun PluginComponentStorage.onLoad() {
        KtorContext = childScopeContext(name = "Selenium", context = Dispatchers.IO)
        SeleniumLogger.level = Level.OFF
    }

    override fun onEnable() {
        MiraiSeleniumConfig.reload()
    }
}
package xyz.cssxsh.mirai.plugin

import io.github.karlatemp.mxlib.*
import io.github.karlatemp.mxlib.exception.*
import io.github.karlatemp.mxlib.logger.*
import kotlinx.coroutines.*
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScopeContext
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.selenium.*

object MiraiSeleniumPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "xyz.cssxsh.mirai.plugin.mirai-selenium-plugin",
        name = "mirai-selenium-plugin",
        version = "1.0.5",
    ) {
        author("cssxsh")
    }
) {

    private var installed = false

    /**
     * 初始化 Selenium
     *
     * 如果 是 [io.github.karlatemp.mxlib.selenium.MxSelenium] 不支持的环境，
     * 请自行实现 初始化方法(判断浏览器类型，下载驱动，配置路径)
     *
     * @see [setupSelenium]
     */
    fun setup(flush: Boolean = false): Boolean = synchronized(this) {
        if (!flush && installed) return true

        MiraiSeleniumConfig.reload()
        installed = false

        try {
            setupSelenium(browser = MiraiSeleniumConfig.browser, factory = MiraiSeleniumConfig.factory)
            installed = true
        } catch (exception: UnsupportedOperationException) {
            logger.warning({ "浏览器不受支持 $exception" }, exception)
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
    fun driver(config: RemoteWebDriverConfig = MiraiSeleniumConfig) = RemoteWebDriver(config)

    @OptIn(ConsoleExperimentalApi::class)
    override fun PluginComponentStorage.onLoad() {
        KtorContext = childScopeContext(name = "SeleniumHttpClient", context = Dispatchers.IO)
        try {
            MxLib.setLoggerFactory { name -> NopLogger(name) }
            MxLib.setDataStorage(dataFolder)
        } catch (_: ValueInitializedException) {
            //
        }
    }

    override fun onEnable() {
        MiraiSeleniumConfig.reload()
    }
}
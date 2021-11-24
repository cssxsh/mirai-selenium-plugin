package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.plugin.jvm.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.selenium.*

internal class MiraiSeleniumPluginTest :
    KotlinPlugin(JvmPluginDescription(id = "", name = "mirai-selenium-plugin", version = "0.0.0")) {

    /**
     * 如果加载成功则为真
     */
    val selenium: Boolean by lazy { MiraiSeleniumPlugin.setup() }

    override fun onEnable() {
        if (selenium) {
            /**
             * 使用 [MiraiSeleniumPlugin] 的默认设置 [MiraiSeleniumConfig]
             */
            driver = MiraiSeleniumPlugin.driver()

            /**
             * 使用 自定义的 的设置 [SeleniumConfig], 实现 [RemoteWebDriverConfig]
             *
             * 注意，[SeleniumConfig] 不要出现在 if (selenium) { } 外面，
             * 否则 [MiraiSeleniumPlugin] 未安装时会出现 [NoClassDefFoundError]
             */
            SeleniumConfig.reload()
            driver = MiraiSeleniumPlugin.driver(config = SeleniumConfig)


            runBlocking(coroutineContext) {

                val screenshot = driver.getScreenshot(url = "https://mirai.mamoe.net/")
            }
        }
    }

    override fun onDisable() {
        if (selenium) {
            /**
             * 关闭 [RemoteWebDriver]
             */
            driver.quit()
        }
    }
}
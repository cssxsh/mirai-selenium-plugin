package xyz.cssxsh.mirai.test

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.utils.warning
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.selenium.MiraiSeleniumPlugin
import xyz.cssxsh.mirai.selenium.data.*
import xyz.cssxsh.selenium.*

internal class MiraiSeleniumPluginTest :
    KotlinPlugin(
        JvmPluginDescription(
            id = "xyz.cssxsh.mirai.plugin.mirai-selenium-plugin-test",
            name = "mirai-selenium-test",
            version = "0.0.0"
        ) {
            dependsOn("xyz.cssxsh.mirai.plugin.mirai-selenium-plugin", true)
        }) {

    /**
     * 如果加载成功则为真
     */
    val selenium: Boolean by lazy {
        try {
            MiraiSeleniumPlugin.setup()
        } catch (exception: NoClassDefFoundError) {
            logger.warning { "相关类加载失败，请安装 https://github.com/cssxsh/mirai-selenium-plugin $exception" }
            false
        }
    }

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


            /**
             * 即用即关
             */
            useRemoteWebDriver(SeleniumConfig) { browser ->
                browser.get("https://mirai.mamoe.net/")
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
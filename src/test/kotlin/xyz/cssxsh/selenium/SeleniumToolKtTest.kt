package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.plugin.data.MiraiSeleniumConfig
import java.io.*

internal class SeleniumToolKtTest {

    private val folder = File("run")

    private val browsers = listOf("Chrome", "Edge", "Firefox")

    private val config = object : RemoteWebDriverConfig by MiraiSeleniumConfig {
        override val userAgent: String = UserAgents.IPAD + " MicroMessenger"
        override val headless: Boolean = true
        override val proxy: String = ""
        override val browser: String = ""
        override val factory: String = "netty"
    }

    private fun useRemoteWebDriver(block: suspend CoroutineScope.(String, RemoteWebDriver) -> Unit) {
        for (browser in browsers) {
            setupSelenium(folder = folder, browser = browser, factory = config.factory)
            runBlocking(KtorContext) {
                var driver: RemoteWebDriver? = null
                try {
                    driver = RemoteWebDriver(config = config)
                    block(browser, driver)
                } catch (cause: Throwable) {
                    cause.printStackTrace()
                } finally {
                    driver?.quit()
                }
            }
        }
    }

    @Test
    fun screenshot(): Unit = useRemoteWebDriver { browser, driver ->

        val url = "https://t.bilibili.com/h5/dynamic/detail/450055453856015371"

        val hide = arrayOf(".open-app", ".launch-app-btn", ".unlogin-popover", ".no-login")

        val screenshot = driver.getScreenshot(url = url, hide = hide)

        folder.resolve("screenshot.${browser.lowercase()}.png").writeBytes(screenshot)
    }

    @Test
    fun pdf(): Unit = useRemoteWebDriver { browser, driver ->

        driver.get("https://hub.fastgit.org/mamoe/mirai/blob/dev/README.md")

        val start = System.currentTimeMillis()
        while (isActive) {
            if (driver.isReady()) break
            if (System.currentTimeMillis() - start > 180_000) break
            delay(10_000)
        }

        val pdf = driver.printToPDF()
        folder.resolve("print.${browser.lowercase()}.pdf").writeBytes(pdf)
    }
}
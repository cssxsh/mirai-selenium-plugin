package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import java.util.concurrent.*

internal class SeleniumToolKtTest : SeleniumTest() {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun screenshot(): Unit = testRemoteWebDriver { browser, driver ->

        val url = "https://t.bilibili.com/761278379279253527"

        val hide = arrayOf(".open-app", ".launch-app-btn", ".unlogin-popover", ".no-login")

        val screenshot = driver.getScreenshot(url = url, hide = hide)

        folder.resolve("screenshot.${browser.lowercase()}.png").writeBytes(screenshot)
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun pdf(): Unit = testRemoteWebDriver { browser, driver ->

        driver.get("https://github.com/mamoe/mirai/blob/dev/README.md")

        val start = System.currentTimeMillis()
        while (isActive) {
            if (driver.isReady()) break
            if (System.currentTimeMillis() - start > 60_000) {
                System.err.println("$browser pdf ready timeout.")
                break
            }
            delay(10_000)
        }

        val pdf = driver.printToPDF()
        folder.resolve("print.${browser.lowercase()}.pdf").writeBytes(pdf)
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun firefox() {
        try {
            setupFirefox(folder = folder, version = "")
        } catch (cause: RuntimeException) {
            logger.warn("...", cause)
        }
        val driver = FirefoxDriver(config = object : RemoteWebDriverConfig {
            override val headless: Boolean = true
            override val log: Boolean = true
            override val factory: String = "netty"
        })

        try {
            driver.get("about:config")
            println(driver.devTools.session())
        } catch (cause: Throwable) {
            logger.warn("firefox", cause)
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun chromium() {
        try {
            setupChromium(folder = folder, version = "")
        } catch (cause: RuntimeException) {
            logger.warn("...", cause)
        }
        val driver = ChromeDriver(config = object : RemoteWebDriverConfig {
            override val headless: Boolean = true
            override val log: Boolean = true
            override val factory: String = "netty"
        }, chromium = true)

        try {
            driver.get("chrome://settings/help")
        } catch (cause: Throwable) {
            logger.warn("chromium", cause)
        }
    }
}
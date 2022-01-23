package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.openqa.selenium.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.plugin.data.*
import java.io.*
import java.util.concurrent.*

internal class SeleniumToolKtTest {

    private val folder = File("run")

    init {
        System.setProperty(SELENIUM_FOLDER, folder.resolve("selenium").apply { mkdirs() }.absolutePath)
    }

    private val browsers by lazy {
        val platform = Platform.getCurrent()
        when {
            platform.`is`(Platform.WINDOWS) -> listOf("Edge", "Chromium", "Firefox")
            platform.`is`(Platform.LINUX) -> listOf("Chromium", "Firefox")
            platform.`is`(Platform.MAC) -> listOf("Chromium")
            else -> throw UnsupportedOperationException("不受支持的平台 $platform")
        }
    }

    private val config = object : RemoteWebDriverConfig by MiraiSeleniumConfig {
        override val userAgent: String = UserAgents.IPAD + " MicroMessenger"
        override val headless: Boolean = true
        override val log: Boolean = true
        override val factory: String = "netty"
    }

    private fun testRemoteWebDriver(block: suspend CoroutineScope.(String, RemoteWebDriver) -> Unit) {
        for (browser in browsers) {
            runBlocking(SeleniumContext) {
                val driver = setupWebDriver(browser = browser).invoke(config)
                try {
                    block(browser, driver)
                } catch (cause: Throwable) {
                    cause.printStackTrace()
                } finally {
                    driver.quit()
                }
            }
        }
    }

    @AfterEach
    fun destroy() {
        DriverCache.forEach { (driver, service) ->
            try {
                driver.quit()
            } catch (_: Throwable) {
                //
            }
            try {
                service.stop()
            } catch (_: Throwable) {
                //
            }
        }
        DriverCache.clear()
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun screenshot(): Unit = testRemoteWebDriver { browser, driver ->

        val url = "https://t.bilibili.com/h5/dynamic/detail/450055453856015371"

        val hide = arrayOf(".open-app", ".launch-app-btn", ".unlogin-popover", ".no-login")

        val screenshot = driver.getScreenshot(url = url, hide = hide)

        folder.resolve("screenshot.${browser.lowercase()}.png").writeBytes(screenshot)
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun pdf(): Unit = testRemoteWebDriver { browser, driver ->

        driver.get("https://github.com/mamoe/mirai/blob/dev/README.md")

        val start = System.currentTimeMillis()
        while (isActive) {
            if (driver.isReady()) break
            if (System.currentTimeMillis() - start > 180_000) break
            delay(10_000)
        }

        val pdf = driver.printToPDF()
        folder.resolve("print.${browser.lowercase()}.pdf").writeBytes(pdf)
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun firefox(): Unit = runBlocking {
        setupFirefox(folder = folder, version = "")
        val driver = RemoteWebDriver(config = object : RemoteWebDriverConfig {
            override val browser: String = "firefox"
            override val headless: Boolean = true
            override val log: Boolean = true
            override val factory: String = "netty"
        })

        try {
            driver.get("about:config")
        } catch (cause: Throwable) {
            cause.printStackTrace()
        }
    }
}
package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.plugin.data.*
import java.io.*

internal class SeleniumToolKtTest {

    private val folder = File("run")

    init {
        System.setProperty(SELENIUM_FOLDER, folder.resolve("selenium").apply { mkdirs() }.absolutePath)
    }

    private val browsers by lazy {
        when (OperatingSystem.current) {
            OperatingSystem.Windows -> listOf("Edge", "Chromium", "Firefox")
            OperatingSystem.Linux -> listOf("Chromium", "Firefox")
            OperatingSystem.Mac -> listOf("Chromium", "Firefox")
        }
    }

    private val config = object : RemoteWebDriverConfig by MiraiSeleniumConfig {
        override val userAgent: String = UserAgents.IPAD + " MicroMessenger"
        override val headless: Boolean = true
        override val proxy: String = ""
        override val browser: String = ""
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
        DriverCache.forEach { (_, service) ->
            try {
                service.stop()
            } catch (_: Throwable) {
                //
            }
        }
        DriverCache.clear()
    }

    @Test
    fun screenshot(): Unit = testRemoteWebDriver { browser, driver ->

        val url = "https://t.bilibili.com/h5/dynamic/detail/450055453856015371"

        val hide = arrayOf(".open-app", ".launch-app-btn", ".unlogin-popover", ".no-login")

        val screenshot = driver.getScreenshot(url = url, hide = hide)

        folder.resolve("screenshot.${browser.lowercase()}.png").writeBytes(screenshot)
    }

    @Test
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
    fun browser(): Unit = runBlocking {
        // chrome: chrome://prefs-internals/
        // firefox about:config
        try {
            setupFirefox(folder = folder, version = "68.0.1esr")
        } catch (_: IllegalStateException) {
            //
        }
        val driver = RemoteWebDriver(config = object : RemoteWebDriverConfig {
            override val browser: String = "firefox"
            override val headless: Boolean = false
            override val log: Boolean = true
            override val factory: String = "netty"
            override val preferences: Map<String, String> = mapOf(
                "network.security.esni.enabled" to "true",
                "network.captive-portal-service.enabled" to "false",
                "network.proxy.no_proxies_on" to
                    listOf(".mozilla.org", ".firefox.com", ".digicert.com", ".mozilla.com", ".amazontrust.com").joinToString()
            )
            override val proxy: String = "http://127.0.0.1:8080"
        })

        try {
            driver.get("about:config")
        } catch (cause: Throwable) {
            cause.printStackTrace()
        }

        delay(30_000)
    }
}
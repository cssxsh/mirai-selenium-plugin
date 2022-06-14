package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.openqa.selenium.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.selenium.data.*
import java.io.File
import java.util.logging.*

internal abstract class SeleniumTest {

    protected val folder = File("run")

    init {
        System.setProperty(SELENIUM_FOLDER, folder.resolve("selenium").apply { mkdirs() }.absolutePath)
        System.setProperty(CHROME_DRIVER_MIRRORS, "https://npm.taobao.org/mirrors/chromedriver")
        System.setProperty(FIREFOX_DRIVER_MIRRORS, "https://npm.taobao.org/mirrors/geckodriver")
        System.setProperty(SEVEN7Z_MIRRORS, "https://downloads.sourceforge.net/sevenzip")
        // System.setProperty("selenium.webdriver.verbose", "true")
        SeleniumLogger.level = Level.WARNING
        Class.forName("org.openqa.selenium.remote.http.HttpClient\$Factory", true, this::class.java.classLoader)
    }

    protected val browsers by lazy {
        val platform = Platform.getCurrent()
        when {
            platform.`is`(Platform.WIN10) -> listOf("Edge", "Chromium", "Firefox")
            platform.`is`(Platform.WINDOWS) -> listOf("Chromium", "Firefox")
            platform.`is`(Platform.LINUX) -> listOf("Chromium", "Firefox")
            platform.`is`(Platform.MAC) -> listOf("Chromium", "Firefox")
            else -> throw UnsupportedOperationException("不受支持的平台 $platform")
        }
    }

    protected val isPC by lazy { Platform.getCurrent().`is`(Platform.WIN10) }

    protected val config = object : RemoteWebDriverConfig by MiraiSeleniumConfig {
        override val userAgent: String = UserAgents.IPAD + " MicroMessenger"
        override val headless: Boolean = !isPC
        override val log: Boolean = true
        override val factory: String = "netty"
    }

    protected fun testRemoteWebDriver(block: suspend CoroutineScope.(String, RemoteWebDriver) -> Unit) {
        if (!isPC) {
            setupChromium(folder = folder, version = "102")
        }
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
        println(DriverCache.status())
        DriverCache.forEach { (driver, service) ->
            try {
                driver.quit()
            } catch (cause: Throwable) {
                cause.printStackTrace()
            }
            try {
                service.stop()
            } catch (cause: Throwable) {
                cause.printStackTrace()
            }
        }
        DriverCache.clear()
    }
}
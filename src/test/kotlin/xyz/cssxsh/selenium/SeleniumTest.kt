package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.openqa.selenium.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.selenium.data.*
import java.io.File
import java.util.logging.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class SeleniumTest {

    protected val folder = File("run")

    init {
        System.setProperty(SELENIUM_FOLDER, folder.resolve("selenium").apply { mkdirs() }.absolutePath)
        System.setProperty(CHROME_DRIVER_MIRRORS, "https://npm.taobao.org/mirrors/chromedriver")
        System.setProperty(FIREFOX_DRIVER_MIRRORS, "https://npm.taobao.org/mirrors/geckodriver")
        System.setProperty(SEVEN7Z_MIRRORS, "https://downloads.sourceforge.net/sevenzip")
        // System.setProperty("selenium.webdriver.verbose", "true")
        SeleniumLogger.level = Level.WARNING
    }

    protected val browsers by lazy {
        val platform = Platform.getCurrent()
        when {
            platform.`is`(Platform.WIN10) -> listOf("Edge", "Chrome", "Firefox")
            platform.`is`(Platform.WINDOWS) -> listOf("Chromium", "Firefox")
            platform.`is`(Platform.LINUX) -> listOf("Chromium", "Firefox")
            platform.`is`(Platform.MAC) -> listOf("Chromium", "Firefox")
            else -> throw UnsupportedOperationException("不受支持的平台 $platform")
        }
    }

    protected val isPC = Platform.getCurrent().`is`(Platform.WIN10)

    protected val config = object : RemoteWebDriverConfig by MiraiSeleniumConfig {
        override val userAgent: String = UserAgents.IPAD + " MicroMessenger"
        override val headless: Boolean = true
        override val log: Boolean = true
        override val factory: String = "netty"
    }

    protected fun testRemoteWebDriver(block: suspend CoroutineScope.(String, RemoteWebDriver) -> Unit) {
        for (browser in browsers) {
            runBlocking(SeleniumContext) {
                val driver = setupWebDriver(browser = browser).invoke(config)
                try {
                    block(browser, driver)
                } catch (cause: WebDriverException) {
                    cause.printStackTrace()
                } finally {
                    driver.quit()
                }
            }
        }
    }

    @BeforeAll
    fun setup() {
        if (!isPC) {
            setupChromium(folder = folder, version = "")
        }
    }

    @AfterEach
    fun destroy() {
        println(DriverCache.status())
        DriverCache.destroy(enable = false) { driver, service ->
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
    }
}
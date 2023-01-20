package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.edge.EdgeDriverService
import org.openqa.selenium.firefox.GeckoDriverService
import org.openqa.selenium.remote.*
import org.slf4j.*
import xyz.cssxsh.mirai.selenium.data.*
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class SeleniumTest {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected val folder = File("run")

    init {
        System.setProperty(SELENIUM_FOLDER, folder.resolve("selenium").apply { mkdirs() }.absolutePath)
        System.setProperty(CHROME_DRIVER_MIRRORS, "https://npm.taobao.org/mirrors/chromedriver")
        System.setProperty(FIREFOX_DRIVER_MIRRORS, "https://npm.taobao.org/mirrors/geckodriver")
        System.setProperty(SEVEN7Z_MIRRORS, "https://downloads.sourceforge.net/sevenzip")
        // System.setProperty("selenium.webdriver.verbose", "true")
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger()
        org.slf4j.bridge.SLF4JBridgeHandler.install()
    }

    protected val browsers by lazy {
        val platform = Platform.getCurrent()
        when {
            platform.`is`(Platform.WIN10) -> listOf("Edge", "Chrome", "Firefox")
            platform.`is`(Platform.WINDOWS) -> {
                System.setProperty(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY, System.getenv("EDGEWEBDRIVER") + "/msedgedriver.exe")
                System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, System.getenv("CHROMEWEBDRIVER") + "/chromedriver.exe")
                System.setProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY, System.getenv("GECKOWEBDRIVER") + "/geckodriver.exe")
                listOf("Edge", "Chromium", "Firefox")
            }
            platform.`is`(Platform.LINUX) -> {
                System.setProperty(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY, System.getenv("EDGEWEBDRIVER") + "/msedgedriver")
                System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, System.getenv("CHROMEWEBDRIVER") + "/chromedriver")
                System.setProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY, System.getenv("GECKOWEBDRIVER") + "/geckodriver")
                listOf("Edge", "Chromium", "Firefox")
            }
            platform.`is`(Platform.MAC) -> {
                System.setProperty(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY, System.getenv("EDGEWEBDRIVER") + "/msedgedriver")
                System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, System.getenv("CHROMEWEBDRIVER") + "/chromedriver")
                System.setProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY, System.getenv("GECKOWEBDRIVER") + "/geckodriver")
                listOf("Edge", "Chromium", "Firefox")
            }
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
            val driver = setupWebDriver(browser = browser).invoke(config)
            try {
                runBlocking {
                    block(browser, driver)
                }
            } catch (cause: WebDriverException) {
                cause.printStackTrace()
            } finally {
                driver.quit()
            }
        }
    }

    @AfterEach
    fun destroy() {
        println(DriverCache.status())
        DriverCache.destroy(enable = false) { driver, service ->
            val process = service.getProcess()
            try {
                driver.quit()
            } catch (cause: Throwable) {
                logger.warn("Driver ${service.url}", cause)
            }
            try {
                service.stop()
            } catch (cause: Throwable) {
                logger.warn("Service ${service.url}", cause)
            }
            try {
                process?.destroy()
            } catch (cause: Throwable) {
                logger.warn("Process ${service.url}", cause)
            }
            true
        }
    }
}
package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.openqa.selenium.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.plugin.data.*
import java.io.*
import java.util.concurrent.*
import java.util.logging.*

internal open class SeleniumToolKtTest {

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

    protected val config = object : RemoteWebDriverConfig by MiraiSeleniumConfig {
        override val userAgent: String = UserAgents.IPAD + " MicroMessenger"
        override val headless: Boolean = true
        override val log: Boolean = true
        override val factory: String = "ktor"
    }

    protected fun testRemoteWebDriver(block: suspend CoroutineScope.(String, RemoteWebDriver) -> Unit) {
        setupChromium(folder = folder, version = "98")
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
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun firefox() {
        setupFirefox(folder = folder, version = "")
        val driver = FirefoxDriver(config = object : RemoteWebDriverConfig {
            override val headless: Boolean = true
            override val log: Boolean = true
            override val factory: String = "ktor"
        })

        try {
            driver.get("about:config")
            println(driver.devTools.session())
        } catch (cause: Throwable) {
            cause.printStackTrace()
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun chromium() {
        setupChromium(folder = folder, version = "98")
        val driver = ChromeDriver(config = object : RemoteWebDriverConfig {
            override val headless: Boolean = true
            override val log: Boolean = true
            override val factory: String = "netty"
        }, chromium = true)

        try {
            driver.get("chrome://settings/help")
        } catch (cause: Throwable) {
            cause.printStackTrace()
        }
    }
}
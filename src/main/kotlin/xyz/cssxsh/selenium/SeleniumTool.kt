package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import me.him188.kotlin.jvm.blocking.bridge.*
import org.openqa.selenium.*
import org.openqa.selenium.chrome.*
import org.openqa.selenium.devtools.*
import org.openqa.selenium.edge.*
import org.openqa.selenium.firefox.*
import org.openqa.selenium.print.*
import org.openqa.selenium.remote.*
import org.openqa.selenium.remote.service.*
import java.time.*
import java.util.*
import java.util.logging.*

// region Selenium

/**
 * @see org.openqa.selenium.chromium.ChromiumDriver
 * @see org.openqa.selenium.devtools.CdpVersionFinder
 * @see org.openqa.selenium.devtools.CdpEndpointFinder
 * @see org.openqa.selenium.devtools.Connection
 * @see org.openqa.selenium.devtools.idealized.Network
 * @see org.openqa.selenium.devtools.v95.V95Network
 * @see org.openqa.selenium.remote.ErrorCodes
 * @see org.openqa.selenium.remote.ProtocolHandshake
 * @see org.openqa.selenium.remote.RemoteLogs
 * @see org.openqa.selenium.remote.RemoteWebDriver
 * @see org.openqa.selenium.remote.codec.w3c.W3CHttpResponseCodec
 * @see org.openqa.selenium.remote.http.netty.NettyWebSocket
 * @see org.openqa.selenium.net.UrlChecker
 * @see org.openqa.selenium.json.JsonOutput
 * @see org.openqa.selenium.os.OsProcess
 */
internal val SeleniumLogger: Logger = Logger.getLogger("org.openqa.selenium")

internal var SeleniumContext = Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { context, cause ->
    SeleniumLogger.log(Level.WARNING, "Exception in coroutine ${context[CoroutineName]?.name ?: "Selenium"}", cause)
}

// endregion

// region RemoteWebDriver

internal fun DriverService.getProcess(): Process? {
    val process = DriverService::class.java.getDeclaredField("process")
        .apply { isAccessible = true }.get(this) ?: return null
    val osProcess = process::class.java.getDeclaredField("process")
        .apply { isAccessible = true }.get(process) ?: return null
    val watchdog = osProcess::class.java.getDeclaredField("executeWatchdog")
        .apply { isAccessible = true }.get(osProcess) ?: return null
    return watchdog::class.java.getDeclaredField("process")
        .apply { isAccessible = true }.get(watchdog) as Process?
}

internal const val SELENIUM_TIMEOUT_INIT = "xyz.cssxsh.selenium.timeout.init"

internal const val SELENIUM_TIMEOUT_PAGE = "xyz.cssxsh.selenium.timeout.page"

internal const val SELENIUM_TIMEOUT_INTERVAL = "xyz.cssxsh.selenium.timeout.interval"

private val Init: Duration by lazy {
    Duration.ofMillis(System.getProperty(SELENIUM_TIMEOUT_INIT, "10000").toLong())
}

private val PageLoad: Duration by lazy {
    Duration.ofMillis(System.getProperty(SELENIUM_TIMEOUT_PAGE, "180000").toLong())
}

private val Interval: Duration by lazy {
    Duration.ofMillis(System.getProperty(SELENIUM_TIMEOUT_INTERVAL, "10000").toLong())
}

/**
 * 创建一个 RemoteWebDriver
 * @param config 配置
 */
public fun RemoteWebDriver(config: RemoteWebDriverConfig): RemoteWebDriver {
    return setupWebDriver(browser = config.browser).invoke(config)
}

/**
 * 创建一个 EdgeDriver
 * @param config 配置
 */
public fun EdgeDriver(config: RemoteWebDriverConfig): EdgeDriver {
    return setupWebDriver(browser = "edge").invoke(config) as EdgeDriver
}

/**
 * 创建一个 ChromeDriver
 * @param config 配置
 */
public fun ChromeDriver(config: RemoteWebDriverConfig, chromium: Boolean): ChromeDriver {
    return setupWebDriver(browser = if (chromium) "chromium" else "chrome").invoke(config) as ChromeDriver
}

/**
 * 创建一个 FirefoxDriver
 * @param config 配置
 */
public fun FirefoxDriver(config: RemoteWebDriverConfig): FirefoxDriver {
    return setupWebDriver(browser = "firefox").invoke(config) as FirefoxDriver
}

// endregion

// region Screenshot

public inline fun <reified T> useRemoteWebDriver(config: RemoteWebDriverConfig, block: (RemoteWebDriver) -> T): T {
    val driver = RemoteWebDriver(config)
    return try {
        block(driver)
    } finally {
        driver.quit()
    }
}

/**
 * 判断页面是否加载完全
 */
public fun RemoteWebDriver.isReady(): Boolean {
    return executeScript(
        """
        function imagesComplete() {
            const images = document.getElementsByTagName('img');
            let complete = true;
            let count = 0;
            try {
                for (const image of images) {
                    complete = complete && image.complete;
                    image.complete && count++;
                }
            } finally {
                console.log(`ImagesComplete: ${'$'}{count}/${'$'}{images.length}`);
            }
            return complete;
        }
        return document.readyState === 'complete' && imagesComplete()
    """.trimIndent()
    ) as Boolean
}

/**
 * 隐藏指定 css 过滤器的 WebElement
 * @param css CSS过滤器
 */
public fun RemoteWebDriver.hide(vararg css: String): List<RemoteWebElement> {
    if (css.isEmpty()) return emptyList()
    @Suppress("UNCHECKED_CAST")
    return executeScript(
        """
        const nodes = Array.from(arguments).flatMap((selector) => Array.from(document.querySelectorAll(selector)));
        for (const node of nodes) node.style.display = 'none';
        return nodes;
    """.trimIndent(), *css
    ) as ArrayList<RemoteWebElement>
}

/**
 * 打开指定 url 页面，并截取图片
 * @param hide CSS过滤器
 * @return 返回的图片文件数据，格式PNG
 */
@JvmBlockingBridge
public suspend fun RemoteWebDriver.getScreenshot(url: String, vararg hide: String): ByteArray {
    val home = windowHandle
    val tab = switchTo().newWindow(WindowType.TAB) as RemoteWebDriver

    try {
        withTimeout(PageLoad.toMillis()) {
            tab.get(url)
            delay(Init.toMillis())
            while (!isReady()) {
                delay(Interval.toMillis())
            }
        }
    } catch (_: TimeoutCancellationException) {
        // ignore
    } catch (_: DevToolsException) {
        // ignore
    }

    return try {
        tab.hide(css = hide)
        tab.getScreenshotAs(OutputType.BYTES)
    } finally {
        tab.close()
        switchTo().window(home)
    }
}

/**
 * 将当前页面打印为PDF
 */
public fun RemoteWebDriver.printToPDF(consumer: PrintOptions.() -> Unit = {}): ByteArray {
    val pdf = print(PrintOptions().apply(consumer))
    return Base64.getMimeDecoder().decode(pdf.content)
}

// endregion
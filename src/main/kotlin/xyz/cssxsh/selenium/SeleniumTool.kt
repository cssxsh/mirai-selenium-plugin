package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import org.openqa.selenium.*
import org.openqa.selenium.print.*
import org.openqa.selenium.remote.*
import org.openqa.selenium.remote.service.*
import java.time.*
import java.util.*
import java.util.logging.*
import kotlin.coroutines.*

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

internal var SeleniumContext: CoroutineContext = Dispatchers.IO + SupervisorJob()

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
    Duration.ofMillis(System.getProperty(SELENIUM_TIMEOUT_INIT)?.toLongOrNull() ?: 10_000)
}

private val PageLoad: Duration by lazy {
    Duration.ofMillis(System.getProperty(SELENIUM_TIMEOUT_PAGE)?.toLongOrNull() ?: 180_000)
}

private val Interval: Duration by lazy {
    Duration.ofMillis(System.getProperty(SELENIUM_TIMEOUT_INTERVAL)?.toLongOrNull() ?: 10_000)
}

/**
 * 创建一个 RemoteWebDriver
 * @param config 配置
 */
fun RemoteWebDriver(config: RemoteWebDriverConfig) = setupWebDriver(browser = config.browser).invoke(config)

// endregion

// region Screenshot

inline fun <reified T> useRemoteWebDriver(config: RemoteWebDriverConfig, block: (RemoteWebDriver) -> T): T {
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
fun RemoteWebDriver.isReady(): Boolean {
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
fun RemoteWebDriver.hide(vararg css: String): List<RemoteWebElement> {
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
suspend fun RemoteWebDriver.getScreenshot(url: String, vararg hide: String): ByteArray {
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
fun RemoteWebDriver.printToPDF(consumer: PrintOptions.() -> Unit = {}): ByteArray {
    val pdf = print(PrintOptions().apply(consumer))
    return Base64.getMimeDecoder().decode(pdf.content)
}

// endregion
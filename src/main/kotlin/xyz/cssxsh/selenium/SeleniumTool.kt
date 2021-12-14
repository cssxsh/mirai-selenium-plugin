package xyz.cssxsh.selenium

import io.github.karlatemp.mxlib.selenium.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.openqa.selenium.*
import org.openqa.selenium.chrome.*
import org.openqa.selenium.chromium.*
import org.openqa.selenium.edge.*
import org.openqa.selenium.firefox.*
import org.openqa.selenium.print.*
import org.openqa.selenium.remote.*
import java.io.*
import java.time.*
import java.util.*
import java.util.function.*
import java.util.logging.*
import java.util.zip.*
import kotlin.properties.*
import kotlin.reflect.*

// region Setup Selenium

private object AllIgnoredOutputStream : OutputStream() {
    override fun close() {}
    override fun write(b: ByteArray, off: Int, len: Int) {}
    override fun write(b: ByteArray) {}
    override fun write(b: Int) {}
    override fun flush() {}
}

private inline fun <reified T : Any, reified R> reflect() = object : ReadWriteProperty<T, R> {

    override fun getValue(thisRef: T, property: KProperty<*>): R {
        return T::class.java.getDeclaredField(property.name).apply { isAccessible = true }.get(thisRef) as R
    }

    override fun setValue(thisRef: T, property: KProperty<*>, value: R) {
        T::class.java.getDeclaredField(property.name).apply { isAccessible = true }.set(thisRef, value)
    }
}

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
internal val logger: Logger = Logger.getLogger("org.openqa.selenium")

internal const val USER_CHOICE_KEY =
    "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\Shell\\Associations\\URLAssociations\\https\\UserChoice"

internal fun queryUserChoice(): String {
    if (System.getProperty("os.name").startsWith("Windows").not()) return ""
    return ProcessBuilder("reg", "query", USER_CHOICE_KEY, "/v", "ProgId").start()
        .inputStream.use { it.reader().readText() }
        .substringAfter("REG_SZ").trim()
}

internal const val EDGE_APPLICATION = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application"

internal const val CHROME_APPLICATION = "C:\\Program Files (x86)\\Google\\Chrome\\Application"

internal val VERSION = """\d+(.\d+)*""".toRegex()

internal val ZIP_URL = "(?<=<Url>).{16,256}zip".toRegex()

internal typealias DriverSupplier = BiFunction<String?, Consumer<Capabilities>?, RemoteWebDriver>

private val MxSeleniumInstance by lazy { MxSelenium() }

private var MxSelenium.initialized: Boolean by reflect()

private var MxSelenium.driverClass: Class<out RemoteWebDriver> by reflect()

private var MxSelenium.driverSupplier: DriverSupplier by reflect()

private val MxSelenium.data: File by reflect()

/**
 * Only Windows
 */
internal fun setupEdgeDriver() {
    val version = requireNotNull(File(EDGE_APPLICATION).list()?.firstOrNull { it matches VERSION }) { "Edge 版本获取失败" }
    val client = HttpClient(OkHttp)

    val xml = MxSeleniumInstance.data.resolve("msedgedriver-${version}.xml")
    if (xml.exists().not()) {
        xml.writeBytes(runBlocking(KtorContext) {
            client.get("https://msedgewebdriverstorage.blob.core.windows.net/edgewebdriver") {
                parameter("prefix", version)
                parameter("comp", "list")
                parameter("timeout", 60_000)
            }
        })
    }

    val url = ZIP_URL.findAll(xml.readText()).first { "win32" in it.value }.value

    val file = MxSeleniumInstance.data.resolve("msedgedriver-${version}.zip")
    if (file.exists().not()) {
        file.writeBytes(runBlocking(KtorContext) {
            client.get(url)
        })
    }

    val driver = MxSeleniumInstance.data.resolve("msedgedriver-${version}.exe")
    if (driver.exists().not()) {
        val zip = ZipFile(file)
        zip.getInputStream(zip.getEntry("msedgedriver.exe")).use { input ->
            driver.writeBytes(input.readAllBytes())
        }
    }

    System.setProperty(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY, driver.absolutePath)

    setMxSelenium(EdgeDriver::class.java) { agent, consumer ->
        val options = EdgeOptions()
        if (agent != null) options.addArguments("user-agent=$agent")
        consumer?.accept(options)
        val service = EdgeDriverService.createDefaultService()
        service.sendOutputTo(AllIgnoredOutputStream)
        EdgeDriver(service, options)
    }
}

internal fun setMxSelenium(driverClass: Class<out RemoteWebDriver>, driverSupplier: DriverSupplier) {
    MxSeleniumInstance.initialized = true
    MxSeleniumInstance.driverClass = driverClass
    MxSeleniumInstance.driverSupplier = driverSupplier
}

/**
 * 初始化 Selenium/MxLib 配置
 * @param browser 浏览器类型 Chrome, Firefox ...
 * @param factory [org.openqa.selenium.remote.http.HttpClient.Factory] , ktor, netty
 * @see setupEdgeDriver
 */
internal fun setupSelenium(browser: String = "", factory: String = "ktor") {

    logger.level = Level.OFF

    if (factory == "ktor") {
        System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    }
    if (MxSeleniumInstance.initialized) {
        MxSeleniumInstance.initialized = false
    }

    if (browser.isNotBlank()) System.setProperty("mxlib.selenium.browser", browser)
    if (factory.isNotBlank()) System.setProperty("webdriver.http.factory", factory)

    if (browser.startsWith("Edge") || queryUserChoice().startsWith("Edge")) {
        setupEdgeDriver()
        return
    }

    /**
     * 切换线程上下文，加载相关配置
     */
    val thread = Thread.currentThread()
    val oc = thread.contextClassLoader
    try {
        thread.contextClassLoader = KtorHttpClient.Factory::class.java.classLoader
        MxSelenium.initialize()
    } finally {
        thread.contextClassLoader = oc
    }

    if (MxSeleniumInstance.driverClass == FirefoxDriver::class.java) {
        setMxSelenium(FirefoxDriver::class.java) { agent, consumer ->
            val options = FirefoxOptions()
            if (agent != null) options.addPreference("general.useragent.override", agent)
            consumer?.accept(options)
            val service = GeckoDriverService.Builder().usingFirefoxBinary(options.binary).build()
            service.sendOutputTo(AllIgnoredOutputStream)
            FirefoxDriver(service, options)
        }
    }

    if (MxSeleniumInstance.driverClass == ChromeDriver::class.java) {
        setMxSelenium(ChromeDriver::class.java) { agent, consumer ->
            val options = ChromeOptions()
            if (agent != null) options.addArguments("user-agent=$agent")
            consumer?.accept(options)
            val service = ChromeDriverService.createServiceWithConfig(options)
            service.sendOutputTo(AllIgnoredOutputStream)
            ChromeDriver(service, options)
        }
    }
}

// endregion

// region RemoteWebDriver

private fun RemoteWebDriverConfig.toConsumer(): (Capabilities) -> Unit = { capabilities ->
    when (capabilities) {
        is ChromiumOptions<*> -> capabilities.apply {
            setHeadless(headless)
            setPageLoadStrategy(PageLoadStrategy.NORMAL)
            setAcceptInsecureCerts(true)
            addArguments("--silent")
            setExperimentalOption(
                "excludeSwitches",
                listOf("enable-automation", "ignore-certificate-errors")
            )
            addArguments("--hide-scrollbars")
            if (proxy.isNotBlank()) {
                addArguments("--proxy-server=${proxy}")
            }
            setExperimentalOption(
                "mobileEmulation",
                mapOf(
                    "deviceMetrics" to mapOf(
                        "width" to width,
                        "height" to height,
                        "pixelRatio" to pixelRatio
                    ),
                    "userAgent" to userAgent
                )
            )
        }
        is FirefoxOptions -> capabilities.apply {
            setHeadless(headless)
            setPageLoadStrategy(PageLoadStrategy.NORMAL)
            setLogLevel(FirefoxDriverLogLevel.FATAL)
            setAcceptInsecureCerts(true)
            if (proxy.isNotBlank()) {
                val url = Url(proxy)
                addPreference("network.proxy.type", 1)
                addPreference("network.proxy.http", url.host)
                addPreference("network.proxy.http_port", url.port)
                addPreference("network.proxy.share_proxy_settings", true)
            }

            // XXX 手动关闭 webgl
            addPreference("webgl.disabled", true)
            addPreference("devtools.responsive.touchSimulation.enabled", true)
            addPreference("devtools.responsive.viewport.width", width)
            addPreference("devtools.responsive.viewport.height", height)
            addPreference("devtools.responsive.viewport.pixelRatio", pixelRatio)
            addPreference("devtools.responsive.userAgent", userAgent)
            // XXX responsive 无法调用
            addPreference("general.useragent.override", userAgent)
            addArguments("--width=${width}", "--height=${height}")
        }
        else -> throw UnsupportedOperationException("不支持设置参数的浏览器 ${capabilities::class}")
    }
}

internal const val INIT = "xyz.cssxsh.selenium.timeout.init"

internal const val PAGE = "xyz.cssxsh.selenium.timeout.page"

internal const val INTERVAL = "xyz.cssxsh.selenium.timeout.interval"

private val Init: Duration by lazy { Duration.ofMillis(System.getProperty(INIT)?.toLongOrNull() ?: 10_000) }

private val PageLoad: Duration by lazy { Duration.ofMillis(System.getProperty(PAGE)?.toLongOrNull() ?: 180_000) }

private val Interval: Duration by lazy { Duration.ofMillis(System.getProperty(INTERVAL)?.toLongOrNull() ?: 10_000) }

/**
 * 创建一个 RemoteWebDriver
 * @param config 配置
 */
fun RemoteWebDriver(config: RemoteWebDriverConfig): RemoteWebDriver {

    if (config.log) {
        when (MxSeleniumInstance.driverClass) {
            ChromeDriver::class.java -> {
                val log = MxSeleniumInstance.data.resolve("chromedriver.log")
                System.setProperty(ChromeDriverService.CHROME_DRIVER_LOG_PROPERTY, log.absolutePath)
            }
            EdgeDriver::class.java -> {
                val log = MxSeleniumInstance.data.resolve("msedgedriver.log")
                System.setProperty(EdgeDriverService.EDGE_DRIVER_LOG_PROPERTY, log.absolutePath)
            }
            FirefoxDriver::class.java -> {
                val log = MxSeleniumInstance.data.resolve("geckodriver.log")
                System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, log.absolutePath)
            }
            else -> Unit
        }
    }

    /**
     * 切换线程上下文，加载相关配置
     */
    val thread = Thread.currentThread()
    val oc = thread.contextClassLoader

    return try {
        thread.contextClassLoader = KtorHttpClient.Factory::class.java.classLoader

        MxSelenium.newDriver(null, config.toConsumer()).apply {
            manage().timeouts().apply {
                pageLoadTimeout(PageLoad)
                scriptTimeout(Interval)
            }
        }
    } finally {
        thread.contextClassLoader = oc
    }
}

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
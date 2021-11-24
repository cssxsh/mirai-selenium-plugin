package xyz.cssxsh.selenium

import io.github.karlatemp.mxlib.*
import io.github.karlatemp.mxlib.exception.*
import io.github.karlatemp.mxlib.logger.*
import io.github.karlatemp.mxlib.selenium.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.openqa.selenium.*
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

private inline fun <reified T : Any, reified R> reflect() = object : ReadWriteProperty<T, R> {

    override fun getValue(thisRef: T, property: KProperty<*>): R {
        return T::class.java.getDeclaredField(property.name).apply { isAccessible = true }.get(thisRef) as R
    }

    override fun setValue(thisRef: T, property: KProperty<*>, value: R) {
        T::class.java.getDeclaredField(property.name).apply { isAccessible = true }.set(thisRef, value)
    }
}

internal const val USER_CHOICE_KEY =
    "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\Shell\\Associations\\URLAssociations\\https\\UserChoice"

fun queryUserChoice(): String {
    if (System.getProperty("os.name").startsWith("Windows").not()) return ""
    return ProcessBuilder("reg", "query", USER_CHOICE_KEY, "/v", "ProgId").start()
        .inputStream.use { it.reader().readText() }
        .substringAfter("REG_SZ").trim()
}

internal const val EDGE_APPLICATION = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application"

internal const val CHROME_APPLICATION = "C:\\Program Files (x86)\\Google\\Chrome\\Application"

internal val VERSION = """\d+(.\d+)*""".toRegex()

internal val ZIP_URL = "(?<=<Url>).{16,256}zip".toRegex()

typealias DriverSupplier = BiFunction<String?, Consumer<Capabilities>?, RemoteWebDriver>

private val MxSeleniumInstance by lazy { MxSelenium() }

private var MxSelenium.initialized: Boolean by reflect()

private var MxSelenium.driverClass: Class<out RemoteWebDriver> by reflect()

private var MxSelenium.driverSupplier: DriverSupplier by reflect()

/**
 * Only Windows
 */
internal fun setupEdgeDriver() {
    val version = requireNotNull(File(EDGE_APPLICATION).list()?.firstOrNull { it matches VERSION }) { "Edge 版本获取失败" }
    val data = MxLib.getDataStorage().resolve("selenium")
    val client = HttpClient(OkHttp) { install(HttpTimeout) }

    val xml = data.resolve("msedgedriver-${version}.xml")
    if (xml.exists().not()) {
        xml.writeBytes(runBlocking(KtorContext) {
            client.get("https://msedgewebdriverstorage.blob.core.windows.net/edgewebdriver") {
                parameter("prefix", version)
                parameter("comp", "list")
                parameter("timeout", 60000)
            }
        })
    }

    val url = ZIP_URL.findAll(xml.readText()).first { "win32" in it.value }.value

    val zip = data.resolve("msedgedriver-${version}.zip")
    if (zip.exists().not()) {
        zip.writeBytes(runBlocking(KtorContext) {
            client.get(url) {
                timeout {
                    socketTimeoutMillis = 60_000
                    connectTimeoutMillis = 60_000
                    requestTimeoutMillis = 180_000
                }
            }
        })
    }

    val driver = data.resolve("msedgedriver-${version}.exe")
    if (driver.exists().not()) {
        with(ZipFile(zip)) {
            getInputStream(getEntry("msedgedriver.exe")).use { input ->
                driver.writeBytes(input.readAllBytes())
            }
        }
    }

    System.setProperty(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY, driver.absolutePath)

    setMxSelenium(EdgeDriver::class.java) { agent, consumer ->
        val options = EdgeOptions()
        if (agent != null) options.addArguments("user-agent=$agent")
        consumer?.accept(options)
        EdgeDriver(options)
    }
}

internal fun setMxSelenium(driverClass: Class<out RemoteWebDriver>, driverSupplier: DriverSupplier) {
    MxSeleniumInstance.initialized = true
    MxSeleniumInstance.driverClass = driverClass
    MxSeleniumInstance.driverSupplier = driverSupplier
}

/**
 * 初始化 Selenium/MxLib 配置
 * @param folder 数据缓存文件夹
 * @param browser 浏览器类型 Chrome, Firefox ...
 * @param factory [org.openqa.selenium.remote.http.HttpClient.Factory] , ktor, netty
 */
internal fun setupSelenium(folder: File, browser: String = "", factory: String = "ktor") {
    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    try {
        MxLib.setLoggerFactory { name -> NopLogger(name) }
        MxLib.setDataStorage(folder)
    } catch (_: ValueInitializedException) {
        //
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

        Logger.getLogger("org.openqa.selenium").level = Level.OFF

        MxSelenium.initialize()
    } finally {
        thread.contextClassLoader = oc
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
        else -> throw IllegalArgumentException("不支持设置参数的浏览器")
    }
}

internal const val INIT = "xyz.cssxsh.selenium.timeout.init"

internal const val PAGE = "xyz.cssxsh.selenium.timeout.page"

internal const val INTERVAL = "xyz.cssxsh.selenium.timeout.interval"

private val Init by lazy { Duration.ofMillis(System.getProperty(INIT)?.toLongOrNull() ?: 10_000) }

private val PageLoad by lazy { Duration.ofMillis(System.getProperty(PAGE)?.toLongOrNull() ?: 180_000) }

private val Interval by lazy { Duration.ofMillis(System.getProperty(INTERVAL)?.toLongOrNull() ?: 10_000) }

/**
 * 创建一个 RemoteWebDriver
 * @param config 配置
 */
fun RemoteWebDriver(config: RemoteWebDriverConfig): RemoteWebDriver {

    /**
     * 切换线程上下文，加载相关配置
     */
    val thread = Thread.currentThread()
    val oc = thread.contextClassLoader

    return try {
        thread.contextClassLoader = KtorHttpClient.Factory::class.java.classLoader

        MxSelenium.newDriver(null, config.toConsumer()).apply {
            // 诡异的等级
            setLogLevel(Level.ALL)
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

/**
 * 判断页面是否加载完全
 */
fun RemoteWebDriver.isReady(): Boolean {
    return executeScript(
        """
        function imagesComplete() {
            const images = document.getElementsByTagName('img');
            let complete = images.length !== 0;
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
    @Suppress("UNCHECKED_CAST")
    return executeScript("""return Array.from(arguments).flatMap((selector) => $(selector).hide().toArray())""", *css)
        as ArrayList<RemoteWebElement>
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
    } catch (_: Throwable) {
        //
    }

    return try {
        tab.hide(*hide)
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
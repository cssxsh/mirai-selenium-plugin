package xyz.cssxsh.selenium

import io.github.karlatemp.mxlib.*
import io.github.karlatemp.mxlib.logger.*
import io.github.karlatemp.mxlib.selenium.*
import kotlinx.coroutines.*
import org.openqa.selenium.*
import org.openqa.selenium.chromium.*
import org.openqa.selenium.firefox.*
import org.openqa.selenium.remote.*
import java.io.*
import java.time.*
import java.util.logging.*

// region Setup Selenium

private fun Class<*>.getLogger(): Logger {
    return declaredFields.first { it.type == Logger::class.java }.apply { isAccessible = true }.get(null) as Logger
}

/**
 * 初始化 Selenium/Mxlib 配置
 * @param folder 数据缓存文件夹
 * @param browser 浏览器类型 Chrome, Firefox ...
 * @param factory [org.openqa.selenium.remote.http.HttpClient.Factory] , ktor, netty
 */
internal fun setupSelenium(folder: File, browser: String = "", factory: String = "ktor") {
    if (browser.isNotBlank()) System.setProperty("mxlib.selenium.browser", browser)
    System.setProperty("webdriver.http.factory", factory)
    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    MxLib.setLoggerFactory { name -> NopLogger(name) }
    MxLib.setDataStorage(folder)
    ProtocolHandshake::class.java.getLogger().parent.level = Level.OFF

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
}

// endregion

// region RemoteWebDriver

/**
 * load JavaScript form xyz.cssxsh.selenium
 */
@Suppress("FunctionName")
private fun JavaScript(name: String): String {
    return requireNotNull(KtorHttpClient.Factory::class.java.getResourceAsStream("$name.js")) { "脚本${name}不存在" }
        .use { it.reader().readText() + "\nreturn $name();" }
}

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
            setExperimentalOption(
                "mobileEmulation",
                mapOf(
                    "deviceMetrics" to mapOf(
                        "width" to width,
                        "height" to height,
                        "pixelRatio" to pixelRatio
                    ),
                    "userAgent" to "$userAgent MicroMessenger"
                )
            )
        }
        is FirefoxOptions -> capabilities.apply {
            setHeadless(headless)
            setPageLoadStrategy(PageLoadStrategy.NORMAL)
            setLogLevel(FirefoxDriverLogLevel.FATAL)
            setAcceptInsecureCerts(true)
            // XXX 手动关闭 webgl
            addPreference("webgl.disabled", true)
            addPreference("devtools.responsive.touchSimulation.enabled", true)
            addPreference("devtools.responsive.viewport.width", width)
            addPreference("devtools.responsive.viewport.height", height)
            addPreference("devtools.responsive.viewport.pixelRatio", pixelRatio)
            addPreference("devtools.responsive.userAgent", "$userAgent MicroMessenger")
            // XXX responsive 无法调用
            addPreference("general.useragent.override", "$userAgent MicroMessenger")
            addArguments("--width=${width}", "--height=${height}")
        }
        else -> throw IllegalArgumentException("未设置参数的浏览器")
    }
}

private val IS_READY_SCRIPT by lazy { JavaScript("IsReady") }

private val HIDE by lazy { JavaScript("Hide") }

internal const val INIT = "xyz.cssxsh.selenium.screenshot.init"

internal const val TIMEOUT = "xyz.cssxsh.selenium.screenshot.timeout"

internal const val INTERVAL = "xyz.cssxsh.selenium.screenshot.interval"

private val Init by lazy { Duration.ofMillis(System.getProperty(INIT).toLongOrNull() ?: 10_000) }

private val Timeout by lazy { Duration.ofMillis(System.getProperty(TIMEOUT).toLongOrNull() ?: 180_000) }

private val Interval by lazy { Duration.ofMillis(System.getProperty(INTERVAL).toLongOrNull() ?: 10_000) }

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
                pageLoadTimeout(Timeout)
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
fun RemoteWebDriver.isReady(): Boolean = executeScript(IS_READY_SCRIPT) as Boolean

/**
 * 隐藏指定 css 过滤器的 WebElement
 * @param css CSS过滤器
 */
fun RemoteWebDriver.hide(vararg css: String): List<RemoteWebElement> {
    @Suppress("UNCHECKED_CAST")
    return executeScript(HIDE, *css) as ArrayList<RemoteWebElement>
}

/**
 * 打开指定 url 页面，并截取图片
 * @param css CSS过滤器
 * @return 返回的图片文件数据，格式PNG
 */
suspend fun RemoteWebDriver.getScreenshot(url: String, vararg hide: String): ByteArray {
    val home = windowHandle
    val tab = switchTo().newWindow(WindowType.TAB) as RemoteWebDriver

    return try {
        withTimeout(Timeout.toMillis()) {
            tab.get(url)
            delay(Init.toMillis())
            while (!isReady()) {
                delay(Interval.toMillis())
            }
        }

        hide(*hide)

        getScreenshotAs(OutputType.BYTES)
    } finally {
        tab.close()
        switchTo().window(home)
    }
}

// endregion
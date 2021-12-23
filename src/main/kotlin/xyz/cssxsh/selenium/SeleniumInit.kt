package xyz.cssxsh.selenium

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.openqa.selenium.*
import org.openqa.selenium.chrome.*
import org.openqa.selenium.chromium.*
import org.openqa.selenium.edge.*
import org.openqa.selenium.firefox.*
import org.openqa.selenium.remote.*
import java.io.*
import java.util.zip.*

internal const val SELENIUM_FOLDER = "xyz.cssxsh.selenium.folder"

internal const val SELENIUM_DOWNLOAD_ATTEMPT = "xyz.cssxsh.selenium.download.attempt"

internal const val CHROME_BROWSER_BINARY = "webdriver.chrome.bin"

private enum class OperatingSystem {
    Windows,
    Linux,
    Mac;

    companion object {
        @JvmStatic
        val current: OperatingSystem by lazy {
            val name = System.getProperty("os.name")
            for (value in values()) {
                if (name.startsWith(prefix = value.name, ignoreCase = true)) {
                    return@lazy value
                }
            }
            throw UnsupportedOperationException("OS: $name")
        }
    }
}

private object AllIgnoredOutputStream : OutputStream() {
    override fun close() {}
    override fun write(b: ByteArray, off: Int, len: Int) {}
    override fun write(b: ByteArray) {}
    override fun write(b: Int) {}
    override fun flush() {}
}

internal typealias RemoteWebDriverSupplier = (config: RemoteWebDriverConfig) -> RemoteWebDriver

/**
 * [OperatingSystem.Windows] 查询注册表
 * @see RegisterKeys
 */
private fun queryRegister(key: String): String {
    val (path, name) = key.split('|')
    return ProcessBuilder("reg", "query", path, "/v", name).start()
        .inputStream.use { it.reader().readText() }
        .substringAfter("REG_SZ").trim()
}

private fun download(url: String): ByteArray = runBlocking(KtorContext) {
    val client = HttpClient(OkHttp)
    var attempt = System.getProperty(SELENIUM_DOWNLOAD_ATTEMPT, "3").toInt()
    while (isActive) {
        try {
            return@runBlocking client.get(url)
        } catch (exception: IOException) {
            if (attempt-- <= 0) {
                throw exception
            }
        }
    }
    throw CancellationException("无法下载 $url")
}

/**
 * @param browser Edge Chrome Chromium Firefox
 * @see SELENIUM_FOLDER 下载目录
 */
internal fun setupWebDriver(browser: String = ""): RemoteWebDriverSupplier {
    val folder = File(System.getProperty(SELENIUM_FOLDER, "."))
    return when {
        browser.isBlank() -> {
            /**
             * auto find default browser
             */
            val default: String = when (OperatingSystem.current) {
                OperatingSystem.Windows -> {
                    try {
                        queryRegister(key = RegisterKeys.USER_CHOICE)
                    } catch (cause: Throwable) {
                        throw UnsupportedOperationException("UserChoice 查询失败", cause)
                    }
                }
                OperatingSystem.Linux -> {
                    try {
                        ProcessBuilder("xdg-settings", "get", "default-web-browser").start()
                            .inputStream.use { it.reader().readText() }
                    } catch (cause: Throwable) {
                        throw UnsupportedOperationException("xdg-settings 执行失败，可能需要安装 xdg-utils", cause)
                    }
                }
                OperatingSystem.Mac -> {
                    // XXX: MacOS/Default
                    File("/Applications").list().orEmpty()
                        .filter { it.endsWith(".app") }
                        .firstOrNull { """Chrome|Chromium|Firefox""".toRegex(RegexOption.IGNORE_CASE) in it }
                        ?: throw UnsupportedOperationException("未找到受支持的浏览器")
                }
            }

            setupWebDriver(browser = default)
        }
        browser.contains(other = "Edge", ignoreCase = true) -> setupEdgeDriver(folder = folder)
        browser.contains(other = "Chrome", ignoreCase = true) -> setupChromeDriver(folder = folder, chromium = false)
        browser.contains(other = "Chromium", ignoreCase = true) -> setupChromeDriver(folder = folder, chromium = true)
        browser.contains(other = "Firefox", ignoreCase = true) -> setupFirefoxDriver(folder = folder)
        else -> throw UnsupportedOperationException("不支持的浏览器 $browser")
    }
}

private fun setupEdgeDriver(folder: File): RemoteWebDriverSupplier {
    if (OperatingSystem.current != OperatingSystem.Windows) {
        throw UnsupportedOperationException("Edge only supported Windows/Edge")
    }

    val version = try {
        queryRegister(key = RegisterKeys.EDGE)
    } catch (cause: Throwable) {
        throw UnsupportedOperationException("Edge 版本获取失败", cause)
    }

    val xml = folder.resolve("msedgedriver-${version}.xml")
    if (xml.exists().not()) {
        xml.writeBytes(
            try {
                download(url = "https://msedgewebdriverstorage.blob.core.windows.net/edgewebdriver?prefix=${version}&comp=list&timeout=60000")
            } catch (cause: Throwable) {
                throw UnsupportedOperationException("无法下载 msedgewebdriver 版本信息", cause)
            }
        )
    }

    val url = """(?<=<Url>).{16,256}\.zip""".toRegex()
        .findAll(xml.readText())
        .first { "win32" in it.value }.value

    val file = folder.resolve("msedgedriver-${version}_${url.substringAfterLast('_')}")
    if (file.exists().not()) {
        file.writeBytes(
            try {
                download(url = url)
            } catch (cause: Throwable) {
                throw UnsupportedOperationException("无法下载 msedgedriver", cause)
            }
        )
    }

    val driver = folder.resolve("${file.nameWithoutExtension}.exe")
    if (driver.exists().not()) {
        val zip = ZipFile(file)
        zip.getInputStream(zip.getEntry("msedgedriver.exe")).use { input ->
            driver.writeBytes(input.readAllBytes())
        }
    }
    driver.setExecutable(true)

    System.setProperty(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY, driver.absolutePath)

    return { config ->
        if (config.log) {
            val path = folder.resolve("msedgedriver.log").absolutePath
            System.setProperty(EdgeDriverService.EDGE_DRIVER_LOG_PROPERTY, path)
        }
        if (config.factory.isNotBlank()) System.setProperty("webdriver.http.factory", config.factory)
        val options = EdgeOptions().also(config.toConsumer())
        val service = EdgeDriverService.createDefaultService()
        service.sendOutputTo(AllIgnoredOutputStream)
        EdgeDriver(service, options)
    }
}

private fun setupChromeDriver(folder: File, chromium: Boolean): RemoteWebDriverSupplier {
    // 取版本
    val version0 = try {
        when (OperatingSystem.current) {
            OperatingSystem.Windows -> queryRegister(key = if (chromium) RegisterKeys.CHROMIUM else RegisterKeys.CHROME)
            OperatingSystem.Linux -> {
                ProcessBuilder(if (chromium) "chromium-browser" else "google-chrome", "--version").start()
                    .inputStream.use { it.reader().readText() }
            }
            OperatingSystem.Mac -> {
                val path = if (chromium) {
                    System.getProperty(CHROME_BROWSER_BINARY, "/Applications/Chromium.app/Contents/MacOS/Chromium")
                } else {
                    System.getProperty(CHROME_BROWSER_BINARY,"/Applications/Google\\ Chrome.app/Contents/MacOS/Google\\ Chrome")
                }
                ProcessBuilder(path, "--version").start()
                    .inputStream.use { it.reader().readText() }
            }
        }.substringAfter("Chrome").substringAfter("Chromium").trim()
    } catch (cause: Throwable) {
        throw UnsupportedOperationException("Chrome/Chromium 版本获取失败", cause)
    }
    // https://chromedriver.storage.googleapis.com
    val base = "https://npm.taobao.org/mirrors/chromedriver"

    // 映射
    val mapping = folder.resolve("chromedriver-${version0}.mapping")
    if (mapping.exists().not()) {

        val list = listOf(
            "LATEST_RELEASE_${version0.substringBeforeLast('.')}",
            "LATEST_RELEASE_${version0.substringBefore('.')}",
            "LATEST_RELEASE"
        )
        var bytes: ByteArray? = null
        for (release in list) {
            try {
                bytes = download(url = "${base}/${release}")
                break
            } catch (_: ClientRequestException) {
                continue
            }
        }
        mapping.writeBytes(bytes ?: throw UnsupportedOperationException("无法找到 chromedriver $version0 版本信息"))
    }
    val version = mapping.readText()

    val suffix = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "win32"
        OperatingSystem.Linux -> "linux64"
        OperatingSystem.Mac -> "mac64"
    }
    val url = "${base}/${version}/chromedriver_${suffix}.zip"
    val file = folder.resolve("chromedriver-${version}_${suffix}.zip")
    if (file.exists().not()) {
        file.writeBytes(
            try {
                download(url = url)
            } catch (cause: Throwable) {
                throw UnsupportedOperationException("无法下载 chromedriver ", cause)
            }
        )
    }

    val driver = if (OperatingSystem.current == OperatingSystem.Windows) {
        folder.resolve("chromedriver-${version}_${suffix}.exe")
    } else {
        folder.resolve("chromedriver-${version}_${suffix}")
    }
    if (driver.exists().not()) {
        val zip = ZipFile(file)
        zip.getInputStream(zip.entries().nextElement()).use { input ->
            driver.writeBytes(input.readAllBytes())
        }
    }
    driver.setExecutable(true)

    System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, driver.absolutePath)

    return { config ->
        if (config.log) {
            val path = folder.resolve("chromedriver.log").absolutePath
            System.setProperty(ChromeDriverService.CHROME_DRIVER_LOG_PROPERTY, path)
            System.setProperty(ChromeDriverService.CHROME_DRIVER_APPEND_LOG_PROPERTY, "true")
        }
        if (config.factory.isNotBlank()) System.setProperty("webdriver.http.factory", config.factory)
        val options = ChromeOptions().also(config.toConsumer())
        val service = ChromeDriverService.createServiceWithConfig(options)
        service.sendOutputTo(AllIgnoredOutputStream)
        ChromeDriver(service, options)
    }
}

private fun setupFirefoxDriver(folder: File): RemoteWebDriverSupplier {
    // 取版本
    val latest = "https://api.github.com/repos/mozilla/geckodriver/releases/latest"
    val json = folder.resolve("geckodriver.json")
    if (json.exists().not() || System.currentTimeMillis() - json.lastModified() > 1000L * 60 * 60 * 24 * 7) {
        json.writeBytes(
            try {
                download(url = latest)
            } catch (cause: Throwable) {
                throw UnsupportedOperationException("无法下载 geckodriver 版本信息", cause)
            }
        )
    }
    val suffix = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "win32"
        OperatingSystem.Linux -> "linux64"
        OperatingSystem.Mac -> "macos"
    }
    val url = """https://github\.com/mozilla/geckodriver/releases/download/.{16,64}\.(tar\.gz|zip)""".toRegex()
        .findAll(json.readText())
        .first { result -> suffix in result.value }.value
    val file = folder.resolve(url.substringAfterLast('/'))
    if (file.exists().not()) {
        file.writeBytes(
            try {
                download(url = url)
            } catch (cause: Throwable) {
                throw UnsupportedOperationException("无法下载 geckodriver", cause)
            }
        )
    }

    val driver = if (OperatingSystem.current == OperatingSystem.Windows) {
        folder.resolve("${file.nameWithoutExtension}.exe")
    } else {
        folder.resolve(file.name.substringBefore(".tar"))
    }
    if (driver.exists().not()) {
        if (file.extension == "zip") {
            val zip = ZipFile(file)
            zip.getInputStream(zip.entries().nextElement()).use { input ->
                driver.writeBytes(input.readAllBytes())
            }
        } else {
            // XXX: tar.gz
            ProcessBuilder("tar", "-xzf", file.absolutePath)
                .directory(folder)
                .start()
                .waitFor()
            folder.resolve("geckodriver").renameTo(driver)
        }
    }
    driver.setExecutable(true)

    System.setProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY, driver.absolutePath)

    return { config ->
        if (config.log) {
            val path = folder.resolve("geckodriver.log").absolutePath
            System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, path)
        }
        if (config.factory.isNotBlank()) System.setProperty("webdriver.http.factory", config.factory)
        val options = FirefoxOptions().also(config.toConsumer())
        val service = GeckoDriverService.Builder().usingFirefoxBinary(options.binary).build()
        service.sendOutputTo(AllIgnoredOutputStream)
        FirefoxDriver(service, options)
    }
}

internal fun RemoteWebDriverConfig.toConsumer(): (Capabilities) -> Unit = { capabilities ->
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

            if (headless) {
                // XXX: 手动关闭 webgl
                addPreference("webgl.disabled", true)
            }
            addPreference("devtools.responsive.touchSimulation.enabled", true)
            addPreference("devtools.responsive.viewport.width", width)
            addPreference("devtools.responsive.viewport.height", height)
            addPreference("devtools.responsive.viewport.pixelRatio", pixelRatio)
            addPreference("devtools.responsive.userAgent", userAgent)
            // XXX: responsive 无法调用
            addPreference("general.useragent.override", userAgent)
            addArguments("--width=${width}", "--height=${height}")
        }
        else -> throw UnsupportedOperationException("不支持设置参数的浏览器 ${capabilities::class}")
    }
}
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
import org.openqa.selenium.net.*
import org.openqa.selenium.remote.*
import java.io.*
import java.util.zip.*

internal const val SELENIUM_FOLDER = "xyz.cssxsh.selenium.folder"

internal const val SELENIUM_DOWNLOAD_ATTEMPT = "xyz.cssxsh.selenium.download.attempt"

internal const val SELENIUM_DOWNLOAD_EXPIRES = "xyz.cssxsh.selenium.download.expires"

internal const val WEBDRIVER_HTTP_FACTORY = "webdriver.http.factory"

internal const val CHROME_BROWSER_BINARY = "webdriver.chrome.bin"

internal const val CHROME_DRIVER_MIRRORS = "webdriver.chrome.mirrors"

internal const val EDGE_BROWSER_BINARY = "webdriver.edge.bin"

private object AllIgnoredOutputStream : OutputStream() {
    override fun close() {}
    override fun write(b: ByteArray, off: Int, len: Int) {}
    override fun write(b: ByteArray) {}
    override fun write(b: Int) {}
    override fun flush() {}
}

internal typealias RemoteWebDriverSupplier = (config: RemoteWebDriverConfig) -> RemoteWebDriver

/**
 * [Platform.WINDOWS] 查询注册表
 * @see RegisterKeys
 */
private fun queryRegister(key: String): String {
    val (path, name) = key.split('|')
    return ProcessBuilder("reg", "query", path, "/v", name)
        .start()
        .inputStream.use { it.reader().readText() }
        .substringAfter("REG_SZ").trim()
}

/**
 * [Platform.WINDOWS] 通过文件夹获取浏览器版本
 * @param folder 二进制文件所在文件夹
 */
private fun queryVersion(folder: File): String {
    // XXX: get version by folder
    val regex = """[\d.]+""".toRegex()
    return folder.list { _, name -> regex matches name }?.lastOrNull()
        ?: throw UnsupportedOperationException("无法在 ${folder.absolutePath} 找到版本信息")
}

/**
 * [Platform.MAC] 通过 Preferences 获取默认浏览器
 */
internal fun queryPreference(): String {
    return ProcessBuilder("plutil", "-p", "~/Library/Preferences/com.apple.LaunchServices/com.apple.LaunchServices.secure.plist")
        .start()
        .inputStream.use { it.reader().readText() }
}

/**
 * 下载文件
 * @param urlString 下载链接
 * @see SELENIUM_DOWNLOAD_ATTEMPT
 */
private fun download(urlString: String): ByteArray = runBlocking(SeleniumContext) {
    val url = Url(urlString = urlString)
    val token = when (url.host) {
        "api.github.com" -> System.getenv("GITHUB_TOKEN")
        else -> null
    }
    val client = HttpClient(OkHttp) {
        defaultRequest {
            if (token != null) {
                header(HttpHeaders.Authorization, "token $token")
            }
        }
    }
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
    throw CancellationException("无法下载 $urlString")
}

/**
 * 安装浏览器驱动， [browser] 为空时获取默认浏览器
 * @param browser Edge Chrome Chromium Firefox
 * @see SELENIUM_FOLDER
 * @see setupEdgeDriver
 * @see setupChromeDriver
 * @see setupFirefoxDriver
 */
internal fun setupWebDriver(browser: String = ""): RemoteWebDriverSupplier {
    val folder = File(System.getProperty(SELENIUM_FOLDER, "."))
    return when {
        browser.isBlank() || browser.contains(other = "Default", ignoreCase = true) -> {
            /**
             * auto find default browser
             */
            val platform = Platform.getCurrent()
            val default: String = when {
                platform.`is`(Platform.WINDOWS) -> {
                    try {
                        queryRegister(key = RegisterKeys.USER_CHOICE)
                    } catch (cause: Throwable) {
                        throw UnsupportedOperationException("UserChoice 查询失败", cause)
                    }
                }
                platform.`is`(Platform.LINUX) -> {
                    try {
                        ProcessBuilder("xdg-settings", "get", "default-web-browser")
                            .start()
                            .inputStream.use { it.reader().readText() }
                    } catch (cause: Throwable) {
                        throw UnsupportedOperationException("xdg-settings 执行失败，可能需要安装 xdg-utils", cause)
                    }
                }
                platform.`is`(Platform.MAC) -> {
                    // XXX: MacOS/Default
                    File("/Applications").list().orEmpty()
                        .filter { it.endsWith(".app") }
                        .firstOrNull { """(?i)Chrome|Chromium|Firefox""".toRegex() in it }
                        ?: throw UnsupportedOperationException("未找到受支持的浏览器")
                }
                else -> throw UnsupportedOperationException("不受支持的平台 $platform")
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

/**
 * 安装 EdgeDriver
 * @param folder 安装目录
 * @see EDGE_BROWSER_BINARY
 * @see RegisterKeys.EDGE
 * @see EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY
 * @see WEBDRIVER_HTTP_FACTORY
 */
private fun setupEdgeDriver(folder: File): RemoteWebDriverSupplier {
    if (Platform.getCurrent().`is`(Platform.WINDOWS).not()) {
        throw UnsupportedOperationException("Edge only supported Windows/Edge")
    }

    val binary = System.getProperty(EDGE_BROWSER_BINARY)?.let(::File)
    val version = try {
        if (binary != null) {
            queryVersion(folder = binary.parentFile)
        } else {
            queryRegister(key = RegisterKeys.EDGE)
        }
    } catch (unsupported: UnsupportedOperationException) {
        throw unsupported
    } catch (cause: Throwable) {
        throw UnsupportedOperationException("Edge 版本获取失败", cause)
    }

    val xml = folder.resolve("msedgedriver-${version}.xml")
    if (xml.exists().not()) {
        xml.writeBytes(
            try {
                download(urlString = "https://msedgewebdriverstorage.blob.core.windows.net/edgewebdriver?prefix=${version}&comp=list&timeout=60000")
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
                download(urlString = url)
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
        if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
        val options = EdgeOptions().also(config.toConsumer())
        if (binary != null) options.setBinary(binary)
        val port = PortProber.findFreePort()
        val uuid = "${System.currentTimeMillis()}-${port}"
        val service = EdgeDriverService.Builder()
            .withLogFile(folder.resolve("msedgedriver.${uuid}.log").takeIf { config.log })
            .usingDriverExecutable(driver)
            .usingPort(port)
            .build()
        val output = folder.resolve("msedgedriver.${uuid}.output")
            .takeIf { config.log }?.outputStream() ?: AllIgnoredOutputStream
        service.sendOutputTo(output)
        EdgeDriver(service, options).also { DriverCache[it] = service }
    }
}

/**
 * 安装 ChromeDriver
 * @param folder 安装目录
 * @param chromium 是否是 Chromium
 * @see CHROME_BROWSER_BINARY
 * @see RegisterKeys.CHROMIUM
 * @see RegisterKeys.CHROME
 * @see CHROME_DRIVER_MIRRORS
 * @see ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY
 * @see WEBDRIVER_HTTP_FACTORY
 */
private fun setupChromeDriver(folder: File, chromium: Boolean): RemoteWebDriverSupplier {
    // 取版本
    val platform = Platform.getCurrent()
    val binary = System.getProperty(CHROME_BROWSER_BINARY)?.let(::File)
    val version0 = try {
        when {
            platform.`is`(Platform.WINDOWS) -> {
                if (binary != null) {
                    queryVersion(folder = folder.parentFile)
                } else {
                    queryRegister(key = if (chromium) RegisterKeys.CHROMIUM else RegisterKeys.CHROME)
                }
            }
            platform.`is`(Platform.LINUX) -> {
                val default = if (chromium) "chromium-browser" else "google-chrome"
                val path = System.getProperty(CHROME_BROWSER_BINARY, default)
                ProcessBuilder(path, "--version")
                    .start()
                    .inputStream.use { it.reader().readText() }
            }
            platform.`is`(Platform.MAC) -> {
                val default = if (chromium) "Chromium" else "Google\\ Chrome"
                val path = System.getProperty(CHROME_BROWSER_BINARY, default)
                ProcessBuilder(path, "--version")
                    .start()
                    .inputStream.use { it.reader().readText() }
            }
            else -> throw UnsupportedOperationException("不受支持的平台 $platform")
        }.substringAfter("Chrome").substringAfter("Chromium").trim()
    } catch (unsupported: UnsupportedOperationException) {
        throw unsupported
    } catch (cause: Throwable) {
        throw UnsupportedOperationException("Chrome/Chromium 版本获取失败", cause)
    }
    // MIRRORS "https://npm.taobao.org/mirrors/chromedriver"
    val base = System.getProperty(CHROME_DRIVER_MIRRORS,"https://chromedriver.storage.googleapis.com")

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
                bytes = download(urlString = "${base}/${release}")
                break
            } catch (_: ClientRequestException) {
                continue
            }
        }
        mapping.writeBytes(bytes ?: throw UnsupportedOperationException("无法找到 chromedriver $version0 版本信息"))
    }
    val version = mapping.readText()

    val suffix = when {
        platform.`is`(Platform.WINDOWS) -> "win32"
        platform.`is`(Platform.LINUX) -> "linux64"
        platform.`is`(Platform.MAC) -> "mac64"
        else -> throw UnsupportedOperationException("不受支持的平台 $platform")
    }
    val url = "${base}/${version}/chromedriver_${suffix}.zip"
    val file = folder.resolve("chromedriver-${version}_${suffix}.zip")
    if (file.exists().not()) {
        file.writeBytes(
            try {
                download(urlString = url)
            } catch (cause: Throwable) {
                throw UnsupportedOperationException("无法下载 chromedriver ", cause)
            }
        )
    }

    val driver = if (platform.`is`(Platform.WINDOWS)) {
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
        if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
        val options = ChromeOptions().also(config.toConsumer())
        if (binary != null) options.setBinary(binary)
        val port = PortProber.findFreePort()
        val uuid = "${System.currentTimeMillis()}-${port}"
        val service = ChromeDriverService.Builder()
            .withAppendLog(config.log)
            .withLogFile(folder.resolve("chromedriver.${uuid}.log").takeIf { config.log })
            .withLogLevel(options.logLevel)
            .usingDriverExecutable(driver)
            .usingPort(port)
            .build()
        val output = folder.resolve("chromedriver.${uuid}.output")
            .takeIf { config.log }?.outputStream() ?: AllIgnoredOutputStream
        service.sendOutputTo(output)
        ChromeDriver(service, options).also { DriverCache[it] = service }
    }
}

/**
 * 安装 FirefoxDriver
 * @param folder 安装目录
 * @see SELENIUM_DOWNLOAD_EXPIRES
 * @see GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY
 * @see WEBDRIVER_HTTP_FACTORY
 * @see FirefoxBinary
 * @see FirefoxDriver.SystemProperty.BROWSER_BINARY
 */
private fun setupFirefoxDriver(folder: File): RemoteWebDriverSupplier {
    // 取版本
    val platform = Platform.getCurrent()
    val latest = "https://api.github.com/repos/mozilla/geckodriver/releases/latest"
    val json = folder.resolve("geckodriver.json")
    val expires = System.getProperty(SELENIUM_DOWNLOAD_EXPIRES, "7").toLong()
    if (json.exists().not() || System.currentTimeMillis() - json.lastModified() > 1000L * 60 * 60 * 24 * expires) {
        json.writeBytes(
            try {
                download(urlString = latest)
            } catch (cause: Throwable) {
                throw UnsupportedOperationException("无法下载 geckodriver 版本信息", cause)
            }
        )
    }
    val suffix = when {
        platform.`is`(Platform.WINDOWS) -> "win32"
        platform.`is`(Platform.LINUX) -> "linux64"
        platform.`is`(Platform.MAC) -> "macos"
        else -> throw UnsupportedOperationException("不受支持的平台 $platform")
    }
    val url = """https://github\.com/mozilla/geckodriver/releases/download/.{16,64}\.(tar\.gz|zip)""".toRegex()
        .findAll(json.readText())
        .first { result -> suffix in result.value }.value
    val file = folder.resolve(url.substringAfterLast('/'))
    if (file.exists().not()) {
        file.writeBytes(
            try {
                download(urlString = url)
            } catch (cause: Throwable) {
                throw UnsupportedOperationException("无法下载 geckodriver", cause)
            }
        )
    }

    val driver = if (platform.`is`(Platform.WINDOWS)) {
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
        if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
        val options = FirefoxOptions().also(config.toConsumer())
        val port = PortProber.findFreePort()
        val uuid = "${System.currentTimeMillis()}-${port}"
        val service = GeckoDriverService.Builder()
            .withLogFile(folder.resolve("geckodriver.${uuid}.log").takeIf { config.log })
            .usingDriverExecutable(driver)
            .usingPort(port)
            .usingFirefoxBinary(options.binary)
            .build()
        val output = folder.resolve("geckodriver.${uuid}.output")
            .takeIf { config.log }?.outputStream() ?: AllIgnoredOutputStream
        service.sendOutputTo(output)
        FirefoxDriver(service, options).also { DriverCache[it] = service }
    }
}

/**
 * [RemoteWebDriverConfig] 配置浏览器 [Capabilities]
 */
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

            addArguments(arguments)
            setExperimentalOption("prefs", preferences.mapValues { (_, value) ->
                value.toBooleanStrictOrNull() ?: value.toDoubleOrNull() ?: value
            })
        }
        is FirefoxOptions -> capabilities.apply {
            setHeadless(headless)
            setPageLoadStrategy(PageLoadStrategy.NORMAL)
            setAcceptInsecureCerts(true)
            if (proxy.isNotBlank()) {
                val url = Url(proxy)
                addPreference("network.proxy.type", 1)
                addPreference("network.proxy.http", url.host)
                addPreference("network.proxy.http_port", url.port)
                addPreference("network.proxy.ssl", url.host)
                addPreference("network.proxy.ssl_port", url.port)
                addPreference("network.proxy.ftp", url.host)
                addPreference("network.proxy.ftp_port", url.port)
                addPreference("network.proxy.socks", url.host)
                addPreference("network.proxy.socks_port", url.port)
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

            addArguments(arguments)
            addPreference("browser.aboutConfig.showWarning", false)
            addPreference("general.warnOnAboutConfig", false)
            for ((key, value) in preferences) {
                addPreference(key, value.toBooleanStrictOrNull() ?: value.toDoubleOrNull() ?: value)
            }
        }
        else -> throw UnsupportedOperationException("不支持设置参数的浏览器 ${capabilities::class}")
    }
}

/**
 * 清理驱动文件
 * @see SELENIUM_FOLDER
 * @see SELENIUM_DOWNLOAD_EXPIRES
 */
internal fun clearWebDriver(): List<File> {
    val expires = System.getProperty(SELENIUM_DOWNLOAD_EXPIRES, "7").toLong()
    val folder = File(System.getProperty(SELENIUM_FOLDER, "."))
    val current = System.currentTimeMillis()
    return (folder.listFiles() ?: return emptyList()).filter { file ->
        file.isFile && (current - file.lastModified()) / (24 * 60 * 60 * 1000) > expires && file.delete()
    }
}

/**
 * 安装 Firefox 浏览器
 * @param folder 安装目录
 * @param version 版本
 * @return binary
 * @see FirefoxBinary
 * @see FirefoxDriver.SystemProperty.BROWSER_BINARY
 */
internal fun setupFirefox(folder: File, version: String): File {
    val base = "https://archive.mozilla.org/pub/firefox/releases"
    val setup = folder.resolve("Firefox-${version}")
    val platform = Platform.getCurrent()
    if (setup.exists().not()) {
        when {
            platform.`is`(Platform.WINDOWS) -> {
                val exe = folder.resolve("Firefox Setup ${version}.exe")
                if (exe.exists().not()) {
                    exe.writeBytes(download(urlString = "${base}/${version}/win64/zh-CN/${exe.name}"))
                }
                val sevenZA = folder.resolve("7za.exe")
                if (sevenZA.exists().not()) {
                    val url = "https://www.7-zip.org/a/7za920.zip"
                    val pack = folder.resolve(url.substringAfterLast('/'))
                    if (pack.exists().not()) {
                        pack.writeBytes(download(urlString = url))
                    }

                    sevenZA.writeBytes(ZipFile(pack).use { file ->
                        val entry = file.getEntry("7za.exe")
                        file.getInputStream(entry).readAllBytes()
                    })
                    sevenZA.setExecutable(true)
                }

                // XXX: bcj2
                ProcessBuilder(sevenZA.absolutePath, "x", exe.absolutePath, "'-x!setup.exe'", "-y")
                    .directory(folder)
                    .start()
                    .waitFor()

                folder.resolve("core").renameTo(setup)
            }
            platform.`is`(Platform.LINUX) -> {
                val bz2 = folder.resolve("firefox-${version}.tar.bz2")
                if (bz2.exists().not()) {
                    bz2.writeBytes(download(urlString = "${base}/${version}/linux-x86_64/zh-CN/${bz2.name}"))
                }

                // XXX: tar.bz2
                ProcessBuilder("tar", "-xjf", bz2.absolutePath)
                    .directory(folder)
                    .start()
                    .waitFor()

                folder.resolve("firefox").renameTo(setup)
            }
            platform.`is`(Platform.MAC) -> {
                val dmg = folder.resolve("Firefox ${version}.dmg")
                if (dmg.exists().not()) {
                    dmg.writeBytes(download(urlString = "${base}/${version}/mac/zh-CN/${dmg.name}"))
                }

                ProcessBuilder("hdiutil", "attach", dmg.absolutePath)
                    .directory(folder)
                    .start()
                    .waitFor()

                System.err.println(File("/Volumes/Firefox/Firefox.app").list()?.toList())

                ProcessBuilder("cp", "-rf", "/Volumes/Firefox/Firefox.app", setup.absolutePath)
                    .directory(folder)
                    .start()
                    .waitFor()

                ProcessBuilder("hdiutil", "detach", "/Volumes/Firefox")
                    .directory(folder)
                    .start()
                    .waitFor()
            }
            else -> throw UnsupportedOperationException("不受支持的平台 $platform")
        }
    }

    val binary = when {
        platform.`is`(Platform.WINDOWS) -> setup.resolve("firefox.exe")
        platform.`is`(Platform.LINUX) -> setup.resolve("firefox")
        platform.`is`(Platform.MAC) -> setup
        else -> throw UnsupportedOperationException("不受支持的平台 $platform")
    }

    System.setProperty(FirefoxDriver.SystemProperty.BROWSER_BINARY, binary.absolutePath)

    return binary
}
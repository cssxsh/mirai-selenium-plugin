package xyz.cssxsh.selenium

import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Dsl.asyncHttpClient
import org.asynchttpclient.uri.Uri
import org.openqa.selenium.*
import org.openqa.selenium.chrome.*
import org.openqa.selenium.chromium.*
import org.openqa.selenium.edge.*
import org.openqa.selenium.firefox.*
import org.openqa.selenium.net.*
import org.openqa.selenium.remote.*
import java.io.*
import java.lang.*
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.zip.*

internal const val SELENIUM_FOLDER = "xyz.cssxsh.selenium.folder"

internal const val SELENIUM_DOWNLOAD_EXPIRES = "xyz.cssxsh.selenium.download.expires"

internal const val SELENIUM_DEFAULT_PORT = 9515

internal const val WEBDRIVER_HTTP_FACTORY = "webdriver.http.factory"

internal const val CHROME_BROWSER_BINARY = "webdriver.chrome.bin"

internal const val CHROME_DRIVER_MIRRORS = "webdriver.chrome.mirrors"

internal const val CHROME_DRIVER_VERSION = "webdriver.chrome.version"

internal const val FIREFOX_DRIVER_MIRRORS = "webdriver.firefox.mirrors"

internal const val EDGE_BROWSER_BINARY = "webdriver.edge.bin"

internal const val FIREFOX_BROWSER_BINARY = FirefoxDriver.SystemProperty.BROWSER_BINARY

internal const val SEVEN7Z_MIRRORS = "seven7z.mirrors"

internal typealias RemoteWebDriverSupplier = (config: RemoteWebDriverConfig) -> RemoteWebDriver

public typealias DriverOptionsConsumer = (Capabilities) -> Unit

/**
 * [Platform.WINDOWS] 查询注册表
 * @see RegisterKeys
 */
internal fun queryRegister(key: String): String {
    val (path, name) = key.split('|')
    val value = ProcessBuilder("reg", "query", path, "/v", name)
        .start()
        .inputStream.use { it.reader().readText() }
        .substringAfter("REG_SZ").trim()

    return value.ifBlank {
        if (value.startsWith("HKEY_CURRENT_USER")) {
            queryRegister(key = key.replaceFirst("HKEY_CURRENT_USER", "HKEY_LOCAL_MACHINE"))
        } else {
            throw UnsupportedOperationException("注册表 $key 获取失败 \n$value")
        }
    }
}

/**
 * [Platform.WINDOWS] 通过文件夹获取浏览器版本
 * @param folder 二进制文件所在文件夹
 */
internal fun queryVersion(folder: File): String {
    // XXX: get version by folder
    val regex = """^[\d.]+""".toRegex()
    return folder.list()?.reversed()?.firstNotNullOf { regex.find(it)?.value }
        ?: throw UnsupportedOperationException("无法在 ${folder.absolutePath} 找到版本信息")
}

internal val IgnoreJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 下载文件
 * @param urlString 下载链接
 * @param folder 下载目录
 * @param filename 文件名，为空时从 header 或者 url 获取
 */
internal fun download(urlString: String, folder: File, filename: String? = null): File {
    if (filename != null) {
        val current = folder.resolve(filename)
        if (current.exists()) return current
    }

    val client = asyncHttpClient(
        DefaultAsyncHttpClientConfig.Builder()
            .setFollowRedirect(true)
            .setUserAgent("curl/7.61.0")
            .setRequestTimeout(30_000)
            .setConnectTimeout(30_000)
            .setReadTimeout(180_000)
    )
    val response = with(client.prepareGet(urlString)) {
        if ("api.github.com" in urlString) {
            System.getenv("GITHUB_TOKEN")?.let { token ->
                addHeader("Authorization", token)
            }
        }
        execute().get()
    }

    if (response.statusCode != 200) throw IllegalStateException("status ${response.statusCode} download $urlString ")

    val relative = filename
        ?: response.getHeader("Content-Disposition")
            ?.let { text -> """filename="[^"]+"""".toRegex().find(text)?.groupValues?.get(1) }
        ?: response.uri.path.substringAfterLast('/')
            .let { value -> URLDecoder.decode(value, Charset.defaultCharset()) }

    val file = folder.resolve(relative)
    file.writeBytes(response.responseBodyAsBytes)

    return file
}

/**
 * 安装浏览器驱动， [browser] 为空时获取默认浏览器
 * @param browser Edge Chrome Chromium Firefox
 * @see SELENIUM_FOLDER
 * @see setupEdgeDriver
 * @see setupChromeDriver
 * @see setupFirefoxDriver
 * @see org.openqa.selenium.remote.http.HttpClient.Factory.createDefault
 */
internal fun setupWebDriver(browser: String = ""): RemoteWebDriverSupplier {
    val folder = File(System.getProperty(SELENIUM_FOLDER, "."))
    folder.mkdirs()
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
                    } catch (cause: IOException) {
                        throw UnsupportedOperationException("UserChoice 查询失败", cause)
                    }
                }
                platform.`is`(Platform.LINUX) -> {
                    try {
                        ProcessBuilder("xdg-settings", "get", "default-web-browser")
                            .start()
                            .inputStream.use { it.reader().readText() }
                    } catch (cause: IOException) {
                        throw UnsupportedOperationException("xdg-settings 执行失败，可能需要安装 xdg-utils", cause)
                    }
                }
                platform.`is`(Platform.MAC) -> {
                    // XXX: MacOS/Default
                    sequence<String> {
                        File("/Applications").list()
                            ?.let { yieldAll(it.iterator()) }
                        File("${System.getProperty("user.home")}/Applications").list()
                            ?.let { yieldAll(it.iterator()) }
                    }.filter { it.endsWith(".app") }
                        .firstOrNull { """(?i)Chrome|Chromium|Firefox""".toRegex() in it }
                        ?: throw UnsupportedOperationException("未找到受支持的浏览器")
                }
                else -> throw UnsupportedOperationException("不受支持的平台 $platform")
            }

            if (default.isBlank()) throw UnsupportedOperationException("默认浏览器为空")

            setupWebDriver(browser = default)
        }
        browser.contains(other = "Edge", ignoreCase = true) -> {
            if (System.getProperty(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY) == null) {
                setupEdgeDriver(folder = folder)
            } else {
                { config ->
                    if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
                    val options = EdgeOptions().also(config.toConsumer())
                    val port = try {
                        PortProber.findFreePort()
                    } catch (_: RuntimeException) {
                        SELENIUM_DEFAULT_PORT
                    }
                    val uuid = "msedgedriver-${System.currentTimeMillis()}-${port}"
                    val service = EdgeDriverService.Builder()
                        .withLogFile(folder.resolve("${uuid}.log").takeIf { config.log })
                        .usingPort(port)
                        .build()
                    val output = folder.resolve("${uuid}.output")
                        .takeIf { config.log }?.outputStream() ?: OutputStream.nullOutputStream()
                    service.sendOutputTo(output)
                    EdgeDriver(service, options).also { DriverCache[it] = service }
                }
            }
        }
        browser.contains(other = "Chrom", ignoreCase = true) -> {
            if (System.getProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY) == null) {
                setupChromeDriver(folder = folder, chromium = browser.contains(other = "Chromium", ignoreCase = true))
            } else {
                { config ->
                    if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
                    val options = ChromeOptions().also(config.toConsumer())
                    val port = try {
                        PortProber.findFreePort()
                    } catch (_: RuntimeException) {
                        SELENIUM_DEFAULT_PORT
                    }
                    val uuid = "chromedriver-${System.currentTimeMillis()}-${port}"
                    val service = ChromeDriverService.Builder()
                        .withAppendLog(config.log)
                        .withLogFile(folder.resolve("${uuid}.log").takeIf { config.log })
                        .usingPort(port)
                        .build()
                    val output = folder.resolve("${uuid}.output")
                        .takeIf { config.log }?.outputStream() ?: OutputStream.nullOutputStream()
                    service.sendOutputTo(output)
                    ChromeDriver(service, options).also { DriverCache[it] = service }
                }
            }
        }
        browser.contains(other = "Firefox", ignoreCase = true) -> {
            if (System.getProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY) == null) {
                setupFirefoxDriver(folder = folder)
            } else {
                { config ->
                    if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
                    val options = FirefoxOptions().also(config.toConsumer())
                    val port = try {
                        PortProber.findFreePort()
                    } catch (_: RuntimeException) {
                        SELENIUM_DEFAULT_PORT
                    }
                    val uuid = "geckodriver-${System.currentTimeMillis()}-${port}"
                    val service = GeckoDriverService.Builder()
                        .withLogFile(folder.resolve("${uuid}.log").takeIf { config.log })
                        .usingPort(port)
                        .usingFirefoxBinary(options.binary)
                        .build()
                    val output = folder.resolve("${uuid}.output")
                        .takeIf { config.log }?.outputStream() ?: OutputStream.nullOutputStream()
                    service.sendOutputTo(output)
                    FirefoxDriver(service, options).also { DriverCache[it] = service }
                }
            }
        }
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
internal fun setupEdgeDriver(folder: File): RemoteWebDriverSupplier {
    if (Platform.getCurrent().`is`(Platform.WINDOWS).not()) {
        throw UnsupportedOperationException("Edge only supported Windows/Edge")
    }

    val binary = System.getProperty(
        EDGE_BROWSER_BINARY,
        "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
    ).let(::File)
    val version = try {
        if (binary.exists()) {
            queryVersion(folder = binary.parentFile)
        } else {
            queryRegister(key = RegisterKeys.EDGE)
        }
    } catch (cause: IOException) {
        throw UnsupportedOperationException("Edge 版本获取失败", cause)
    }

    val xml = download(
        urlString = "https://msedgewebdriverstorage.blob.core.windows.net/edgewebdriver?prefix=${version}&comp=list&timeout=60000",
        folder = folder,
        filename = "msedgedriver-${version}.xml"
    )

    val url = """(?<=<Url>).{16,256}\.zip""".toRegex()
        .findAll(xml.readText())
        .first { "win64" in it.value }.value

    val file = download(
        urlString = url,
        folder = folder,
        filename = "msedgedriver-${version}_${url.substringAfterLast('_')}"
    )

    val driver = folder.resolve("${file.nameWithoutExtension}.exe")
    if (driver.exists().not()) {
        val zip = ZipFile(file)
        val entry = zip.getEntry("msedgedriver.exe")
        zip.getInputStream(entry).use { input ->
            driver.writeBytes(input.readAllBytes())
        }
        driver.setLastModified(entry.time)
    }
    driver.setExecutable(true)

    return { config ->
        if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
        val options = EdgeOptions().also(config.toConsumer())
        if (binary.exists()) options.setBinary(binary)
        val port = try {
            PortProber.findFreePort()
        } catch (_: RuntimeException) {
            SELENIUM_DEFAULT_PORT
        }
        val uuid = "msedgedriver-${System.currentTimeMillis()}-${port}"
        val service = EdgeDriverService.Builder()
            .withLogFile(folder.resolve("${uuid}.log").takeIf { config.log })
            .usingDriverExecutable(driver)
            .usingPort(port)
            .build()
        val output = folder.resolve("${uuid}.output")
            .takeIf { config.log }?.outputStream() ?: OutputStream.nullOutputStream()
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
internal fun setupChromeDriver(folder: File, chromium: Boolean): RemoteWebDriverSupplier {
    // 取版本
    val platform = Platform.getCurrent()
    val binary = System.getProperty(
        CHROME_BROWSER_BINARY,
        "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
    ).let(::File)
    val version0 = try {
        when {
            platform.`is`(Platform.WINDOWS) -> {
                if (binary.exists()) {
                    queryVersion(folder = binary.parentFile)
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
    } catch (cause: IOException) {
        throw UnsupportedOperationException("Chrome/Chromium 版本获取失败", cause)
    }
    System.setProperty(CHROME_DRIVER_VERSION, version0.substringBefore('.'))
    // MIRRORS "https://npm.taobao.org/mirrors/chromedriver"
    val base = System.getProperty(CHROME_DRIVER_MIRRORS, "https://chromedriver.storage.googleapis.com")

    // 映射
    val mapping = folder.resolve("chromedriver-${version0}.mapping")
    if (mapping.exists().not()) {
        val list = listOf(
            "LATEST_RELEASE_${version0.substringBeforeLast('.')}",
            "LATEST_RELEASE_${version0.substringBefore('.')}",
            "LATEST_RELEASE"
        )

        for (release in list) {
            try {
                download(
                    urlString = "${base}/${release}",
                    folder = folder,
                    filename = "chromedriver-${version0}.mapping"
                )
                break
            } catch (_: IOException) {
                continue
            } catch (_: IllegalStateException) {
                continue
            }
        }
    }
    val version = mapping.readText()

    val suffix = when {
        platform.`is`(Platform.WINDOWS) -> "win32"
        platform.`is`(Platform.LINUX) -> "linux64"
        platform.`is`(Platform.MAC) -> {
            if ("aarch64" in System.getProperty("os.arch")) {
                "mac64_m1"
            } else {
                "mac64"
            }
        }
        else -> throw UnsupportedOperationException("不受支持的平台 $platform")
    }
    val file = download(
        urlString = "${base}/${version}/chromedriver_${suffix}.zip",
        folder = folder,
        filename = "chromedriver-${version}_${suffix}.zip"
    )

    val driver = if (platform.`is`(Platform.WINDOWS)) {
        folder.resolve("chromedriver-${version}_${suffix}.exe")
    } else {
        folder.resolve("chromedriver-${version}_${suffix}")
    }
    if (driver.exists().not()) {
        val zip = ZipFile(file)
        val entry = zip.entries().nextElement()
        zip.getInputStream(entry).use { input ->
            driver.writeBytes(input.readAllBytes())
        }
        driver.setLastModified(entry.time)
    }
    driver.setExecutable(true)

    return { config ->
        if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
        val options = ChromeOptions().also(config.toConsumer())
        if (binary.exists()) options.setBinary(binary)
        val port = try {
            PortProber.findFreePort()
        } catch (_: RuntimeException) {
            SELENIUM_DEFAULT_PORT
        }
        val uuid = "chromedriver-${System.currentTimeMillis()}-${port}"
        val service = ChromeDriverService.Builder()
            .withAppendLog(config.log)
            .withLogFile(folder.resolve("${uuid}.log").takeIf { config.log })
            .usingDriverExecutable(driver)
            .usingPort(port)
            .build()
        val output = folder.resolve("${uuid}.output")
            .takeIf { config.log }?.outputStream() ?: OutputStream.nullOutputStream()
        service.sendOutputTo(output)
        ChromeDriver(service, options).also { DriverCache[it] = service }
    }
}

/**
 * 安装 FirefoxDriver
 * @param folder 安装目录
 * @see GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY
 * @see WEBDRIVER_HTTP_FACTORY
 * @see FirefoxBinary
 * @see FIREFOX_BROWSER_BINARY
 */
internal fun setupFirefoxDriver(folder: File): RemoteWebDriverSupplier {
    // 取版本
    val platform = Platform.getCurrent()
    val json = IgnoreJson.decodeFromString(
        deserializer = GitHubRelease.serializer(),
        string = download(
            urlString = "https://api.github.com/repos/mozilla/geckodriver/releases/latest",
            folder = folder,
            filename = "geckodriver.json"
        ).readText()
    )
    val version = json.tagName
    val filename = when {
        platform.`is`(Platform.WINDOWS) -> "geckodriver-$version-win64.zip"
        platform.`is`(Platform.LINUX) -> "geckodriver-$version-linux64.tar.gz"
        platform.`is`(Platform.MAC) -> {
            if ("aarch64" in System.getProperty("os.arch")) {
                "geckodriver-$version-macos-aarch64.tar.gz"
            } else {
                "geckodriver-$version-macos.tar.gz"
            }
        }
        else -> throw UnsupportedOperationException("不受支持的平台 $platform")
    }
    // https://npm.taobao.org/mirrors/geckodriver/
    val base = System.getProperty(FIREFOX_DRIVER_MIRRORS, "https://github.com/mozilla/geckodriver/releases/download")
    val file = download(
        urlString = "$base/$version/$filename",
        folder = folder,
        filename = filename
    )

    val driver = if (platform.`is`(Platform.WINDOWS)) {
        folder.resolve("${file.nameWithoutExtension}.exe")
    } else {
        folder.resolve(file.name.substringBefore(".tar"))
    }
    if (driver.exists().not()) {
        if (file.extension == "zip") {
            val zip = ZipFile(file)
            val entry = zip.entries().nextElement()
            zip.getInputStream(entry).use { input ->
                driver.writeBytes(input.readAllBytes())
            }
            driver.setLastModified(entry.time)
        } else {
            // XXX: tar.gz
            ProcessBuilder("tar", "-xzf", file.absolutePath)
                .directory(folder)
                .start()
                .waitFor()

            check(folder.resolve("geckodriver").renameTo(driver)) { "重命名 geckodriver 失败" }
        }
    }
    driver.setExecutable(true)

    return { config ->
        if (config.factory.isNotBlank()) System.setProperty(WEBDRIVER_HTTP_FACTORY, config.factory)
        val options = FirefoxOptions().also(config.toConsumer())
        val port = try {
            PortProber.findFreePort()
        } catch (_: RuntimeException) {
            SELENIUM_DEFAULT_PORT
        }
        val uuid = "geckodriver-${System.currentTimeMillis()}-${port}"
        val service = GeckoDriverService.Builder()
            .withLogFile(folder.resolve("${uuid}.log").takeIf { config.log })
            .usingDriverExecutable(driver)
            .usingPort(port)
            .usingFirefoxBinary(options.binary)
            .build()
        val output = folder.resolve("${uuid}.output")
            .takeIf { config.log }?.outputStream() ?: OutputStream.nullOutputStream()
        service.sendOutputTo(output)
        FirefoxDriver(service, options).also { DriverCache[it] = service }
    }
}

/**
 * [RemoteWebDriverConfig] 配置浏览器 [Capabilities]
 */
@PublishedApi
internal fun RemoteWebDriverConfig.toConsumer(): DriverOptionsConsumer = { capabilities ->
    when (capabilities) {
        is ChromiumOptions<*> -> capabilities.apply {
            setPageLoadStrategy(PageLoadStrategy.NORMAL)
            setAcceptInsecureCerts(true)
            addArguments("--silent")
            setExperimentalOption(
                "excludeSwitches",
                listOf("enable-automation", "ignore-certificate-errors")
            )
            addArguments("--hide-scrollbars")
            if (headless) {
                if (System.getProperty(CHROME_DRIVER_VERSION, "109").toInt() >= 109) {
                    addArguments("--headless=new")
                } else {
                    addArguments("--headless=chrome")
                }
            }
            if (proxy.isNotBlank()) {
                addArguments("--proxy-server=${proxy}")
            }

            addArguments("--user-agent=${userAgent}")
            addArguments("--window-size=${width},${height}")
            addArguments(arguments)
            setExperimentalOption("prefs", preferences.mapValues { (_, value) ->
                value.toBooleanStrictOrNull() ?: value.toDoubleOrNull() ?: value
            })
        }
        is FirefoxOptions -> capabilities.apply {
            setPageLoadStrategy(PageLoadStrategy.NORMAL)
            setAcceptInsecureCerts(true)
            if (proxy.isNotBlank()) {
                val url = Uri.create(proxy)
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
                addArguments("-headless")
                // XXX: 手动关闭 webgl
                addPreference("webgl.disabled", true)
            }
            // https://firefox-source-docs.mozilla.org/remote/cdp/RequiredPreferences.html
            addPreference("fission.bfcacheInParent", true)
            addPreference("fission.webContentIsolationStrategy", 0)
            addPreference("general.useragent.override", userAgent)
            addArguments("--width=${width}", "--height=${height}")
            addArguments("--hide-scrollbars")

            addArguments(arguments)
            addPreference("browser.aboutConfig.showWarning", false)
            addPreference("general.warnOnAboutConfig", false)
            for ((key, value) in preferences) {
                addPreference(key, value.toBooleanStrictOrNull() ?: value.toDoubleOrNull() ?: value)
            }
        }
        else -> throw UnsupportedOperationException("不支持设置参数的浏览器 ${capabilities::class}")
    }
    // custom
    capabilities.also(custom)
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
 * [Platform.WINDOWS] 安装 7za
 * @param folder 安装目录
 * @return binary
 */
internal fun sevenZA(folder: File): File {
    return folder.resolve("7za.exe").apply {
        if (exists().not()) {
            val base = System.getProperty(SEVEN7Z_MIRRORS, "https://www.7-zip.org/a")
            val pack = download(urlString = "${base}/7za920.zip", folder = folder)
            ZipFile(pack).use { file ->
                val entry = file.getEntry("7za.exe")
                setLastModified(entry.time)
                writeBytes(file.getInputStream(entry).readAllBytes())
            }
            setExecutable(true)
        }
    }
}

/**
 * 安装 Firefox 浏览器
 * @param folder 安装目录
 * @param version 版本, 为空时下载 release-latest 版
 * @return binary
 * @see FirefoxBinary
 * @see FIREFOX_BROWSER_BINARY
 */
internal fun setupFirefox(folder: File, version: String): File {
    folder.mkdirs()
    val platform = Platform.getCurrent()
    val binary = when {
        platform.`is`(Platform.WINDOWS) -> {
            val setup: File
            val exe = if (version.isBlank()) {
                download(
                    urlString = "https://download.mozilla.org/?product=firefox-latest-ssl&os=win64&lang=zh-CN",
                    folder = folder
                ).apply {
                    val latest = name.substringAfterLast(' ').removeSuffix(".exe")
                    setup = folder.resolve("Firefox-v${latest}-win")
                }
            } else {
                setup = folder.resolve("Firefox-v${version}-win")
                download(
                    urlString = "https://archive.mozilla.org/pub/firefox/releases/${version}/win64/zh-CN/Firefox Setup ${version}.exe",
                    folder = folder,
                    filename = "Firefox Setup ${version}.exe"
                )
            }

            if (setup.exists().not()) {
                // XXX: bcj2
                ProcessBuilder(sevenZA(folder = folder).absolutePath, "x", exe.absolutePath, "-x!setup.exe", "-y")
                    .directory(folder)
                    .start()
                    // 防止卡顿
                    .apply { inputStream.transferTo(OutputStream.nullOutputStream()) }
                    .waitFor()

                check(folder.resolve("core").renameTo(setup)) { "重命名 core 失败" }
            }

            setup.resolve("firefox.exe")
        }
        platform.`is`(Platform.LINUX) -> {
            val setup: File
            val bz2 = if (version.isBlank()) {
                download(
                    urlString = "https://download.mozilla.org/?product=firefox-latest-ssl&os=linux64&lang=zh-CN",
                    folder = folder
                ).apply {
                    val latest = name.substringAfterLast('-').removeSuffix(".tar.bz2")
                    setup = folder.resolve("Firefox-v${latest}-linux")
                }
            } else {
                setup = folder.resolve("Firefox-v${version}-linux")
                download(
                    urlString = "https://archive.mozilla.org/pub/firefox/releases/${version}/linux-x86_64/zh-CN/firefox-${version}.tar.bz2",
                    folder = folder,
                    filename = "firefox-${version}.tar.bz2"
                )
            }

            if (setup.exists().not()) {
                // XXX: tar.bz2
                ProcessBuilder("tar", "-xjf", bz2.absolutePath)
                    .directory(folder)
                    .start()
                    .waitFor()

                check(folder.resolve("firefox").renameTo(setup)) { "重命名 firefox 失败" }
            }

            setup.resolve("firefox")
        }
        platform.`is`(Platform.MAC) -> {
            val setup: File
            val dmg = if (version.isBlank()) {
                download(
                    urlString = "https://download.mozilla.org/?product=firefox-latest-ssl&os=osx&lang=zh-CN",
                    folder = folder
                ).apply {
                    val latest = name.substringAfterLast(' ').removeSuffix(".dmg")
                    setup = folder.resolve("Firefox-v${latest}-mac")
                }
            } else {
                setup = folder.resolve("Firefox-v${version}-mac")
                download(
                    urlString = "https://archive.mozilla.org/pub/firefox/releases/${version}/mac/zh-CN/Firefox ${version}.dmg",
                    folder = folder,
                    filename = "Firefox ${version}.dmg"
                )
            }

            if (setup.exists().not()) {
                ProcessBuilder("hdiutil", "attach", "-quiet", "-noautofsck", "-noautoopen", dmg.absolutePath)
                    .directory(folder)
                    .start()
                    .waitFor()

                val volume = File("/Volumes/Firefox")

                ProcessBuilder("cp", "-a", volume.absolutePath, setup.absolutePath)
                    .directory(volume)
                    .start()
                    .waitFor()

                ProcessBuilder("hdiutil", "detach", volume.absolutePath)
                    .directory(folder)
                    .start()
                    .waitFor()
            }

            setup.resolve("Firefox.app/Contents/MacOS/firefox")
        }
        else -> throw UnsupportedOperationException("不受支持的平台 $platform")
    }

    System.setProperty(FIREFOX_BROWSER_BINARY, binary.absolutePath)

    return binary
}

/**
 * 安装 Chromium 浏览器 , download by [macchrome](https://github.com/macchrome)
 * @param folder 安装目录
 * @param version 版本, 为空时下载 snapshots-latest 版
 * @return binary
 * @see CHROME_BROWSER_BINARY
 */
internal fun setupChromium(folder: File, version: String): File {
    folder.mkdirs()
    val platform = Platform.getCurrent()
    fun release(owner: String, repo: String): GitHubRelease {
        return if (version.isNotBlank()) {
            var page = 0
            val release: GitHubRelease
            while (true) {
                val releases = IgnoreJson.decodeFromString(
                    deserializer = ListSerializer(GitHubRelease.serializer()),
                    string = download(
                        urlString = "https://api.github.com/repos/$owner/$repo/releases?page=${page++}",
                        folder = folder
                    ).readText()
                )

                if (releases.isEmpty()) throw IllegalArgumentException("Chromium Version: $version 查找失败")

                release = releases.find { version in it.tagName } ?: continue
                break
            }
            release
        } else {
            IgnoreJson.decodeFromString(
                deserializer = GitHubRelease.serializer(),
                string = download(
                    urlString = "https://api.github.com/repos/$owner/$repo/releases/latest",
                    folder = folder
                ).readText()
            )
        }
    }

    val binary = when {
        // https://github.com/macchrome/winchrome/releases
        platform.`is`(Platform.WINDOWS) -> {
            val release = release(owner = "macchrome", repo = "winchrome")
            val setup = folder.resolve("Chromium-${release.tagName}")

            if (setup.exists().not()) {
                val url = release.assets
                    .first { asset -> asset.browserDownloadUrl.endsWith(".7z") }
                    .browserDownloadUrl

                val pack = download(urlString = url, folder = folder, filename = url.substringAfterLast('/'))

                // XXX: 7z
                ProcessBuilder(sevenZA(folder = folder).absolutePath, "x", pack.absolutePath, "-y")
                    .directory(folder)
                    .start()
                    // 防止卡顿
                    .apply { inputStream.transferTo(OutputStream.nullOutputStream()) }
                    .waitFor()

                check(folder.resolve(pack.nameWithoutExtension).renameTo(setup)) {
                    "重命名 ${pack.nameWithoutExtension} 失败"
                }
            }

            setup.resolve("chrome.exe")
        }
        // https://github.com/macchrome/linchrome/releases
        platform.`is`(Platform.LINUX) -> {
            val release = release(owner = "macchrome", repo = "linchrome")
            val setup = folder.resolve("Chromium-${release.tagName}")

            if (setup.exists().not()) {
                val url = release.assets
                    .first { asset -> asset.browserDownloadUrl.endsWith(".tar.xz") }
                    .browserDownloadUrl

                val xz = download(urlString = url, folder = folder, filename = url.substringAfterLast('/'))

                // XXX: tar.xz
                ProcessBuilder("tar", "-xf", xz.absolutePath)
                    .directory(folder)
                    .start()
                    .waitFor()

                val unpack = xz.name.removeSuffix(".tar.xz")
                check(folder.resolve(unpack).renameTo(setup)) { "重命名 $unpack 失败" }
            }

            setup.resolve("chrome")
        }
        // https://github.com/macchrome/macstable/releases
        platform.`is`(Platform.MAC) -> {
            val release = release(owner = "macchrome", repo = "macstable")
            val setup = folder.resolve("Chromium-${release.tagName}")

            if (setup.exists().not()) {
                val url = release.assets
                    .first { asset -> "Chromium" in asset.browserDownloadUrl }
                    .browserDownloadUrl

                val zip = download(urlString = url, folder = folder, filename = url.substringAfterLast('/'))

                setup.mkdirs()
                // XXX: big zip
                ProcessBuilder("unzip", "-o", "-q", zip.absolutePath)
                    .directory(setup)
                    .start()
                    .waitFor()
            }

            setup.resolve("Chromium.app/Contents/MacOS/Chromium")
        }
        else -> throw UnsupportedOperationException("不受支持的平台 $platform")
    }

    System.setProperty(CHROME_BROWSER_BINARY, binary.absolutePath)

    return binary
}
package xyz.cssxsh.selenium

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.*
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

internal const val SELENIUM_DOWNLOAD_EXPIRES = "xyz.cssxsh.selenium.download.expires"

internal const val WEBDRIVER_HTTP_FACTORY = "webdriver.http.factory"

internal const val CHROME_BROWSER_BINARY = "webdriver.chrome.bin"

internal const val CHROME_DRIVER_MIRRORS = "webdriver.chrome.mirrors"

internal const val FIREFOX_DRIVER_MIRRORS = "webdriver.firefox.mirrors"

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
internal fun queryRegister(key: String): String {
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
internal fun queryVersion(folder: File): String {
    // XXX: get version by folder
    val regex = """[\d.]+""".toRegex()
    return folder.list()?.reversed()?.firstNotNullOf { regex.find(it)?.value }
        ?: throw UnsupportedOperationException("无法在 ${folder.absolutePath} 找到版本信息")
}

/**
 * [Platform.MAC] 通过 Preferences 获取默认浏览器
 */
internal fun queryPreference(): String {
    return ProcessBuilder(
        "plutil",
        "-p",
        "~/Library/Preferences/com.apple.LaunchServices/com.apple.LaunchServices.secure.plist"
    )
        .start()
        .inputStream.use { it.reader().readText() }
}

internal fun HttpMessage.contentDisposition(): ContentDisposition? {
    return headers[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
}

/**
 * 下载文件
 * @param urlString 下载链接
 * @param folder 下载目录
 * @param filename 文件名，为空时从 header 或者 url 获取
 */
internal fun download(urlString: String, folder: File, filename: String? = null): File = runBlocking(SeleniumContext) {

    if (filename != null) {
        val current = folder.resolve(filename)
        if (current.exists()) return@runBlocking current
    }

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
    client.get<HttpStatement>(url).execute { response ->
        val relative = filename
            ?: response.contentDisposition()?.parameter(ContentDisposition.Parameters.FileName)
            ?: response.request.url.encodedPath.substringAfterLast('/').decodeURLPart()

        val file = folder.resolve(relative)

        file.outputStream().use { output ->
            val channel: ByteReadChannel = response.receive()

            while (!channel.isClosedForRead) {
                channel.copyTo(output)
            }
        }

        file
    }
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
                    (File("/Applications").list().orEmpty().asList() +
                        File("${System.getProperty("user.home")}/Applications").list().orEmpty())
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
internal fun setupEdgeDriver(folder: File): RemoteWebDriverSupplier {
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


    val xml = download(
        urlString = "https://msedgewebdriverstorage.blob.core.windows.net/edgewebdriver?prefix=${version}&comp=list&timeout=60000",
        folder = folder,
        filename = "msedgedriver-${version}.xml"
    )

    val url = """(?<=<Url>).{16,256}\.zip""".toRegex()
        .findAll(xml.readText())
        .first { "win32" in it.value }.value

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
internal fun setupChromeDriver(folder: File, chromium: Boolean): RemoteWebDriverSupplier {
    // 取版本
    val platform = Platform.getCurrent()
    val binary = System.getProperty(CHROME_BROWSER_BINARY)?.let(::File)
    val version0 = try {
        when {
            platform.`is`(Platform.WINDOWS) -> {
                if (binary != null) {
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
    } catch (unsupported: UnsupportedOperationException) {
        throw unsupported
    } catch (cause: Throwable) {
        throw UnsupportedOperationException("Chrome/Chromium 版本获取失败", cause)
    }
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
            } catch (_: ClientRequestException) {
                continue
            }
        }
    }
    val version = mapping.readText()

    val suffix = when {
        platform.`is`(Platform.WINDOWS) -> "win32"
        platform.`is`(Platform.LINUX) -> "linux64"
        platform.`is`(Platform.MAC) -> "mac64"
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
internal fun setupFirefoxDriver(folder: File): RemoteWebDriverSupplier {
    // 取版本
    val platform = Platform.getCurrent()
    val json = GitHubJson.decodeFromString(
        deserializer = GitHubRelease.serializer(),
        string = download(
            urlString = "https://api.github.com/repos/mozilla/geckodriver/releases/latest",
            folder = folder,
            filename = "geckodriver.json"
        ).readText()
    )
    val version = json.tagName
    val filename = when {
        platform.`is`(Platform.WINDOWS) -> "geckodriver-$version-win32.zip"
        platform.`is`(Platform.LINUX) -> "geckodriver-$version-linux64.tar.gz"
        platform.`is`(Platform.MAC) -> "geckodriver-$version-macos.tar.gz"
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
 * [Platform.WINDOWS] 安装 7za
 * @param folder 安装目录
 * @return binary
 */
internal fun sevenZA(folder: File): File {
    return folder.resolve("7za.exe").apply {
        if (exists().not()) {
            val pack = download(urlString = "https://www.7-zip.org/a/7za920.zip", folder = folder)
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
 * @see FirefoxDriver.SystemProperty.BROWSER_BINARY
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
                    setup = folder.resolve("Firefox-${latest}-win")
                }
            } else {
                setup = folder.resolve("Firefox-${version}-win")
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
                    .apply { inputStream.transferTo(AllIgnoredOutputStream) }
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
                    setup = folder.resolve("Firefox-${latest}-linux")
                }
            } else {
                setup = folder.resolve("Firefox-${version}-linux")
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
                    setup = folder.resolve("Firefox-${latest}-mac")
                }
            } else {
                setup = folder.resolve("Firefox-${version}-mac")
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

    System.setProperty(FirefoxDriver.SystemProperty.BROWSER_BINARY, binary.absolutePath)

    return binary
}

/**
 * 安装 Chromium 浏览器 [GitHub macchrome](https://github.com/macchrome)
 * @param folder 安装目录
 * @param version 版本, 为空时下载 snapshots-latest 版
 * @return binary
 * @see CHROME_BROWSER_BINARY
 */
internal fun setupChromium(folder: File, version: String): File {
    folder.mkdirs()
    val platform = Platform.getCurrent()
    fun release(repo: String): GitHubRelease {
        return if (version.isBlank()) {
            var page = 0
            val release: GitHubRelease
            while (true) {
                val releases = GitHubJson.decodeFromString(
                    deserializer = ListSerializer(GitHubRelease.serializer()),
                    string = download(
                        urlString = "https://api.github.com/repos/macchrome/$repo/releases?page=${page++}",
                        folder = folder
                    ).readText()
                )

                if (releases.isEmpty()) throw IllegalArgumentException("Chromium Version: $version 查找失败")

                release = releases.find { version in it.tagName } ?: continue
                break
            }
            release
        } else {
            GitHubJson.decodeFromString(
                deserializer = GitHubRelease.serializer(),
                string = download(
                    urlString = "https://api.github.com/repos/macchrome/$repo/releases/latest",
                    folder = folder
                ).readText()
            )
        }
    }

    val binary = when {
        // https://github.com/macchrome/winchrome/releases
        platform.`is`(Platform.WINDOWS) -> {
            val release = release(repo = "winchrome")
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
                    .apply { inputStream.transferTo(AllIgnoredOutputStream) }
                    .waitFor()

                check(folder.resolve(pack.nameWithoutExtension).renameTo(setup)) {
                    "重命名 ${pack.nameWithoutExtension} 失败"
                }
            }

            setup.resolve("chrome.exe")
        }
        // https://github.com/macchrome/linchrome/releases
        platform.`is`(Platform.LINUX) -> {
            val release = release(repo = "linchrome")
            val setup = folder.resolve("Chromium-${release.tagName}")

            if (setup.exists().not()) {
                val url = release.assets
                    .first { asset -> asset.browserDownloadUrl.endsWith(".tar.xz") }
                    .browserDownloadUrl

                // XXX: tar.xz
                val xz = download(urlString = url, folder = folder, filename = url.substringAfterLast('/'))

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
            val release = release(repo = "macstable")
            val setup = folder.resolve("Chromium-${release.tagName}")

            if (setup.exists().not()) {
                val url = release.assets
                    .first { asset -> asset.browserDownloadUrl.startsWith("Chromium") }
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
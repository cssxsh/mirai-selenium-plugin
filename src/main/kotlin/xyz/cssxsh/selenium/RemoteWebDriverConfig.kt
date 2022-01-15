package xyz.cssxsh.selenium


/**
 * 用来配置 RemoteWebDriver
 * @see [RemoteWebDriver]
 * @see [RemoteWebDriverConfig.toConsumer]
 * @see [RemoteWebDriverSupplier]
 */
interface RemoteWebDriverConfig {
    val userAgent: String
    val width: Int
    val height: Int
    val pixelRatio: Int
    val headless: Boolean
    val proxy: String get() = ""
    val log: Boolean get() = false
    val browser: String
    val factory: String
    val arguments: List<String> get() = emptyList()
}
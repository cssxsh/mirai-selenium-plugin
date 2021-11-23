package xyz.cssxsh.selenium


/**
 * 用来配置 RemoteWebDriver
 * @see [RemoteWebDriver]
 * @see [RemoteWebDriverConfig.toConsumer]
 * @see [setupSelenium]
 */
interface RemoteWebDriverConfig {
    val userAgent: String
    val width: Int
    val height: Int
    val pixelRatio: Int
    val headless: Boolean
    val browser: String
}
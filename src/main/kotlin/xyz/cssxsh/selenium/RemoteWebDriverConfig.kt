package xyz.cssxsh.selenium

import java.util.*

/**
 * 用来配置 RemoteWebDriver
 * @see [RemoteWebDriver]
 * @see [RemoteWebDriverConfig.toConsumer]
 * @see [RemoteWebDriverSupplier]
 */
public interface RemoteWebDriverConfig {
    /**
     * Http Header User Agent
     */
    public val userAgent: String

    /**
     * 浏览器窗口宽度
     */
    public val width: Int

    /**
     * 浏览器窗口高度
     */
    public val height: Int

    /**
     * 像素比, 此设置废弃
     * @see setDeviceMetrics
     */
    @Deprecated("此设置废弃", ReplaceWith("0"))
    public val pixelRatio: Int get() = 0

    /**
     * 无头模式
     */
    public val headless: Boolean

    /**
     * 浏览器代理
     */
    public val proxy: String get() = ""

    /**
     * User Preferences
     */
    public val preferences: Map<String, String> get() = emptyMap()

    /**
     * 浏览器日志、输出，开启会输出到 文件，关闭则无输出
     */
    public val log: Boolean get() = false

    /**
     * 浏览器类型：`Chrome`,`Chromium`,`Firefox`,`Edge`, 留空会自动获取系统默认浏览器
     */
    public val browser: String get() = ""

    /**
     * Selenium HttpClientFactory，可选值: ktor / netty
     */
    public val factory: String get() = "netty"

    /**
     * 浏览器启动参数
     */
    public val arguments: List<String> get() = emptyList()

    /**
     * 自定义修改 Capabilities
     */
    public val custom: DriverOptionsConsumer get() = {}

    public companion object INSTANCE : RemoteWebDriverConfig {
        @JvmStatic
        public val loader: ServiceLoader<RemoteWebDriverConfig> by lazy {
            ServiceLoader.load(RemoteWebDriverConfig::class.java, RemoteWebDriverConfig::class.java.classLoader)
        }

        private val instance: RemoteWebDriverConfig by lazy {
            loader.first()
        }

        override val userAgent: String get() = instance.userAgent
        override val width: Int get() = instance.width
        override val height: Int get() = instance.height
        override val headless: Boolean get() = instance.headless
        override val proxy: String get() = instance.proxy
        override val preferences: Map<String, String> get() = instance.preferences
        override val log: Boolean get() = instance.log
        override val browser: String get() = instance.browser
        override val factory: String get() = instance.factory
        override val arguments: List<String> get() = instance.arguments
        override val custom: DriverOptionsConsumer get() = instance.custom
    }
}
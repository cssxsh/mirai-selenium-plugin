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
    public val userAgent: String get() = UserAgents.IPAD

    /**
     * 浏览器窗口宽度
     */
    public val width: Int get() = 768

    /**
     * 浏览器窗口高度
     */
    public val height: Int get() = 1024

    /**
     * 像素比, 此设置废弃
     * @see setDeviceMetrics
     */
    @Deprecated("此设置废弃", ReplaceWith("0"))
    public val pixelRatio: Int
        get() = 0

    /**
     * 无头模式
     */
    public val headless: Boolean get() = true

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
     * Selenium HttpClientFactory，可选值: netty
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
        public val instances: Sequence<RemoteWebDriverConfig> = sequence {
            val clazz = RemoteWebDriverConfig::class.java
            yieldAll(ServiceLoader.load(clazz))
            yieldAll(ServiceLoader.load(clazz, clazz.classLoader))
        }

        override val userAgent: String get() = instances.first().userAgent
        override val width: Int get() = instances.first().width
        override val height: Int get() = instances.first().height
        override val headless: Boolean get() = instances.first().headless
        override val proxy: String get() = instances.first().proxy
        override val preferences: Map<String, String> get() = instances.first().preferences
        override val log: Boolean get() = instances.first().log
        override val browser: String get() = instances.first().browser
        override val factory: String get() = instances.first().factory
        override val arguments: List<String> get() = instances.first().arguments
        override val custom: DriverOptionsConsumer get() = instances.first().custom
    }
}
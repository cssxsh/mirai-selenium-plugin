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
    public val userAgent: String get() = INSTANCE.userAgent

    /**
     * 浏览器窗口宽度
     */
    public val width: Int get() = INSTANCE.width

    /**
     * 浏览器窗口高度
     */
    public val height: Int get() = INSTANCE.height

    /**
     * 像素比，(firefox下设置无效)
     */
    public val pixelRatio: Int get() = INSTANCE.pixelRatio

    /**
     * 无头模式
     */
    public val headless: Boolean get() = INSTANCE.headless

    /**
     * 浏览器代理
     */
    public val proxy: String get() = INSTANCE.proxy

    /**
     * User Preferences
     */
    public val preferences: Map<String, String> get() = INSTANCE.preferences

    /**
     * 浏览器日志、输出，开启会输出到 文件，关闭则无输出
     */
    public val log: Boolean get() = INSTANCE.log

    /**
     * 浏览器类型：`Chrome`,`Chromium`,`Firefox`,`Edge`, 留空会自动获取系统默认浏览器
     */
    public val browser: String get() = INSTANCE.browser

    /**
     * Selenium HttpClientFactory，可选值: ktor / netty
     */
    public val factory: String get() = INSTANCE.factory

    /**
     * 浏览器启动参数
     */
    public val arguments: List<String> get() = INSTANCE.arguments

    public companion object INSTANCE : RemoteWebDriverConfig {
        @JvmStatic
        public val loader: ServiceLoader<RemoteWebDriverConfig> by lazy {
            ServiceLoader.load(RemoteWebDriverConfig::class.java, RemoteWebDriverConfig::class.java.classLoader)
        }

        private val instance: RemoteWebDriverConfig by lazy {
            loader.iterator().next()
        }

        override val userAgent: String get() = instance.userAgent
        override val width: Int get() = instance.width
        override val height: Int get() = instance.height
        override val pixelRatio: Int get() = instance.pixelRatio
        override val headless: Boolean get() = instance.headless
        override val proxy: String get() = instance.proxy
        override val preferences: Map<String, String> get() = instance.preferences
        override val log: Boolean get() = instance.log
        override val browser: String get() = instance.browser
        override val factory: String get() = instance.factory
        override val arguments: List<String> get() = instance.arguments
    }
}
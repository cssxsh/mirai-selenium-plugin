package xyz.cssxsh.selenium

import java.util.*

/**
 * 用来配置 RemoteWebDriver
 * @see [RemoteWebDriver]
 * @see [RemoteWebDriverConfig.toConsumer]
 * @see [RemoteWebDriverSupplier]
 */
public interface RemoteWebDriverConfig {
    public val userAgent: String get() = INSTANCE.userAgent
    public val width: Int get() = INSTANCE.width
    public val height: Int get() = INSTANCE.height
    public val pixelRatio: Int get() = INSTANCE.pixelRatio
    public val headless: Boolean get() = INSTANCE.headless
    public val proxy: String get() = INSTANCE.proxy
    public val preferences: Map<String, String> get() = INSTANCE.preferences
    public val log: Boolean get() = INSTANCE.log
    public val browser: String get() = INSTANCE.browser
    public val factory: String get() = INSTANCE.factory
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
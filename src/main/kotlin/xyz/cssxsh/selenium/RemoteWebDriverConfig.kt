package xyz.cssxsh.selenium

import java.util.*

/**
 * 用来配置 RemoteWebDriver
 * @see [RemoteWebDriver]
 * @see [RemoteWebDriverConfig.toConsumer]
 * @see [RemoteWebDriverSupplier]
 */
interface RemoteWebDriverConfig {
    val userAgent: String get() = INSTANCE.userAgent
    val width: Int get() = INSTANCE.width
    val height: Int get() = INSTANCE.height
    val pixelRatio: Int get() = INSTANCE.pixelRatio
    val headless: Boolean get() = INSTANCE.headless
    val proxy: String get() = INSTANCE.proxy
    val preferences: Map<String, String> get() = INSTANCE.preferences
    val log: Boolean get() = INSTANCE.log
    val browser: String get() = INSTANCE.browser
    val factory: String get() = INSTANCE.factory
    val arguments: List<String> get() = INSTANCE.arguments

    companion object INSTANCE : RemoteWebDriverConfig {
        @JvmStatic
        val loader: ServiceLoader<RemoteWebDriverConfig> by lazy {
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
package xyz.cssxsh.mirai.test

import org.openqa.selenium.remote.*

/**
 * 注意，[RemoteWebDriver] 不要出现在 if (selenium) { } 外面，
 * 否则 [MiraiSeleniumPlugin] 未安装时会出现 [NoClassDefFoundError]
 * 也不应该作为 KotlinPlugin 的属性声明，否则会出现 [NoClassDefFoundError]
 */
lateinit var driver: RemoteWebDriver
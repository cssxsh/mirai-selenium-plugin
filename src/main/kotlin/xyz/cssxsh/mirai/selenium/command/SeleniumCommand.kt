package xyz.cssxsh.mirai.selenium.command

import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.selenium.*
import xyz.cssxsh.mirai.selenium.data.*
import xyz.cssxsh.selenium.*

public object SeleniumCommand : CompositeCommand(
    owner = MiraiSeleniumPlugin,
    primaryName = "selenium",
    description = "Selenium 驱动相关指令"
) {

    private val logger get() = MiraiSeleniumPlugin.logger

    @SubCommand
    @Description("安装驱动文件")
    public suspend fun CommandSender.setup(flush: Boolean = true) {
        sendMessage("安装驱动开始, flush: $flush")
        try {
            if (flush) with(MiraiSeleniumPlugin) { MiraiSeleniumConfig.reload() }
            val result = MiraiSeleniumPlugin.setup(flush = flush)
            sendMessage("安装驱动${if (result) "成功" else "失败"}")
        } catch (cause: Throwable) {
            logger.warning("安装驱动异常", cause)
            sendMessage("安装驱动异常")
        }
    }

    @SubCommand
    @Description("清理驱动文件")
    public suspend fun CommandSender.clear() {
        sendMessage("清理驱动文件")
        try {
            MiraiSeleniumPlugin.clear()
        } catch (cause: Throwable) {
            logger.warning("清理驱动文件异常", cause)
            sendMessage("清理驱动文件异常")
        }
    }

    @SubCommand
    @Description("清理驱动进程")
    public suspend fun CommandSender.destroy(all: Boolean = true) {
        sendMessage(if (all) "清理异常驱动进程" else "清除所有驱动进程")
        try {
            MiraiSeleniumPlugin.destroy(enable = all)
        } catch (cause: Throwable) {
            logger.warning("清理驱动进程异常", cause)
            sendMessage("清理驱动进程异常")
        }
    }

    @SubCommand
    @Description("驱动进程状态")
    public suspend fun CommandSender.status() {
        try {
            sendMessage(DriverCache.status().joinToString(separator = "\n").ifEmpty { "当前没有驱动进程" })
        } catch (cause: Throwable) {
            logger.warning("驱动进程状态异常", cause)
            sendMessage("驱动进程状态异常")
        }
    }

    @SubCommand
    @Description("下载解压 firefox, https://archive.mozilla.org/pub/firefox/releases/")
    public suspend fun CommandSender.firefox(version: String = "") {
        sendMessage("下载 firefox 开始, version: ${version.ifBlank { "latest" }}")
        try {
            val binary = MiraiSeleniumPlugin.firefox(version = version)
            sendMessage("下载结束，binary: ${binary.absolutePath}")
        } catch (cause: Throwable) {
            logger.warning("下载 firefox 异常", cause)
            sendMessage("下载 firefox 异常")
        } finally {
            MiraiBrowserConfig.firefox = System.getProperty(FIREFOX_BROWSER_BINARY).orEmpty()
        }
    }

    @SubCommand
    @Description("下载解压 chromium, https://github.com/macchrome")
    public suspend fun CommandSender.chromium(version: String = "") {
        sendMessage("下载 chromium 开始, version: ${version.ifBlank { "latest" }}")
        try {
            val binary = MiraiSeleniumPlugin.chromium(version = version)
            sendMessage("下载结束，binary: ${binary.absolutePath}")
        } catch (cause: Throwable) {
            logger.warning("下载 chromium 异常", cause)
            sendMessage("下载 chromium 异常")
        } finally {
            MiraiBrowserConfig.chrome = System.getProperty(CHROME_BROWSER_BINARY).orEmpty()
        }
    }
}
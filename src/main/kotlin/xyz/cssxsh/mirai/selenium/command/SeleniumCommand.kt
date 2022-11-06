package xyz.cssxsh.mirai.selenium.command

import kotlinx.serialization.json.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.openqa.selenium.*
import xyz.cssxsh.mirai.selenium.*
import xyz.cssxsh.mirai.selenium.data.*
import xyz.cssxsh.selenium.*
import java.io.*
import java.time.*

/**
 * Selenium 驱动/测试相关指令
 */
public object SeleniumCommand : CompositeCommand(
    owner = MiraiSeleniumPlugin,
    primaryName = "selenium",
    description = "Selenium 驱动相关指令"
) {

    private val logger get() = MiraiSeleniumPlugin.logger

    /**
     * 安装驱动文件
     * @param flush 是否刷新
     */
    @SubCommand
    @Description("安装驱动文件")
    public suspend fun CommandSender.setup(flush: Boolean = true) {
        sendMessage("安装驱动开始, flush: $flush")
        try {
            if (flush) with(MiraiSeleniumPlugin) { MiraiSeleniumConfig.reload() }
            val result = MiraiSeleniumPlugin.setup(flush = flush)
            sendMessage("安装驱动${if (result) "成功" else "失败"}")
        } catch (cause: IOException) {
            logger.warning("安装驱动异常", cause)
            sendMessage("安装驱动异常")
        }
    }

    /**
     * 清理驱动文件
     */
    @SubCommand
    @Description("清理驱动文件")
    public suspend fun CommandSender.clear() {
        sendMessage("清理驱动文件")
        try {
            MiraiSeleniumPlugin.clear()
        } catch (cause: IOException) {
            logger.warning("清理驱动文件异常", cause)
            sendMessage("清理驱动文件异常")
        }
    }

    /**
     * 清理驱动进程
     * @param enable 判断是否进程是否正常工作
     */
    @SubCommand
    @Description("清理驱动进程")
    public suspend fun CommandSender.destroy(enable: Boolean = true) {
        sendMessage(if (enable) "清理异常驱动进程" else "清除所有驱动进程")
        try {
            MiraiSeleniumPlugin.destroy(enable = enable)
        } catch (cause: IOException) {
            logger.warning("清理驱动进程异常", cause)
            sendMessage("清理驱动进程异常")
        }
    }

    /**
     * 驱动进程状态
     */
    @SubCommand
    @Description("驱动进程状态")
    public suspend fun CommandSender.status() {
        try {
            sendMessage(DriverCache.status().joinToString(separator = "\n").ifEmpty { "当前没有驱动进程" })
        } catch (cause: IOException) {
            logger.warning("驱动进程状态异常", cause)
            sendMessage("驱动进程状态异常")
        }
    }

    /**
     * 安装 firefox
     * @param version 版本
     */
    @SubCommand
    @Description("下载解压 firefox, https://archive.mozilla.org/pub/firefox/releases/")
    public suspend fun CommandSender.firefox(version: String = "") {
        sendMessage("下载 firefox 开始, version: ${version.ifBlank { "latest" }}")
        try {
            val binary = MiraiSeleniumPlugin.firefox(version = version)
            sendMessage("下载结束，binary: ${binary.absolutePath}")
        } catch (cause: IOException) {
            logger.warning("下载 firefox 异常", cause)
            sendMessage("下载 firefox 异常")
        } finally {
            MiraiBrowserConfig.firefox = System.getProperty(FIREFOX_BROWSER_BINARY).orEmpty()
        }
    }

    /**
     * 安装 chromium
     * @param version 版本
     */
    @SubCommand
    @Description("下载解压 chromium, https://github.com/macchrome")
    public suspend fun CommandSender.chromium(version: String = "") {
        sendMessage("下载 chromium 开始, version: ${version.ifBlank { "latest" }}")
        try {
            val binary = MiraiSeleniumPlugin.chromium(version = version)
            sendMessage("下载结束，binary: ${binary.absolutePath}")
        } catch (cause: IOException) {
            logger.warning("下载 chromium 异常", cause)
            sendMessage("下载 chromium 异常")
        } finally {
            MiraiBrowserConfig.chrome = System.getProperty(CHROME_BROWSER_BINARY).orEmpty()
        }
    }

    /**
     * 测试 图表绘制功能
     */
    @SubCommand
    @Description("测试 图表绘制功能，将以群员入群事件和发言时间为数据集")
    public suspend fun MemberCommandSenderOnMessage.chart() {
        val tags = mapOf(
            "Within 1 days" to LocalDateTime.now().minusDays(1),
            "Within 3 days" to LocalDateTime.now().minusDays(3),
            "Within 1 weeks" to LocalDateTime.now().minusWeeks(1),
            "Within 1 months" to LocalDateTime.now().minusMonths(1),
            "Within 3 months" to LocalDateTime.now().minusMonths(3),
            "More distant" to LocalDateTime.MIN
        )
        val joins: MutableMap<String, Int> = HashMap()
        val speaks: MutableMap<String, Int> = HashMap()
        val zone = ZoneOffset.ofHours(+8)

        for (member in group.members) {
            val join = LocalDateTime.ofEpochSecond(member.joinTimestamp.toLong(), 0, zone)
            for ((tag, date) in tags) {
                if (join > date) {
                    joins.compute(tag) { _, num -> (num ?: 0) + 1 }
                    break
                }
            }

            val speak = LocalDateTime.ofEpochSecond(member.lastSpeakTimestamp.toLong(), 0, zone)
            for ((tag, date) in tags) {
                if (speak > date) {
                    speaks.compute(tag) { _, num -> (num ?: 0) + 1 }
                    break
                }
            }
        }

        val option = buildJsonObject {
            put("animation", false)
            putJsonObject("tooltip") {
                put("trigger", "item")
            }
            putJsonObject("legend") {
                //
            }
            putJsonObject("grid") {
                put("left", "3%")
                put("right", "4%")
                put("bottom", "3%")
                put("containLabel", true)
            }
            putJsonArray("xAxis") {
                addJsonObject {
                    put("type", "category")
                    putJsonArray("data") {
                        tags.forEach { (date, _) ->
                            add(date)
                        }
                    }
                    putJsonObject("axisTick") {
                        put("alignWithLabel", true)
                    }
                }
            }
            putJsonArray("yAxis") {
                addJsonObject {
                    put("type", "value")
                }
            }
            putJsonArray("series") {
                addJsonObject {
                    put("type", "bar")
                    put("name", "Join")
                    putJsonArray("data") {
                        tags.forEach { (date, _) ->
                            add(joins[date])
                        }
                    }
                }
                addJsonObject {
                    put("type", "bar")
                    put("name", "Speak")
                    putJsonArray("data") {
                        tags.forEach { (date, _) ->
                            add(speaks[date])
                        }
                    }
                }
            }
        }

        useRemoteWebDriver(RemoteWebDriverConfig.INSTANCE) { driver ->
            val bytes = driver.echartsAs(meta = EChartsMeta(option = option.toString()), OutputType.BYTES)

            bytes.toExternalResource().use { group.sendImage(it) }
        }
    }
}
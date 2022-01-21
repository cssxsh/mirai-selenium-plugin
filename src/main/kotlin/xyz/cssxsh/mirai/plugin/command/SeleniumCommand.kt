package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.selenium.*

object SeleniumCommand : CompositeCommand(
    owner = MiraiSeleniumPlugin,
    primaryName = "selenium",
    description = "Selenium 驱动相关指令"
) {

    @SubCommand
    @Description("安装驱动文件")
    suspend fun CommandSender.setup(flush: Boolean = true) {
        sendMessage("安装驱动开始, flush: $flush")
        try {
            val result = MiraiSeleniumPlugin.setup(flush = flush)
            sendMessage("安装驱动${if (result) "成功" else "失败"}")
        } catch (cause: Throwable) {
            sendMessage("安装驱动异常, $cause")
        }
    }

    @SubCommand
    @Description("清理驱动文件")
    suspend fun CommandSender.clear() {
        sendMessage("清理驱动文件")
        try {
            MiraiSeleniumPlugin.clear()
        } catch (cause: Throwable) {
            sendMessage("清理驱动文件异常, $cause")
        }
    }

    @SubCommand
    @Description("清理驱动进程")
    suspend fun CommandSender.destroy(all: Boolean = true) {
        sendMessage(if (all) "清理异常驱动进程" else "清除所有驱动进程")
        try {
            MiraiSeleniumPlugin.destroy(enable = all)
        } catch (cause: Throwable) {
            sendMessage("清理驱动进程异常, $cause")
        }
    }

    @SubCommand
    @Description("驱动进程状态")
    suspend fun CommandSender.status() {
        try {
            sendMessage(DriverCache.status().joinToString(separator = "\n"))
        } catch (cause: Throwable) {
            sendMessage("驱动进程状态异常, $cause")
        }
    }
}
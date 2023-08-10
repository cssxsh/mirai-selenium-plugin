package xyz.cssxsh.mirai.test

import kotlinx.coroutines.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.utils.*
import org.openqa.selenium.OutputType
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.selenium.*
import xyz.cssxsh.mirai.selenium.data.*
import xyz.cssxsh.selenium.*

internal class MiraiSeleniumPluginTest :
    KotlinPlugin(
        JvmPluginDescription(
            id = "xyz.cssxsh.mirai.plugin.mirai-selenium-plugin-test",
            name = "mirai-selenium-test",
            version = "0.0.0"
        ) {
            dependsOn("xyz.cssxsh.mirai.plugin.mirai-selenium-plugin", true)
        }) {

    /**
     * 如果加载成功则为真
     */
    val selenium: Boolean by lazy {
        try {
            MiraiSeleniumPlugin.setup()
        } catch (exception: NoClassDefFoundError) {
            logger.warning { "相关类加载失败，请安装 https://github.com/cssxsh/mirai-selenium-plugin $exception" }
            false
        }
    }

    override fun onEnable() {
        if (selenium) {
            /**
             * 使用 [MiraiSeleniumPlugin] 的默认设置 [MiraiSeleniumConfig]
             */
            driver = MiraiSeleniumPlugin.driver()

            /**
             * 使用 自定义的 的设置 [SeleniumConfig], 实现 [RemoteWebDriverConfig]
             *
             * 注意，[SeleniumConfig] 不要出现在 if (selenium) { } 外面，
             * 否则 [MiraiSeleniumPlugin] 未安装时会出现 [NoClassDefFoundError]
             */
            SeleniumConfig.reload()
            driver = MiraiSeleniumPlugin.driver(config = SeleniumConfig)


            launch {
                val screenshot = driver.getScreenshot(url = "https://mirai.mamoe.net/")
                screenshot.size
            }


            /**
             * 即用即关
             */
            useRemoteWebDriver(SeleniumConfig) { browser ->
                browser.get("https://mirai.mamoe.net/")
            }


            /**
             * 绘制图表
             * option 是 json 数据，格式详见 https://echarts.apache.org/zh/option.html
             * @see [https://echarts.apache.org/examples/zh/editor.html?c=line-stack]
             */
            useRemoteWebDriver(SeleniumConfig) { driver ->
                val option = """
                    {
                      title: {
                        text: 'Stacked Line'
                      },
                      tooltip: {
                        trigger: 'axis'
                      },
                      legend: {
                        data: ['Email', 'Union Ads', 'Video Ads', 'Direct', 'Search Engine']
                      },
                      grid: {
                        left: '3%',
                        right: '4%',
                        bottom: '3%',
                        containLabel: true
                      },
                      toolbox: {
                        feature: {
                          saveAsImage: {}
                        }
                      },
                      xAxis: {
                        type: 'category',
                        boundaryGap: false,
                        data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
                      },
                      yAxis: {
                        type: 'value'
                      },
                      series: [
                        {
                          name: 'Email',
                          type: 'line',
                          stack: 'Total',
                          data: [120, 132, 101, 134, 90, 230, 210]
                        },
                        {
                          name: 'Union Ads',
                          type: 'line',
                          stack: 'Total',
                          data: [220, 182, 191, 234, 290, 330, 310]
                        },
                        {
                          name: 'Video Ads',
                          type: 'line',
                          stack: 'Total',
                          data: [150, 232, 201, 154, 190, 330, 410]
                        },
                        {
                          name: 'Direct',
                          type: 'line',
                          stack: 'Total',
                          data: [320, 332, 301, 334, 390, 330, 320]
                        },
                        {
                          name: 'Search Engine',
                          type: 'line',
                          stack: 'Total',
                          data: [820, 932, 901, 934, 1290, 1330, 1320]
                        }
                      ]
                    }
                """.trimIndent()
                val content = driver.echartsAs(meta = EChartsMeta(option = option), outputType = OutputType.FILE)
            }
        }
    }

    override fun onDisable() {
        if (selenium) {
            /**
             * 关闭 [RemoteWebDriver]
             */
            driver.quit()
        }
    }
}
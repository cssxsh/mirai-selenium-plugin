package xyz.cssxsh.selenium

import org.icepear.echarts.*
import org.icepear.echarts.components.coord.cartesian.*
import org.icepear.echarts.serializer.*
import org.junit.jupiter.api.*
import org.openqa.selenium.*

internal class EChartsTest : SeleniumTest() {

    @Test
    fun canvas() {
        val bar = Bar()
            .setLegend()
            .setTooltip("item")
            .addXAxis(arrayOf("Matcha Latte", "Milk Tea", "Cheese Cocoa", "Walnut Brownie"))
            .addYAxis(ValueAxis().setType("value").setAnimation(false))
            .addSeries("2015", arrayOf(43.3, 83.1, 86.4, 72.4))
            .addSeries("2016", arrayOf(85.8, 73.4, 65.2, 53.9))
            .addSeries("2017", arrayOf(93.7, 55.1, 82.5, 39.1))
        val meta = EChartsMeta(height = "100%", width = "100%", option = EChartsSerializer().toJson(bar.option))

        if (isPC) {
            System.setProperty("xyz.cssxsh.selenium.echarts.cdn", "https://cdn.bootcss.com/echarts/5.2.2/echarts.min.js")
        }

        testRemoteWebDriver { browser, driver ->
            println(browser)

            val svg = driver.echartsAs(meta = meta.copy(renderer = EChartsRenderer.svg), outputType = OutputType.FILE)

            svg.renameTo(folder.resolve("echarts.${browser.lowercase()}.svg"))

            val png = driver.echartsAs(meta = meta.copy(renderer = EChartsRenderer.canvas), outputType = OutputType.BYTES)

            folder.resolve("echarts.${browser.lowercase()}.png")
                .writeBytes(png)
        }
    }
}
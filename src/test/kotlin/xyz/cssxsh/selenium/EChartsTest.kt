package xyz.cssxsh.selenium

import kotlinx.coroutines.delay
import org.icepear.echarts.*
import org.icepear.echarts.render.*
import org.junit.jupiter.api.Test
import java.util.*


internal class EChartsTest : SeleniumTest() {
    @Test
    fun canvas() {
        val bar = Bar()
            .setLegend()
            .setTooltip("item")
            .addXAxis(arrayOf("Matcha Latte", "Milk Tea", "Cheese Cocoa", "Walnut Brownie"))
            .addYAxis()
            .addSeries("2015", arrayOf(43.3, 83.1, 86.4, 72.4))
            .addSeries("2016", arrayOf(85.8, 73.4, 65.2, 53.9))
            .addSeries("2017", arrayOf(93.7, 55.1, 82.5, 39.1))
        val engine = Engine()

        val html = engine.renderHtml(bar)
        val file = folder.resolve("index.html")

        if (isPC) {
            file.writeText(html.replace("https://cdnjs.cloudflare.com/ajax/libs", "https://cdn.bootcss.com"))
        }

        testRemoteWebDriver { browser, driver ->
            println(browser)

            driver.get(file.toURI().toString())
            delay(1_000)
            val png = driver.executeScript("""
                let canvas = document.querySelector("canvas");
                let image = canvas.toDataURL("image/png");
                return image
            """.trimIndent())

            check(png is String)

            folder.resolve("echarts.${browser.lowercase()}.png")
                .writeBytes(Base64.getMimeDecoder().decode(png.substringAfter(',')))
        }
    }
}
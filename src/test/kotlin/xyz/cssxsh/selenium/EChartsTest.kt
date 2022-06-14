package xyz.cssxsh.selenium

import com.github.jknack.handlebars.*
import kotlinx.coroutines.delay
import org.icepear.echarts.*
import org.icepear.echarts.serializer.*
import org.junit.jupiter.api.Test
import org.openqa.selenium.remote.*
import java.util.*


internal class EChartsTest : SeleniumTest() {

    private data class EChartsMeta(
        var height: String,
        var width: String,
        var option: String,
        var cdn: String = "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.2.2/echarts.min.js",
        var id: String = "display-container",
        var renderer: String = "canvas"
    )

    private class EChartsImage(val type: String, val content: String) {
        fun toByteArray(): ByteArray {
            return when (type) {
                "base64" -> Base64.getMimeDecoder().decode(content.substringAfter(','))
                "svg" -> content.toByteArray()
                else -> throw IllegalArgumentException(type)
            }
        }
    }

    private fun RemoteWebDriver.getEChartsImage(id: String): EChartsImage? {
        val result = executeScript("""
            const display = document.getElementById("$id")
            const canvas = display.querySelector("canvas");
            if (canvas) return canvas.toDataURL("image/png")
            const svg = display.querySelector("svg");
            if (svg) return svg.outerHTML
            return null;
        """.trimIndent()) as String? ?: return null

        return if (result.startsWith("data")) {
            EChartsImage(type = "base64", content = result)
        } else {
            EChartsImage(type = "svg", content = result)
        }
    }

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
        val template = Handlebars().compile("custom")
        val meta = EChartsMeta(height = "100%", width = "100%", option = EChartsSerializer.toJson(bar.option))

        if (isPC) {
            meta.cdn = "https://cdn.bootcss.com/echarts/5.2.2/echarts.min.js"
        }

        val html = folder.resolve("index.html")

        testRemoteWebDriver { browser, driver ->
            println(browser)

            html.writeText(template.apply(meta.copy(renderer = "svg")))
            driver.get(html.toURI().toASCIIString())
            delay(1_000)
            val svg = driver.getEChartsImage(id = meta.id)!!

            folder.resolve("echarts.${browser.lowercase()}.svg")
                .writeBytes(svg.toByteArray())

            html.writeText(template.apply(meta.copy(renderer = "canvas")))
            driver.get(html.toURI().toASCIIString())
            delay(1_000)
            val png = driver.getEChartsImage(id = meta.id)!!

            folder.resolve("echarts.${browser.lowercase()}.png")
                .writeBytes(png.toByteArray())
        }
    }
}
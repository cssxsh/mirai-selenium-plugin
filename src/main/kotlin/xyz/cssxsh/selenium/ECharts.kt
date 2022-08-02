package xyz.cssxsh.selenium

import com.github.jknack.handlebars.*
import com.github.jknack.handlebars.io.*
import org.openqa.selenium.remote.*
import java.net.URL
import java.net.URLDecoder
import java.nio.file.Files
import java.util.*

public data class EChartsMeta(
    var height: String = "100%",
    var width: String = "100%",
    var option: String,
    var cdn: String = System.getProperty("xyz.cssxsh.selenium.echarts.cdn", DEFAULT_CDN),
    var renderer: EChartsRenderer = EChartsRenderer.canvas,
    var duration: Long = 1_000
)

@Suppress("EnumEntryName")
public enum class EChartsRenderer { canvas, svg }

private val template by lazy {
    Handlebars(object : URLTemplateLoader() {
        override fun getResource(location: String): URL? = EChartsMeta::class.java.getResource(location)
    }).compile("echarts")
}

private const val DEFAULT_CDN = "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.2.2/echarts.min.js"

public fun RemoteWebDriver.echarts(meta: EChartsMeta): String {
    val html = Files.createTempFile("echarts", ".html").toFile()
    html.writeText(template.apply(meta))
    get(html.toURI().toASCIIString())

    return executeAsyncScript("""
        const duration = arguments[0] || 1000;
        const callback = [...arguments].at(-1);
        const id = setInterval(() => {
            if (window.chart != null) {
                clearInterval(id)
                callback(chart.getDataURL());
            }
        }, duration);
    """.trimIndent(), meta.duration) as String
}

public fun data(url: String): Pair<String, ByteArray> {
    val contentType = url.substring(3, url.indexOf(';'))
    val encode = url.substring(url.indexOf(';') + 1, url.indexOf(','))
    val content = url.substringAfter(',')

    return contentType to when (encode) {
        "base64" -> Base64.getMimeDecoder().decode(content)
        else -> URLDecoder.decode(content, Charsets.UTF_8).toByteArray()
    }
}
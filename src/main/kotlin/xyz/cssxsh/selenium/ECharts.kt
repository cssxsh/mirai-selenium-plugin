package xyz.cssxsh.selenium

import com.github.jknack.handlebars.*
import com.github.jknack.handlebars.io.*
import org.openqa.selenium.*
import org.openqa.selenium.remote.*
import java.net.*
import java.nio.file.*
import java.util.*

/**
 * ECharts 元数据
 * @param option https://echarts.apache.org/zh/option.html
 */
public data class EChartsMeta(
    var height: String = "100%",
    var width: String = "100%",
    var option: String,
    var cdn: String = System.getProperty("xyz.cssxsh.selenium.echarts.cdn", DEFAULT_CDN),
    var renderer: EChartsRenderer = EChartsRenderer.canvas,
    var duration: Long = 1_000
)

/**
 * ECharts 绘制类型
 * @property canvas 画板
 * @property svg 矢量图
 */
@Suppress("EnumEntryName")
public enum class EChartsRenderer { canvas, svg }

private val template by lazy {
    Handlebars(object : URLTemplateLoader() {
        override fun getResource(location: String): URL? = EChartsMeta::class.java.getResource(location)
    }).compile("echarts")
}

private const val DEFAULT_CDN = "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.2.2/echarts.min.js"

/**
 * 调用 echarts 绘图
 * @param meta 数据及相关配置
 * @return 数据URL [data]
 * @see data
 */
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

/**
 * 对 [echarts] 的结果进行解码
 * @see echarts
 */
public fun data(url: String): Pair<String, ByteArray> {
    val contentType = url.substring(3, url.indexOf(';'))
    val encode = url.substring(url.indexOf(';') + 1, url.indexOf(','))
    val content = url.substringAfter(',')

    return contentType to when (encode) {
        "base64" -> Base64.getMimeDecoder().decode(content)
        else -> URLDecoder.decode(content, Charsets.UTF_8).toByteArray()
    }
}

/**
 * 调用 echarts 绘图
 * @param meta 数据及相关配置
 * @param outputType 输出类型
 */
public fun <X: Any> RemoteWebDriver.echartsAs(meta: EChartsMeta, outputType: OutputType<X>): X {
    val url = echarts(meta)
    val encode = url.substring(url.indexOf(';') + 1, url.indexOf(','))
    val content = url.substringAfter(',')

    return when (encode) {
        "base64" -> outputType.convertFromBase64Png(content)
        else -> outputType.convertFromPngBytes(URLDecoder.decode(content, Charsets.UTF_8).toByteArray())
    }
}
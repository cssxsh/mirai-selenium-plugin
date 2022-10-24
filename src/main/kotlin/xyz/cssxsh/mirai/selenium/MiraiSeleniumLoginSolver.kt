package xyz.cssxsh.mirai.selenium

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import net.mamoe.mirai.*
import net.mamoe.mirai.console.extensions.*
import net.mamoe.mirai.utils.*
import org.openqa.selenium.devtools.v85.network.*
import org.openqa.selenium.devtools.v85.network.model.*
import xyz.cssxsh.selenium.*
import java.util.*
import kotlin.coroutines.*

public object MiraiSeleniumLoginSolver : LoginSolver(), BotConfigurationAlterer {
    private const val TIMEOUT_PROPERTY = "xyz.cssxsh.mirai.solver.timeout"
    override val isSliderCaptchaSupported: Boolean = true

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        return useRemoteWebDriver(DriverConfig) { driver ->
            // open image
            driver.get("data:image/png;base64,${Base64.getMimeEncoder().encodeToString(data)}")
            // alert
            driver.executeScript("""return prompt("请输入验证码")""")?.toString()
        }
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? {
        val start = System.currentTimeMillis()
        val timeout = System.getProperty(TIMEOUT_PROPERTY)?.toLong() ?: 600_000L
        return useRemoteWebDriver(DriverConfig) { driver ->
            driver.setDeviceMetrics(400, 700, 0, true)

            var ticket: String? = null
            var request: RequestId? = null

            driver.devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))
            driver.devTools.addListener(Network.responseReceived()) { data ->
                if ("cap_union_new_verify" in data.response.url) {
                    request = data.requestId
                }
            }
            driver.get(url)
            while (coroutineContext.isActive) {
                if (request != null) {
                    val response = driver.devTools.send(Network.getResponseBody(request))
                    val text = if (response.base64Encoded) {
                        Base64.getMimeDecoder().decode(response.body).toString()
                    } else {
                        response.body
                    }
                    val json = Json.parseToJsonElement(text)
                    ticket = json.jsonObject["ticket"]?.jsonPrimitive?.content
                    if (ticket.isNullOrEmpty()) {
                        request = null
                    } else {
                        break
                    }
                }
                delay(1000)

                if (System.currentTimeMillis() - start > timeout) break
            }

            ticket
        }
    }

    @Deprecated(
        "Please use onSolveDeviceVerification instead",
        replaceWith = ReplaceWith("onSolveDeviceVerification(bot, url, null)"),
        level = DeprecationLevel.WARNING
    )
    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String? {
        val start = System.currentTimeMillis()
        val timeout = System.getProperty(TIMEOUT_PROPERTY)?.toLong() ?: 600_000L

        return useRemoteWebDriver(DriverConfig) { driver ->
            driver.setDeviceMetrics(400, 700, 0, true)
            driver.get(url)
            driver.executeScript("""return alert("处理完成后请关闭所有窗口")""")

            while (coroutineContext.isActive) {
                if (driver.windowHandles.isNullOrEmpty()) break
                delay(1000)

                if (System.currentTimeMillis() - start > timeout) break
            }

            null
        }
    }

    override fun alterConfiguration(botId: Long, configuration: BotConfiguration): BotConfiguration {
        configuration.loginSolver = this

        return configuration
    }

    private object DriverConfig : RemoteWebDriverConfig {
        override val userAgent: String = UserAgents.QQ
        override val headless: Boolean = false
        override val browser: String get() = RemoteWebDriverConfig.instances.firstOrNull()?.browser.orEmpty()
    }
}
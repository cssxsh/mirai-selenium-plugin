package xyz.cssxsh.selenium

import org.openqa.selenium.remote.*
import org.openqa.selenium.remote.service.*
import java.util.*

internal object DriverCache : MutableMap<RemoteWebDriver, DriverService> by HashMap() {

    fun status() = entries.map { (driver, service) ->
        "${driver::class.simpleName}(session=${driver.sessionId}, url=${service.url}, process=${service.getProcess()})"
    }
}
package xyz.cssxsh.selenium

import org.openqa.selenium.remote.*
import org.openqa.selenium.remote.service.*
import java.util.*

public object DriverCache : MutableMap<RemoteWebDriver, DriverService> by HashMap() {

    public fun status(): List<String> = entries.map { (driver, service) ->
        "${driver::class.simpleName}(session=${driver.sessionId}, url=${service.url}, process=${service.getProcess()})"
    }

    internal val DESTROY_HANDLE: (RemoteWebDriver, DriverService) -> Unit = { driver, service ->
        driver.quit()
        service.stop()
    }

    @JvmOverloads
    public fun destroy(enable: Boolean, block: (RemoteWebDriver, DriverService) -> Unit = DESTROY_HANDLE) {
        entries.removeIf { (driver, service) ->
            if (enable && driver.sessionId != null && service.isRunning) return@removeIf false
            block(driver, service)
            true
        }
    }
}
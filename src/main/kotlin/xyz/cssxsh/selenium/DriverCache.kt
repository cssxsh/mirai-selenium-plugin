package xyz.cssxsh.selenium

import org.openqa.selenium.remote.*
import org.openqa.selenium.remote.service.*
import java.io.IOException
import java.util.concurrent.*

/**
 * 驱动服务缓存，用于超时强制关闭
 */
public object DriverCache : MutableMap<RemoteWebDriver, DriverService> by ConcurrentHashMap() {

    /**
     * 缓存中驱动状态
     */
    public fun status(): List<String> = entries.map { (driver, service) ->
        "${driver::class.simpleName}(session=${driver.sessionId}, url=${service.url}, process=${service.getProcess()})"
    }

    private val DESTROY_HANDLE: (RemoteWebDriver, DriverService) -> Boolean = { driver, service ->
        try {
            driver.quit()
            service.stop()
            true
        } catch (_: IOException) {
            false
        }
    }

    /**
     * 销毁
     * @param enable 检查是否运行中
     * @param block 销毁操作
     * @see DESTROY_HANDLE
     */
    @JvmOverloads
    public fun destroy(enable: Boolean, block: (RemoteWebDriver, DriverService) -> Boolean = DESTROY_HANDLE) {
        entries.removeIf { (driver, service) ->
            if (enable && driver.sessionId != null && service.isRunning) {
                false
            } else {
                block(driver, service)
            }
        }
    }
}
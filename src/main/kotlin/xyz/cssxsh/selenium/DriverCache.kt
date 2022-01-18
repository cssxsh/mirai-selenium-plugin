package xyz.cssxsh.selenium

import org.openqa.selenium.remote.*
import org.openqa.selenium.remote.service.*
import java.util.*

internal object DriverCache : MutableMap<RemoteWebDriver, DriverService> by HashMap() {
    override fun toString(): String {
        return entries.joinToString(separator = ", ", prefix = "{ ", postfix = " }") { "${it.key}: ${it.value}" }
    }
}
package xyz.cssxsh.selenium

import kotlinx.serialization.json.*
import org.asynchttpclient.Dsl.asyncHttpClient
import org.asynchttpclient.uri.Uri
import org.openqa.selenium.devtools.*
import org.openqa.selenium.devtools.idealized.*
import org.openqa.selenium.devtools.idealized.log.model.*
import org.openqa.selenium.devtools.idealized.target.model.*
import org.openqa.selenium.remote.RemoteWebDriver

/**
 * [devtools-protocol](https://chromedevtools.github.io/devtools-protocol)
 * @see HasDevTools
 * @see DevTools.session
 */
public inline val RemoteWebDriver.devTools: DevTools get() = (this as HasDevTools).devTools.apply { session() }

/**
 * @see DevTools.connection
 */
internal fun RemoteWebDriver.cdp(): ChromeDevToolsProtocol {
    val ws = Uri.create(capabilities.getCapability("se:cdp") as String)
    val json = asyncHttpClient().prepareGet("http://${ws.host}//json/protocol")
        .execute()
        .get()
        .responseBody

    return Json.decodeFromString(ChromeDevToolsProtocol.serializer(), json)
}

/**
 * firefox unsupported [Log.clear](https://chromedevtools.github.io/devtools-protocol/tot/Log/#method-clear)
 * @see DevTools.createSessionIfThereIsNotOne
 */
public fun DevTools.session(): SessionID {
    try {
        createSessionIfThereIsNotOne()
    } catch (ignore: DevToolsException) {
        if ("Log.clear" !in ignore.message.orEmpty()) {
            throw ignore
        }
    }
    return cdpSession
}

// region Browser

/**
 * [Browser.getVersion](https://chromedevtools.github.io/devtools-protocol/tot/Browser/#method-getVersion)
 * @see HasDevTools
 */
public fun RemoteWebDriver.browser(): Map<String, String> = devTools.send(Command("Browser.getVersion", emptyMap()))

// endregion

// region Domains

/**
 * Events
 * @see Domains
 */
public fun RemoteWebDriver.events(): Events<*, *> = devTools.domains.events()

/**
 * Javascript
 * @see Domains
 */
public fun RemoteWebDriver.javascript(): Javascript<*, *> = devTools.domains.javascript()

/**
 * [Network](https://chromedevtools.github.io/devtools-protocol/tot/Network)
 * @see Domains
 */
public fun RemoteWebDriver.network(): Network<*, *> = devTools.domains.network()

/**
 * [Log.entryAdded](https://chromedevtools.github.io/devtools-protocol/tot/Log/#event-entryAdded)
 * @see Domains
 */
public fun DevTools.addLogListener(handler: (LogEntry) -> Unit): Unit = with(domains.log()) {
    send(enable())
    addListener(entryAdded(), handler)
}

// endregion

// region Emulation

/**
 * [Emulation.setDeviceMetricsOverride](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setDeviceMetricsOverride)
 * @see HasDevTools
 */
public fun RemoteWebDriver.setDeviceMetrics(width: Int, height: Int, deviceScaleFactor: Number, mobile: Boolean) {
    devTools.send(
        Command<Void>(
            "Emulation.setDeviceMetricsOverride",
            mapOf("width" to width, "height" to height, "deviceScaleFactor" to deviceScaleFactor, "mobile" to mobile)
        )
    )
}

/**
 * [Emulation.setScrollbarsHidden](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setScrollbarsHidden)
 * @see HasDevTools
 */
public fun RemoteWebDriver.setScrollbarsHidden(hidden: Boolean = true) {
    devTools.send(Command<Void>("Emulation.setScrollbarsHidden", mapOf("hidden" to hidden)))
}

// endregion
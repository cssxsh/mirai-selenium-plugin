package xyz.cssxsh.selenium

import org.openqa.selenium.devtools.*
import org.openqa.selenium.devtools.v97.browser.*
import org.openqa.selenium.devtools.v97.emulation.*
import org.openqa.selenium.devtools.v97.network.*
import org.openqa.selenium.remote.RemoteWebDriver
import java.util.*


// region Browser

/**
 * [Browser.getVersion](https://chromedevtools.github.io/devtools-protocol/tot/Browser/#method-getVersion)
 * @see HasDevTools
 */
public fun RemoteWebDriver.getVersion(): Browser.GetVersionResponse {
    this as HasDevTools
    devTools.createSessionIfThereIsNotOne()
    return devTools.send(Browser.getVersion())
}

// endregion

// region Network

/**
 * [Network.setUserAgentOverride](https://chromedevtools.github.io/devtools-protocol/tot/Network/#method-setUserAgentOverride)
 * @see HasDevTools
 */
public fun RemoteWebDriver.setUserAgent(userAgent: String) {
    this as HasDevTools
    devTools.createSessionIfThereIsNotOne()
    devTools.send(Network.setUserAgentOverride(userAgent, Optional.empty(), Optional.empty(), Optional.empty()))
}

// endregion

// region Emulation

/**
 * [Emulation.setDeviceMetricsOverride](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setDeviceMetricsOverride)
 * @see HasDevTools
 */
public fun RemoteWebDriver.setDeviceMetrics(width: Int, height: Int, deviceScaleFactor: Number, mobile: Boolean) {
    this as HasDevTools
    devTools.createSessionIfThereIsNotOne()
    devTools.send(Emulation.setDeviceMetricsOverride(
        width, height, deviceScaleFactor, mobile,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
    ))
}

/**
 * [Emulation.setScrollbarsHidden](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setScrollbarsHidden)
 * @see HasDevTools
 */
public fun RemoteWebDriver.setScrollbarsHidden(hidden: Boolean = true) {
    this as HasDevTools
    devTools.createSessionIfThereIsNotOne()
    devTools.send(Emulation.setScrollbarsHidden(hidden))
}

// endregion
package xyz.cssxsh.selenium

import org.junit.jupiter.api.Test

internal class SeleniumDevToolsKtTest : SeleniumTest() {

    @Test
    fun getVersion(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.cdp()
        println(driver.devTools.session())
        println(driver.browser())
    }

    @Test
    fun network(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.network().setUserAgent("curl/3.0")
    }

    @Test
    fun setDeviceMetrics(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.setDeviceMetrics(1080, 960, 0, true)
    }

    @Test
    fun setScrollbarsHidden(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.setScrollbarsHidden()
    }
}
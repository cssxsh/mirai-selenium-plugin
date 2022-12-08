package xyz.cssxsh.selenium

import org.junit.jupiter.api.Test

internal class SeleniumDevToolsKtTest : SeleniumTest() {

    @Test
    fun getVersion(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.cdp()
        println(driver.devTools.session())
        println(driver.browser())
        driver.devTools.close()
    }

    @Test
    fun network(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.network().setUserAgent("curl/3.0")
        driver.devTools.close()
    }

    @Test
    fun setDeviceMetrics(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.setDeviceMetrics(1080, 960, 0, true)
        driver.devTools.close()
    }

    @Test
    fun setScrollbarsHidden(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.setScrollbarsHidden()
        driver.devTools.close()
    }
}
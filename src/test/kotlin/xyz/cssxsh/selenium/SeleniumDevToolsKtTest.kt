package xyz.cssxsh.selenium

import org.junit.jupiter.api.Test

internal class SeleniumDevToolsKtTest : SeleniumTest() {

    @Test
    fun getVersion(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        driver.cdp()
        try {
            println(driver.devTools.session())
            println(driver.browser())
        } finally {
            driver.devTools.close()
        }
    }

    @Test
    fun network(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        try {
            driver.network().setUserAgent("curl/3.0")
        } finally {
            driver.devTools.close()
        }
    }

    @Test
    fun setDeviceMetrics(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        try {
            driver.setDeviceMetrics(1080, 960, 0, true)
        } finally {
            driver.devTools.close()
        }
    }

    @Test
    fun setScrollbarsHidden(): Unit = testRemoteWebDriver { browser, driver ->
        println(browser)
        try {
            driver.setScrollbarsHidden()
        } finally {
            driver.devTools.close()
        }
    }
}
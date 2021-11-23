package xyz.cssxsh.selenium

import kotlinx.coroutines.*
import kotlin.coroutines.*
import org.openqa.selenium.remote.http.*

typealias SeleniumHttpClient = HttpClient

typealias SeleniumHttpClientConfig = ClientConfig

typealias SeleniumHttpClientFactory = HttpClient.Factory

typealias SeleniumHttpClientName = HttpClientName

typealias SeleniumHttpRequest = HttpRequest

typealias SeleniumHttpResponse = HttpResponse

typealias SeleniumWebListener = WebSocket.Listener

typealias SeleniumUserAgent = AddSeleniumUserAgent

typealias SeleniumHttpContents = Contents


internal var KtorContext: CoroutineContext = Dispatchers.IO

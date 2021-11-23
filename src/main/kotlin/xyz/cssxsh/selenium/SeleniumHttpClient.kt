package xyz.cssxsh.selenium

import kotlinx.coroutines.*

typealias SeleniumHttpClient = org.openqa.selenium.remote.http.HttpClient

typealias SeleniumHttpClientConfig = org.openqa.selenium.remote.http.ClientConfig

typealias SeleniumHttpClientFactory = org.openqa.selenium.remote.http.HttpClient.Factory

typealias SeleniumHttpClientName = org.openqa.selenium.remote.http.HttpClientName

typealias SeleniumHttpRequest = org.openqa.selenium.remote.http.HttpRequest

typealias SeleniumHttpResponse = org.openqa.selenium.remote.http.HttpResponse

typealias SeleniumWebListener = org.openqa.selenium.remote.http.WebSocket.Listener

typealias SeleniumUserAgent = org.openqa.selenium.remote.http.AddSeleniumUserAgent

typealias SeleniumHttpContents = org.openqa.selenium.remote.http.Contents


internal var KtorContext = Dispatchers.IO

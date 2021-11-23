package xyz.cssxsh.selenium

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.http.HttpMethod
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.net.URI

class KtorHttpClient(private val config: SeleniumHttpClientConfig) : SeleniumHttpClient {

    @OptIn(KtorExperimentalAPI::class)
    val client = HttpClient(OkHttp) {
        engine {
            proxy = config.proxy()
        }
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectionTimeout().toMillis()
            requestTimeoutMillis = config.readTimeout().toMillis()
            socketTimeoutMillis = config.readTimeout().toMillis()
        }
        install(WebSockets)
        install(UserAgent) {
            agent = SeleniumUserAgent.USER_AGENT
        }
    }

    @OptIn(InternalAPI::class)
    private fun HttpRequestBuilder.takeFrom(request: SeleniumHttpRequest, base: URI) = apply {
        url {
            takeFrom(base.resolve(request.uri))
            for (name in request.queryParameterNames) {
                parameters.appendAll(name, request.getQueryParameters(name))
            }
        }
        method = HttpMethod.parse(request.method.name)
        headers {
            for (name in request.headerNames) {
                if (name in HttpHeaders.UnsafeHeadersList) continue
                appendAll(name, request.getHeaders(name))
            }
        }
        for (name in request.attributeNames) {
            attributes.put(AttributeKey(name), request.getAttribute(name))
        }
        if (method == HttpMethod.Post) {
            body = ByteArrayContent(
                bytes = request.content.get().readAllBytes(),
                contentType = request.getHeader(HttpHeaders.ContentType)?.let(ContentType::parse)
            )
        }
    }

    private fun HttpResponse.toSeleniumHttpResponse() = SeleniumHttpResponse().also { response ->
        response.status = status.value
        response.content = SeleniumHttpContents.memoize { content.toInputStream() }
        headers.forEach { name, list ->
            for (value in list) {
                response.addHeader(name, value)
            }
        }
    }

    override fun execute(request: SeleniumHttpRequest?): SeleniumHttpResponse = runBlocking(KtorContext) {
        requireNotNull(request) { "SeleniumHttpRequest Not Null" }
        client.request<HttpResponse> { takeFrom(request, config.baseUri()) }.toSeleniumHttpResponse()
    }

    override fun openSocket(request: SeleniumHttpRequest?, listener: SeleniumWebListener?) = runBlocking(KtorContext) {
        requireNotNull(request) { "SeleniumHttpRequest Not Null" }
        requireNotNull(listener) { "SeleniumWebSocket.Listener Not Null" }
        val session = client.webSocketSession {
            takeFrom(request, config.baseUri())
            url {
                protocol = when (protocol) {
                    URLProtocol.HTTPS, URLProtocol.WSS -> URLProtocol.WSS
                    URLProtocol.HTTP, URLProtocol.WS -> URLProtocol.WS
                    else -> throw IllegalArgumentException("$protocol Not WebSocket")
                }
            }
        }
        KtorWebSocket(session, listener)
    }

    @SeleniumHttpClientName("ktor")
    class Factory : SeleniumHttpClientFactory {

        private val clients = mutableListOf<HttpClient>()

        override fun cleanupIdleClients() = synchronized(clients) {
            for (client in clients) {
                client.close()
            }
            clients.clear()
        }

        override fun createClient(config: SeleniumHttpClientConfig?): SeleniumHttpClient {
            requireNotNull(config) { "ClientConfig Not Null" }
            return KtorHttpClient(config).apply {
                synchronized(clients) {
                    clients.add(client)
                }
            }
        }
    }
}
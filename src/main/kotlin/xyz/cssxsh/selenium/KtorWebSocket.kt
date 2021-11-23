package xyz.cssxsh.selenium

import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import org.openqa.selenium.remote.http.*

class KtorWebSocket(private val session: DefaultClientWebSocketSession, private val listener: WebSocket.Listener) :
    WebSocket {

    init {
        session.launch(KtorContext) {
            while (isActive) {
                try {
                    when (val frame = session.incoming.receive()) {
                        is Frame.Binary -> {
                            listener.onBinary(frame.data)
                        }
                        is Frame.Text -> {
                            listener.onText(frame.readText())
                        }
                        is Frame.Close -> {
                            val (code, reason) = requireNotNull(session.closeReason.await()) { "CloseReason Not Null" }
                            listener.onClose(code.toInt(), reason)
                            return@launch
                        }
                        else -> {
                        }
                    }
                } catch (cause: Throwable) {
                    listener.onError(cause)
                    return@launch
                }
            }
        }
    }

    override fun close(): Unit = runBlocking(KtorContext) { session.close() }

    override fun send(message: Message?): WebSocket = apply {
        runBlocking(KtorContext) {
            when (message) {
                is BinaryMessage -> session.send(message.data())
                is TextMessage -> session.send(message.text())
                is CloseMessage -> session.close()
                else -> {
                }
            }
        }
    }

    override fun sendBinary(data: ByteArray?): WebSocket = apply {
        if (data == null) return@apply
        runBlocking(KtorContext) {
            session.send(data)
        }
    }

    override fun sendText(data: CharSequence?): WebSocket = apply {
        if (data == null) return@apply
        runBlocking(KtorContext) {
            session.send(StringBuilder(data).toString())
        }
    }
}
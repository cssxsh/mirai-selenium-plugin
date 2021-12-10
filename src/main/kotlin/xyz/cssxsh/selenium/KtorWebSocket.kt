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
                        else -> Unit
                    }
                } catch (cause: Throwable) {
                    listener.onError(cause)
                    return@launch
                }
            }
        }
    }

    override fun close(): Unit = runBlocking(KtorContext) { session.close() }

    override fun send(message: Message?): KtorWebSocket {
        runBlocking(KtorContext) {
            when (message) {
                is BinaryMessage -> session.send(message.data())
                is TextMessage -> session.send(message.text())
                is CloseMessage -> session.close()
                else -> Unit
            }
        }
        return this
    }

    override fun sendBinary(data: ByteArray?): KtorWebSocket {
        if (data != null) runBlocking(KtorContext) {
            session.send(data)
        }
        return this
    }

    override fun sendText(data: CharSequence?): KtorWebSocket {
        if (data != null) runBlocking(KtorContext) {
            session.send(StringBuilder(data).toString())
        }
        return this
    }
}
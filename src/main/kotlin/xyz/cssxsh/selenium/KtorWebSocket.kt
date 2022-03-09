package xyz.cssxsh.selenium

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import org.openqa.selenium.remote.http.*

internal class KtorWebSocket(private val session: DefaultWebSocketSession, private val listener: WebSocket.Listener) :
    WebSocket {

    init {
        session.launch(SeleniumContext) {
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
                        else -> throw UnsupportedOperationException("Frame: ${frame::class.qualifiedName}")
                    }
                } catch (cause: Throwable) {
                    if (session.closeReason.isCompleted) {
                        val (code, reason) = requireNotNull(session.closeReason.await()) { "CloseReason Not Null" }
                        listener.onClose(code.toInt(), reason)
                    } else {
                        listener.onError(cause)
                    }
                    return@launch
                }
            }
        }
    }

    override fun close() {
        session.launch(SeleniumContext) {
            session.close()
        }
    }

    override fun send(message: Message): KtorWebSocket {
        session.launch(SeleniumContext) {
            when (message) {
                is BinaryMessage -> session.send(message.data())
                is TextMessage -> session.send(message.text())
                is CloseMessage -> session.close()
                else -> throw UnsupportedOperationException("Message: ${message::class.qualifiedName}")
            }
        }
        return this
    }

    override fun sendBinary(data: ByteArray): KtorWebSocket {
        session.launch(SeleniumContext) {
            session.send(data)
        }
        return this
    }

    override fun sendText(data: CharSequence): KtorWebSocket {
        session.launch(SeleniumContext) {
            session.send(StringBuilder(data).toString())
        }
        return this
    }
}
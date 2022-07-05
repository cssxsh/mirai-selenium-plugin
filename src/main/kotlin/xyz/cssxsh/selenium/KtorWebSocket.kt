package xyz.cssxsh.selenium

import io.ktor.websocket.*
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
                        return@launch
                    } else {
                        listener.onError(cause)
                    }
                }
            }
        }
    }

    override fun close() {
        session.launch(SeleniumContext) {
            try {
                session.close()
            } catch (cause: Throwable) {
                listener.onError(cause)
            }
        }
    }

    override fun send(message: Message): KtorWebSocket {
        session.launch(SeleniumContext) {
            try {
                when (message) {
                    is BinaryMessage -> session.send(message.data())
                    is TextMessage -> session.send(message.text())
                    is CloseMessage -> session.close(CloseReason(message.code().toShort(), message.reason()))
                    else -> throw UnsupportedOperationException("Message: ${message::class.qualifiedName}")
                }
            } catch (cause: Throwable) {
                listener.onError(cause)
            }
        }
        return this
    }

    override fun sendBinary(data: ByteArray): KtorWebSocket {
        session.launch(SeleniumContext) {
            try {
                session.send(data)
            } catch (cause: Throwable) {
                listener.onError(cause)
            }
        }
        return this
    }

    override fun sendText(data: CharSequence): KtorWebSocket {
        session.launch(SeleniumContext) {
            try {
                session.send(data.toString())
            } catch (cause: Throwable) {
                listener.onError(cause)
            }
        }
        return this
    }
}
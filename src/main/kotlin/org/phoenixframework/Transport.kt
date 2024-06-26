/*
 * Copyright (c) 2019 Daniel Rees <daniel.rees18@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.phoenixframework

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URL

/**
 * Interface that defines different types of Transport layers. A default {@link WebSocketTransport}
 * is provided which uses an OkHttp WebSocket to transport data between your Phoenix server.
 *
 * Future support may be added to provide your own custom Transport, such as a LongPoll
 */
interface Transport {

  /** Available ReadyStates of a {@link Transport}. */
  enum class ReadyState {

    /** The Transport is connecting to the server  */
    CONNECTING,

    /** The Transport is connected and open */
    OPEN,

    /** The Transport is closing */
    CLOSING,

    /** The Transport is closed */
    CLOSED
  }

  /** The state of the Transport. See {@link ReadyState} */
  val readyState: ReadyState

  /** Called when the Transport opens */
  var onOpen: (() -> Unit)?
  /** Called when the Transport receives an error */
  var onError: ((Throwable, Response?) -> Unit)?
  /** Called each time the Transport receives a JSON message */
  var onJsonMessage: ((String) -> Unit)?
  /** Called each time the Transport receives a message */
  var onBinaryMessage: ((ByteString) -> Unit)?
  /** Called when the Transport closes */
  var onClose: ((Int) -> Unit)?

  /** Connect to the server */
  fun connect()

  /**
   * Disconnect from the Server
   *
   * @param code Status code as defined by <a
   * href="http://tools.ietf.org/html/rfc6455#section-7.4">Section 7.4 of RFC 6455</a>.
   * @param reason Reason for shutting down or {@code null}.
   */
  fun disconnect(code: Int, reason: String? = null)

  /**
   * Sends text to the Server
   */
  fun send(data: String)

  /**
   * Sends binary data to the Server
   */
  fun send(bytes: ByteString)
}

/**
 * A WebSocket implementation of a Transport that uses a WebSocket to facilitate sending
 * and receiving data.
 *
 * @param url: URL to connect to
 * @param okHttpClient: Custom client that can be pre-configured before connecting
 */
class WebSocketTransport(
  private val url: URL,
  private val okHttpClient: OkHttpClient
) :
    WebSocketListener(),
    Transport {

  internal var connection: WebSocket? = null

  override var readyState: Transport.ReadyState = Transport.ReadyState.CLOSED
  override var onOpen: (() -> Unit)? = null
  override var onError: ((Throwable, Response?) -> Unit)? = null
  override var onJsonMessage: ((String) -> Unit)? = null
  override var onBinaryMessage: ((ByteString) -> Unit)? = null
  override var onClose: ((Int) -> Unit)? = null

  override fun connect() {
    this.readyState = Transport.ReadyState.CONNECTING
    val request = Request.Builder().url(url).build()
    connection = okHttpClient.newWebSocket(request, this)
  }

  override fun disconnect(code: Int, reason: String?) {
    connection?.close(code, reason)
    connection = null
  }

  override fun send(data: String) {
    connection?.send(data)
  }

  override fun send(bytes: ByteString) {
    connection?.send(bytes)
  }

  //------------------------------------------------------------------------------
  // WebSocket Listener
  //------------------------------------------------------------------------------
  override fun onOpen(webSocket: WebSocket, response: Response) {
    this.readyState = Transport.ReadyState.OPEN
    this.onOpen?.invoke()
  }

  override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    // Set the state of the Transport as CLOSED since no more data will be received
    this.readyState = Transport.ReadyState.CLOSED

    // Invoke the onError callback, to inform of the error
    this.onError?.invoke(t, response)

    /*
      According to the OkHttp documentation, `onFailure` will be

      "Invoked when a web socket has been closed due to an error reading from or writing to the
      network. Both outgoing and incoming messages may have been lost. No further calls to this
      listener will be made."

      This means `onClose` will never be called which will never kick off the socket reconnect
      attempts.

      The JS WebSocket class calls `onError` and then `onClose` which will then trigger
      the reconnect logic inside of the PhoenixClient. In order to mimic this behavior and abstract
      this detail of OkHttp away from the PhoenixClient, the `WebSocketTransport` class should
      convert `onFailure` calls to an `onError` and `onClose` sequence.
     */
    this.onClose?.invoke(WS_CLOSE_ABNORMAL)
  }

  override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
    this.readyState = Transport.ReadyState.CLOSING
    webSocket.close(code, reason)
  }

  override fun onMessage(webSocket: WebSocket, text: String) {
    this.onJsonMessage?.invoke(text)
  }

  override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
    this.onBinaryMessage?.invoke(bytes)
  }

  override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    this.readyState = Transport.ReadyState.CLOSED
    this.onClose?.invoke(code)
  }
}
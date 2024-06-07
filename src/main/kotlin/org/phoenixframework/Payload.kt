package org.phoenixframework

import okio.ByteString
import okio.ByteString.Companion.toByteString

interface Payload {
}

data class JsonPayload(val body: Map<String, Any> = emptyMap()) : Payload

data class BinaryPayload(val body: ByteString) : Payload

fun Map<String, Any>.toPayload() = JsonPayload(this)

fun ByteString.toPayload() = BinaryPayload(this)

fun ByteArray.toPayload() = BinaryPayload(this.toByteString())
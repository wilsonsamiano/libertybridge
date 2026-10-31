package com.wilson.libertybridge.airplay

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/** 每次绑定生成独立身份，防止旧 accept 和旧清理作用于新 listener。 */
class AirPlayListenerIdentity(val generation: Int) {
    val id: Long = nextId.incrementAndGet()
    private companion object { val nextId = AtomicLong() }
}

data class AirPlayTcpAccepted(
    val listener: AirPlayListenerIdentity,
    val internal: Boolean,
    val acceptedAtNanos: Long,
)

internal fun isInternalAirPlayPeer(peer: InetAddress, local: InetAddress): Boolean =
    peer.isLoopbackAddress || peer == local ||
        runCatching { NetworkInterface.getByInetAddress(peer) != null }.getOrDefault(true)

/**
 * Accepts until a connection comes from [expected], the control-session peer; others are closed unread.
 * Java usually reports an IPv4-mapped peer as its IPv4 address, but some runtimes report the mapped
 * form (::ffff:127.0.0.1) instead, so the comparison normalizes mapped addresses on both sides
 * rather than trusting [InetAddress.equals]. A mapped form and its IPv4 address are the same peer;
 * ::1 and 127.0.0.1 stay distinct loopbacks and are never equated.
 */
internal fun ServerSocket.acceptFrom(expected: InetAddress, onRejected: (InetAddress?) -> Unit = {}): Socket {
    val wanted = normalizedHostAddress(expected)
    while (true) {
        val socket = accept()
        if (normalizedHostAddress(socket.inetAddress) == wanted) return socket
        onRejected(socket.inetAddress)
        runCatching { socket.close() }
    }
}

/** Unwraps an IPv4-mapped IPv6 address (::ffff:a.b.c.d) to its IPv4 form; anything else unchanged. */
internal fun normalizedHostAddress(address: InetAddress?): String? {
    val host = address?.hostAddress ?: return null
    val mapped = host.removePrefix("::ffff:").removePrefix("::FFFF:")
    return if (mapped != host && mapped.count { it == '.' } == 3) mapped else host
}

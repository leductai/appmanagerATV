package com.kaos.tvappmanager.platform.install

import com.kaos.tvappmanager.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ADB protocol client for silent install via localhost adbd.
 *
 * Connects to 127.0.0.1:5555 (adbd TCP), performs CNXN handshake, opens shell service
 * `pm install -r -S <size> <package>`, streams APK, parses stdout for "Success".
 *
 * Falls back to PackageInstaller if connection/auth fails or if disabled in settings.
 *
 * Note: If app is installed as system app with INSTALL_PACKAGES permission, can use
 * PackageInstaller.SessionParams.setInstallAsApex() for silent install without ADB,
 * but that's not required for this feature.
 */
class AdbLocalClient(private val logger: AppLogger) {

    private val host = "127.0.0.1"
    private val port = 5555
    private val connectTimeoutMs = 5000
    private val readTimeoutMs = 30000

    // ADB protocol constants
    private val A_CNXN = 0x4e584e43
    private val A_AUTH = 0x48545541
    private val A_OPEN = 0x4e45504f
    private val A_OKAY = 0x59414b4f
    private val A_WRTE = 0x45545257
    private val A_CLSE = 0x45534c43

    private val VERSION = 0x01000000
    private val MAX_DATA = 256 * 1024

    /**
     * Check if localhost adbd is reachable (for settings UI indicator).
     */
    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), 2000)
                true
            }
        } catch (e: Exception) {
            logger.log(TAG, "unreachable: ${e.message}")
            false
        }
    }

    /**
     * Silent install APK via pm install over ADB localhost.
     * Returns null on success, error message on failure (caller fallback to PackageInstaller).
     */
    suspend fun install(apkFile: File, packageName: String): String? = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // Handshake
            val connectPayload = "host::TvAppMgr\u0000"
            sendMessage(output, A_CNXN, VERSION, MAX_DATA, connectPayload)
            val resp = receiveMessage(input)
            when (resp.command) {
                A_CNXN -> logger.log(TAG, "connected")
                A_AUTH -> {
                    logger.log(TAG, "AUTH required (secure device), fallback")
                    return@withContext "ADB requires authentication"
                }
                else -> return@withContext "ADB unexpected response: ${resp.command.toHex()}"
            }

            // Open shell service for pm install
            val size = apkFile.length()
            // -S streams APK over stdin; do NOT append package name (pm reads it from the APK).
            val shellCmd = "exec:pm install -r -S $size"
            val localId = 1
            sendMessage(output, A_OPEN, localId, 0, shellCmd)
            val openResp = receiveMessage(input)
            if (openResp.command != A_OKAY) {
                return@withContext "ADB OPEN failed: ${openResp.command.toHex()}"
            }
            val remoteId = openResp.arg0

            logger.log(TAG, "streaming $size bytes")

            // Stream APK. adbd may interleave WRTE (shell output) before OKAY acks;
            // treat WRTE as output too and keep waiting for the OKAY of each chunk.
            val stdoutBuilder = StringBuilder()
            apkFile.inputStream().use { apkIn ->
                val buffer = ByteArray(MAX_DATA)
                while (true) {
                    val read = apkIn.read(buffer)
                    if (read <= 0) break
                    sendMessage(output, A_WRTE, localId, remoteId, buffer.copyOf(read))
                    // Await OKAY for this chunk; accept WRTE payloads meanwhile.
                    while (true) {
                        val msg = receiveMessage(input)
                        when (msg.command) {
                            A_OKAY -> break
                            A_WRTE -> {
                                stdoutBuilder.append(msg.data.decodeToString())
                                sendMessage(output, A_OKAY, localId, remoteId, "")
                            }
                            A_CLSE -> {
                                logger.log(TAG, "closed during stream")
                                return@withContext "ADB stream closed early: $stdoutBuilder"
                            }
                            else -> return@withContext "ADB ack unexpected: ${msg.command.toHex()}"
                        }
                    }
                }
            }

            // Send an empty WRTE to signal EOF on stdin. CLSE would close the whole
            // shell stream before pm can finish and return its output.
            sendMessage(output, A_WRTE, localId, remoteId, ByteArray(0))
            while (true) {
                val msg = receiveMessage(input)
                when (msg.command) {
                    A_WRTE -> {
                        stdoutBuilder.append(msg.data.decodeToString())
                        sendMessage(output, A_OKAY, localId, remoteId, "")
                    }
                    A_OKAY -> {
                        // OKAY may arrive when the stream finishes; just ignore.
                    }
                    A_CLSE -> {
                        logger.log(TAG, "shell closed")
                        break
                    }
                    else -> logger.log(TAG, "unexpected ${msg.command.toHex()}")
                }
            }

            val output_text = stdoutBuilder.toString().trim()
            logger.log(TAG, "pm output: $output_text")

            if (output_text.contains("Success", ignoreCase = true)) {
                logger.log(TAG, "install SUCCESS")
                null
            } else {
                "pm install failed: $output_text"
            }

        } catch (e: Exception) {
            logger.log(TAG, "exception: ${e.message}")
            "ADB connection failed: ${e.message}"
        } finally {
            socket?.close()
        }
    }

    private fun sendMessage(out: OutputStream, command: Int, arg0: Int, arg1: Int, payload: String) {
        sendMessage(out, command, arg0, arg1, payload.toByteArray(Charsets.UTF_8))
    }

    private fun sendMessage(out: OutputStream, command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val dataLen = data.size
        val dataCrc = if (dataLen > 0) checksum(data) else 0
        val magic = command xor 0xFFFFFFFF.toInt()

        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(dataLen)
        header.putInt(dataCrc)
        header.putInt(magic)

        out.write(header.array())
        if (dataLen > 0) out.write(data)
        out.flush()
    }

    private fun receiveMessage(input: InputStream): AdbMessage {
        val header = ByteArray(24)
        input.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val dataLen = buf.int
        val dataCrc = buf.int
        val magic = buf.int

        if (magic != (command xor 0xFFFFFFFF.toInt())) {
            throw IllegalStateException("ADB magic mismatch")
        }

        val data = if (dataLen > 0) {
            val payload = ByteArray(dataLen)
            input.readFully(payload)
            if (checksum(payload) != dataCrc) {
                throw IllegalStateException("ADB data CRC mismatch")
            }
            payload
        } else ByteArray(0)

        return AdbMessage(command, arg0, arg1, data)
    }

    /** ADB protocol checksum: plain sum of all bytes, not CRC32. */
    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += b.toInt() and 0xFF
        return sum
    }

    private fun Int.toHex() = "0x${this.toUInt().toString(16).uppercase()}"

    private fun InputStream.readFully(buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val read = read(buf, offset, buf.size - offset)
            if (read < 0) throw java.io.EOFException("ADB connection closed")
            offset += read
        }
    }

    private data class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private companion object {
        const val TAG = "AdbLocal"
    }
}

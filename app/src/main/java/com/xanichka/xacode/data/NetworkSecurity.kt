package com.xanichka.xacode.data

import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL

internal object NetworkSecurity {
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "::1", "[::1]")

    fun apiUrl(raw: String): URL {
        val uri = parse(raw)
        require(uri.scheme == "https" || (uri.scheme == "http" && isLoopbackHost(uri.host))) {
            "API должен использовать HTTPS. HTTP разрешён только для локального Ollama."
        }
        return uri.toURL()
    }

    fun publicDownloadUrl(raw: String): URL {
        val uri = parse(raw)
        require(uri.scheme == "https") { "Для загрузок разрешён только HTTPS" }
        val addresses = InetAddress.getAllByName(uri.host)
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
            "Загрузка из локальной или служебной сети запрещена"
        }
        return uri.toURL()
    }

    fun readLimited(input: InputStream?, maxBytes: Int): String {
        if (input == null) return ""
        return input.use { stream ->
            val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "Ответ сервера слишком большой" }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun parse(raw: String): URI {
        val uri = runCatching { URI(raw.trim()) }.getOrElse { error("Некорректный URL") }
        require(uri.host?.isNotBlank() == true && uri.userInfo == null && uri.fragment == null) {
            "Некорректный URL"
        }
        require(uri.port in -1..65535) { "Некорректный порт" }
        return uri
    }

    private fun isLoopbackHost(host: String?): Boolean = host?.lowercase() in loopbackHosts

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val bytes = address.address
        if (address is Inet4Address) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if (first == 0 || first == 10 || first == 127 || first >= 224) return false
            if (first == 100 && second in 64..127) return false
            if (first == 169 && second == 254) return false
            if (first == 172 && second in 16..31) return false
            if (first == 192 && second == 168) return false
            if (first == 198 && second in 18..19) return false
        }
        if (address is Inet6Address && ((bytes[0].toInt() and 0xfe) == 0xfc)) return false
        return true
    }
}

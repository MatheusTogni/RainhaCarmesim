package com.rainhacarmesim.app.network

/** Interpreta a saída do utilitário `ping` do Android (toybox/iputils). Puro e testável. */
object PingOutputParser {
    private val replyRegex = Regex("""icmp_seq=(\d+).*?time[=<]([\d.]+)\s*ms""")
    private val headerRegex = Regex("""^PING\s+(\S+)\s+\(([^)]+)\)""")
    private val summaryRegex = Regex("""(\d+)\s+packets transmitted,\s+(\d+)\s+(?:packets\s+)?received""")
    private val lossRegex = Regex("""([\d.]+)%\s+packet loss""")
    private val rttRegex = Regex("""=\s*([\d.]+)/([\d.]+)/([\d.]+)(?:/([\d.]+))?\s*ms""")

    data class Reply(val seq: Int, val timeMs: Double)

    data class Summary(
        val transmitted: Int,
        val received: Int,
        val lossPercent: Double,
        val minMs: Double?,
        val avgMs: Double?,
        val maxMs: Double?,
        val mdevMs: Double?,
    )

    fun parseReply(line: String): Reply? {
        val m = replyRegex.find(line) ?: return null
        val seq = m.groupValues[1].toIntOrNull() ?: return null
        val t = m.groupValues[2].toDoubleOrNull() ?: return null
        return Reply(seq, t)
    }

    /** Retorna o IP resolvido do cabeçalho "PING host (ip) ...". */
    fun parseResolvedAddress(line: String): String? = headerRegex.find(line.trim())?.groupValues?.get(2)

    fun parseSummary(lines: List<String>): Summary? {
        var transmitted: Int? = null
        var received: Int? = null
        var loss: Double? = null
        var min: Double? = null
        var avg: Double? = null
        var max: Double? = null
        var mdev: Double? = null
        for (line in lines) {
            val s = summaryRegex.find(line)
            if (s != null) {
                transmitted = s.groupValues[1].toIntOrNull()
                received = s.groupValues[2].toIntOrNull()
            }
            val l = lossRegex.find(line)
            if (l != null) loss = l.groupValues[1].toDoubleOrNull()
            if (line.contains("min/avg/max")) {
                val r = rttRegex.find(line)
                if (r != null) {
                    min = r.groupValues[1].toDoubleOrNull()
                    avg = r.groupValues[2].toDoubleOrNull()
                    max = r.groupValues[3].toDoubleOrNull()
                    mdev = r.groupValues[4].toDoubleOrNull()
                }
            }
        }
        val tx = transmitted ?: return null
        val rx = received ?: return null
        val lossValue = loss ?: if (tx > 0) (tx - rx) * 100.0 / tx else 0.0
        return Summary(tx, rx, lossValue, min, avg, max, mdev)
    }
}

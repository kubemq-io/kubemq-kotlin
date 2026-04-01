package io.kubemq.sdk.common

internal object ChannelDecoder {

    fun decodeChannelList(bodyBytes: ByteArray): List<ChannelInfo> {
        val json = String(bodyBytes, Charsets.UTF_8).trim()
        if (json.isEmpty() || json == "null" || json == "[]") {
            return emptyList()
        }
        return parseJsonArray(json)
    }

    private fun parseJsonArray(json: String): List<ChannelInfo> {
        val results = mutableListOf<ChannelInfo>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return emptyList()
        }
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) {
            return emptyList()
        }

        val objects = splitJsonObjects(inner)
        for (obj in objects) {
            val channelInfo = parseChannelObject(obj.trim())
            if (channelInfo != null) {
                results.add(channelInfo)
            }
        }
        return results
    }

    private fun splitJsonObjects(content: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in content.indices) {
            when (content[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects.add(content.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun parseChannelObject(json: String): ChannelInfo? {
        val fields = extractFields(json)
        return ChannelInfo(
            name = fields["name"]?.unquote() ?: return null,
            type = fields["type"]?.unquote() ?: "",
            lastActivity = fields["lastActivity"]?.toLongOrNull() ?: 0L,
            isActive = fields["isActive"]?.toBooleanStrictOrNull() ?: false,
            incoming = parseStatsObject(fields["incoming"] ?: "{}"),
            outgoing = parseStatsObject(fields["outgoing"] ?: "{}"),
        )
    }

    private fun parseStatsObject(json: String): ChannelStats {
        val fields = extractFields(json)
        return ChannelStats(
            messages = fields["messages"]?.toLongOrNull() ?: 0L,
            volume = fields["volume"]?.toLongOrNull() ?: 0L,
            waiting = fields["waiting"]?.toLongOrNull() ?: 0L,
            expired = fields["expired"]?.toLongOrNull() ?: 0L,
            delayed = fields["delayed"]?.toLongOrNull() ?: 0L,
        )
    }

    private fun extractFields(json: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val trimmed = json.trim()
        val content = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }

        var i = 0
        while (i < content.length) {
            // Find key
            val keyStart = content.indexOf('"', i)
            if (keyStart < 0) break
            val keyEnd = content.indexOf('"', keyStart + 1)
            if (keyEnd < 0) break
            val key = content.substring(keyStart + 1, keyEnd)

            // Find colon
            val colonIdx = content.indexOf(':', keyEnd + 1)
            if (colonIdx < 0) break

            // Find value
            val valueStart = findValueStart(content, colonIdx + 1)
            if (valueStart < 0) break

            val valueResult = extractValue(content, valueStart)
            fields[key] = valueResult.first
            i = valueResult.second
        }
        return fields
    }

    private fun findValueStart(content: String, from: Int): Int {
        var idx = from
        while (idx < content.length && content[idx].isWhitespace()) idx++
        return if (idx < content.length) idx else -1
    }

    private fun extractValue(content: String, start: Int): Pair<String, Int> {
        return when (content[start]) {
            '"' -> {
                val end = content.indexOf('"', start + 1)
                if (end < 0) {
                    Pair(content.substring(start), content.length)
                } else {
                    Pair(content.substring(start, end + 1), end + 1)
                }
            }
            '{' -> {
                var depth = 0
                var idx = start
                while (idx < content.length) {
                    when (content[idx]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                return Pair(content.substring(start, idx + 1), idx + 1)
                            }
                        }
                    }
                    idx++
                }
                Pair(content.substring(start), content.length)
            }
            '[' -> {
                var depth = 0
                var idx = start
                while (idx < content.length) {
                    when (content[idx]) {
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 0) {
                                return Pair(content.substring(start, idx + 1), idx + 1)
                            }
                        }
                    }
                    idx++
                }
                Pair(content.substring(start), content.length)
            }
            else -> {
                var idx = start
                while (idx < content.length && content[idx] != ',' && content[idx] != '}' && content[idx] != ']') {
                    idx++
                }
                Pair(content.substring(start, idx).trim(), idx)
            }
        }
    }

    private fun String.unquote(): String {
        val t = this.trim()
        return if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) {
            t.substring(1, t.length - 1)
        } else {
            t
        }
    }
}

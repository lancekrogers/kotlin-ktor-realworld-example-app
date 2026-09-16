package io.realworld.app.domain

data class Paging(val limit: Int, val offset: Long) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100

        fun parse(limit: String?, offset: String?): Paging {
            val l = if (limit == null) DEFAULT_LIMIT
                    else requireNotNull(limit.toIntOrNull()) { "limit must be an integer." }
            require(l in 1..MAX_LIMIT) { "limit must be between 1 and $MAX_LIMIT." }
            val o = if (offset == null) 0L
                    else requireNotNull(offset.toLongOrNull()) { "offset must be an integer." }
            require(o >= 0) { "offset must not be negative." }
            return Paging(l, o)
        }
    }
}

package io.realworld.app.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

object DbConfig {
    /**
     * `Server.createPgServer().start()` used to run here, binding H2's PostgreSQL-wire listener
     * (TCP 5435) on every startup. Nothing needed it — Hikari connects in-process over the JDBC
     * URL below — and it was never stopped, so it also collided with itself once more than one
     * test class started the app. Removed rather than gated: an unused listening socket is
     * attack surface with no upside.
     */
    fun setup(jdbcUrl: String, username: String, password: String) {
        val config = HikariConfig().also { config ->
            config.jdbcUrl = jdbcUrl
            config.username = username
            config.password = password
        }
        Database.connect(HikariDataSource(config))
    }
}

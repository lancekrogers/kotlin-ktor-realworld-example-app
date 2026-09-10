package io.realworld.app.web.rules

import io.ktor.server.engine.ConnectorType
import io.realworld.app.config.SERVER_PORT
import io.realworld.app.config.setup
import io.realworld.app.web.util.HttpUtil
import org.junit.rules.ExternalResource
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class AppRule : ExternalResource() {
    private val app = setup()
    lateinit var http: HttpUtil
    val port = app.environment.connectors.find { it.type == ConnectorType.HTTP }?.port ?: SERVER_PORT

    override fun before() {
        app.start()
        awaitPort(open = true)
        http = HttpUtil(port)
    }

    override fun after() {
        app.stop(500, 500, TimeUnit.MILLISECONDS)
        // The rule is per-test-method, so the next test rebinds the same fixed port. Waiting for
        // release here stops the following test racing the previous shutdown -- the cause of
        // intermittent "Connection refused" failures under the old fixed 500ms sleep.
        awaitPort(open = false)
    }

    /** Poll until the port reaches the wanted state, instead of sleeping and hoping. */
    private fun awaitPort(open: Boolean, timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isListening() == open) return
            TimeUnit.MILLISECONDS.sleep(50)
        }
        error("Timed out waiting for port $port to become ${if (open) "available" else "free"}.")
    }

    private fun isListening(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("localhost", port), 200) }
        true
    }.getOrDefault(false)
}

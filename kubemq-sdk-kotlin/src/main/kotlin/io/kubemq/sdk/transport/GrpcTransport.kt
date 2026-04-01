package io.kubemq.sdk.transport

import io.grpc.ClientInterceptors
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
import io.kubemq.sdk.common.Defaults
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.exception.toKubeMQException
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

internal class GrpcTransport(
    private val address: String,
    private val tlsConfig: TlsConfig?,
    private val maxReceiveSize: Int,
    private val keepAlive: Boolean,
    private val pingIntervalSeconds: Int,
    private val pingTimeoutSeconds: Int,
    private val tokenSupplier: () -> String?,
    private val unaryTimeoutMs: Long = 60_000L,
) : Transport {

    private var managedChannel: ManagedChannel? = null
    private var coroutineStub: kubemq.kubemqGrpcKt.kubemqCoroutineStub? = null
    private var asyncStub: kubemq.kubemqGrpc.kubemqStub? = null

    override val isReady: Boolean
        get() = managedChannel?.getState(false) == ConnectivityState.READY

    fun connect() {
        val resolvedAddress = if ("://" in address) address else "dns:///$address"

        val builder = NettyChannelBuilder.forTarget(resolvedAddress)
            .maxInboundMessageSize(maxReceiveSize)
            .flowControlWindow(Defaults.FLOW_CONTROL_WINDOW)

        if (tlsConfig != null) {
            builder.negotiationType(NegotiationType.TLS)
            builder.sslContext(buildSslContext(tlsConfig))
        } else {
            builder.negotiationType(NegotiationType.PLAINTEXT)
        }

        if (keepAlive) {
            builder.keepAliveTime(pingIntervalSeconds.toLong(), TimeUnit.SECONDS)
            builder.keepAliveTimeout(pingTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            builder.keepAliveWithoutCalls(true)
        }

        managedChannel = builder.build()

        val authInterceptor = AuthInterceptor(tokenSupplier, isTls = tlsConfig != null)
        val interceptedChannel = ClientInterceptors.intercept(managedChannel, authInterceptor)

        coroutineStub = kubemq.kubemqGrpcKt.kubemqCoroutineStub(interceptedChannel)
        asyncStub = kubemq.kubemqGrpc.newStub(interceptedChannel)
    }

    override suspend fun ping(): ServerInfo {
        try {
            val result = getUnaryStub()
                .ping(kubemq.Kubemq.Empty.getDefaultInstance())
            return ServerInfo(
                host = result.host,
                version = result.version,
                serverStartTime = result.serverStartTime,
                serverUpTimeSeconds = result.serverUpTimeSeconds,
            )
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("ping")
        }
    }

    override fun getUnaryStub(): kubemq.kubemqGrpcKt.kubemqCoroutineStub =
        (coroutineStub
            ?: throw KubeMQException.Connection("Transport not connected", operation = "getUnaryStub"))
            .withDeadlineAfter(unaryTimeoutMs, TimeUnit.MILLISECONDS)

    override fun getStreamingStub(): kubemq.kubemqGrpcKt.kubemqCoroutineStub =
        coroutineStub
            ?: throw KubeMQException.Connection("Transport not connected", operation = "getStreamingStub")

    override fun getAsyncStub(): kubemq.kubemqGrpc.kubemqStub =
        asyncStub
            ?: throw KubeMQException.Connection("Transport not connected", operation = "getAsyncStub")

    override fun close() {
        managedChannel?.let { ch ->
            ch.shutdown()
            if (!ch.awaitTermination(Defaults.SHUTDOWN_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)) {
                ch.shutdownNow()
            }
        }
        managedChannel = null
        coroutineStub = null
        asyncStub = null
    }

    private fun buildSslContext(config: TlsConfig): SslContext {
        val sslBuilder = SslContextBuilder.forClient()
        sslBuilder.protocols("TLSv1.3", "TLSv1.2")

        if (config.insecureSkipVerify) {
            sslBuilder.trustManager(
                io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE
            )
        } else {
            if (config.caCertPem.isNotEmpty()) {
                sslBuilder.trustManager(ByteArrayInputStream(config.caCertPem))
            } else if (config.caCertFile.isNotBlank()) {
                sslBuilder.trustManager(File(config.caCertFile))
            }
        }

        if (config.certPem.isNotEmpty() && config.keyPem.isNotEmpty()) {
            sslBuilder.keyManager(
                ByteArrayInputStream(config.certPem),
                ByteArrayInputStream(config.keyPem),
            )
        } else if (config.certFile.isNotBlank() && config.keyFile.isNotBlank()) {
            sslBuilder.keyManager(File(config.certFile), File(config.keyFile))
        }

        return sslBuilder.build()
    }
}

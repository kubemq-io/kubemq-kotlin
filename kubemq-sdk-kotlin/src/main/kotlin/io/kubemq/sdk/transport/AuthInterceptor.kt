package io.kubemq.sdk.transport

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

internal class AuthInterceptor(
    private val tokenSupplier: () -> String?,
    private val isTls: Boolean = false,
) : ClientInterceptor {

    companion object {
        private val AUTH_KEY: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        private val log = Logger.getLogger(AuthInterceptor::class.java.name)
    }

    private val plaintextWarningLogged = AtomicBoolean(false)

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                val token = tokenSupplier()
                if (!token.isNullOrEmpty()) {
                    if (!isTls && plaintextWarningLogged.compareAndSet(false, true)) {
                        log.warning("Auth token sent over plaintext connection. Enable TLS for security.")
                    }
                    headers.put(AUTH_KEY, token)
                }
                super.start(responseListener, headers)
            }
        }
    }
}

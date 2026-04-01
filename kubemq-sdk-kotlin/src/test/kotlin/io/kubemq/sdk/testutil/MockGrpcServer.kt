package io.kubemq.sdk.testutil

import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import java.io.Closeable
import java.util.UUID

class MockGrpcServer(
    private val server: Server,
    val channel: ManagedChannel,
) : Closeable {
    companion object {
        fun create(service: io.grpc.BindableService): MockGrpcServer {
            val name = "test-${UUID.randomUUID()}"
            val server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start()
            val channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build()
            return MockGrpcServer(server, channel)
        }
    }

    override fun close() {
        channel.shutdownNow()
        server.shutdownNow()
    }
}

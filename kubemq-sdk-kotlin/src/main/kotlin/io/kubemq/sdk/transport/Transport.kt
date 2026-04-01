package io.kubemq.sdk.transport

import io.kubemq.sdk.common.ServerInfo
import java.io.Closeable

internal interface Transport : Closeable {
    suspend fun ping(): ServerInfo
    @Deprecated("Use getUnaryStub() or getStreamingStub()", ReplaceWith("getUnaryStub()"))
    fun getCoroutineStub(): kubemq.kubemqGrpcKt.kubemqCoroutineStub = getUnaryStub()
    fun getUnaryStub(): kubemq.kubemqGrpcKt.kubemqCoroutineStub
    fun getStreamingStub(): kubemq.kubemqGrpcKt.kubemqCoroutineStub
    fun getAsyncStub(): kubemq.kubemqGrpc.kubemqStub
    val isReady: Boolean
    override fun close()
}

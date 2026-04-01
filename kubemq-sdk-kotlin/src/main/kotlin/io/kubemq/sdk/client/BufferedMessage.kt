package io.kubemq.sdk.client

internal interface BufferedMessage {
    val grpcRequest: Any
    val replayAction: suspend (KubeMQClient) -> Unit
}

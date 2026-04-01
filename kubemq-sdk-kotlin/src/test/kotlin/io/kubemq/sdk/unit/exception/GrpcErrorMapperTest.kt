package io.kubemq.sdk.unit.exception

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kubemq.sdk.exception.ErrorCategory
import io.kubemq.sdk.exception.ErrorCode
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.exception.toKubeMQException

class GrpcErrorMapperTest : FunSpec({

    fun sre(code: Status.Code, desc: String? = null): StatusRuntimeException {
        val status = Status.fromCode(code).let {
            if (desc != null) it.withDescription(desc) else it
        }
        return StatusRuntimeException(status)
    }

    test("OK maps to Server exception") {
        val ex = sre(Status.Code.OK).toKubeMQException("op")
        ex.shouldBeInstanceOf<KubeMQException.Server>()
        ex.operation shouldBe "op"
    }

    test("CANCELLED with isClientCancelled=true maps to ClientClosed") {
        val ex = sre(Status.Code.CANCELLED).toKubeMQException("op", isClientCancelled = true)
        ex.shouldBeInstanceOf<KubeMQException.ClientClosed>()
    }

    test("CANCELLED with isClientCancelled=false maps to Server") {
        val ex = sre(Status.Code.CANCELLED).toKubeMQException("op", isClientCancelled = false)
        ex.shouldBeInstanceOf<KubeMQException.Server>()
        ex.code shouldBe ErrorCode.CANCELLED
    }

    test("UNKNOWN maps to Server") {
        val ex = sre(Status.Code.UNKNOWN, "some error").toKubeMQException("op")
        ex.shouldBeInstanceOf<KubeMQException.Server>()
        ex.message shouldBe "some error"
    }

    test("UNKNOWN without description uses default") {
        val ex = sre(Status.Code.UNKNOWN).toKubeMQException("op")
        ex.shouldBeInstanceOf<KubeMQException.Server>()
        ex.message shouldBe "Unknown error"
    }

    test("INVALID_ARGUMENT maps to Validation") {
        val ex = sre(Status.Code.INVALID_ARGUMENT, "bad arg").toKubeMQException("op", channel = "ch")
        ex.shouldBeInstanceOf<KubeMQException.Validation>()
        ex.message shouldBe "bad arg"
        ex.channel shouldBe "ch"
    }

    test("DEADLINE_EXCEEDED maps to Timeout") {
        val ex = sre(Status.Code.DEADLINE_EXCEEDED, "too slow").toKubeMQException(
            "op", channel = "ch", requestId = "r1",
        )
        ex.shouldBeInstanceOf<KubeMQException.Timeout>()
        ex.message shouldBe "too slow"
        ex.channel shouldBe "ch"
        ex.requestId shouldBe "r1"
    }

    test("NOT_FOUND maps to Server with NOT_FOUND code") {
        val ex = sre(Status.Code.NOT_FOUND, "missing").toKubeMQException("op", channel = "ch")
        ex.shouldBeInstanceOf<KubeMQException.Server>()
        ex.code shouldBe ErrorCode.NOT_FOUND
        ex.channel shouldBe "ch"
    }

    test("ALREADY_EXISTS maps to Validation with ALREADY_EXISTS code") {
        val ex = sre(Status.Code.ALREADY_EXISTS, "duplicate").toKubeMQException("op", channel = "ch")
        ex.shouldBeInstanceOf<KubeMQException.Validation>()
        ex.code shouldBe ErrorCode.ALREADY_EXISTS
    }

    test("PERMISSION_DENIED maps to Authorization") {
        val ex = sre(Status.Code.PERMISSION_DENIED, "no access").toKubeMQException("op", channel = "ch")
        ex.shouldBeInstanceOf<KubeMQException.Authorization>()
        ex.channel shouldBe "ch"
    }

    test("RESOURCE_EXHAUSTED maps to Throttling") {
        val ex = sre(Status.Code.RESOURCE_EXHAUSTED, "quota").toKubeMQException("op", channel = "ch")
        ex.shouldBeInstanceOf<KubeMQException.Throttling>()
        ex.channel shouldBe "ch"
    }

    test("FAILED_PRECONDITION maps to Validation with FAILED_PRECONDITION code") {
        val ex = sre(Status.Code.FAILED_PRECONDITION, "bad state").toKubeMQException("op", channel = "ch")
        ex.shouldBeInstanceOf<KubeMQException.Validation>()
        ex.code shouldBe ErrorCode.FAILED_PRECONDITION
    }

    test("ABORTED maps to Server with ABORTED code, TRANSIENT, retryable") {
        val ex = sre(Status.Code.ABORTED, "conflict").toKubeMQException("op", channel = "ch")
        ex.shouldBeInstanceOf<KubeMQException.Server>()
        ex.code shouldBe ErrorCode.ABORTED
        ex.category shouldBe ErrorCategory.TRANSIENT
        ex.isRetryable shouldBe true
    }

    test("OUT_OF_RANGE maps to Validation with OUT_OF_RANGE code") {
        val ex = sre(Status.Code.OUT_OF_RANGE, "too big").toKubeMQException("op", channel = "ch")
        ex.shouldBeInstanceOf<KubeMQException.Validation>()
        ex.code shouldBe ErrorCode.OUT_OF_RANGE
    }

    test("UNIMPLEMENTED maps to Server with UNIMPLEMENTED code") {
        val ex = sre(Status.Code.UNIMPLEMENTED, "not supported").toKubeMQException("op")
        ex.shouldBeInstanceOf<KubeMQException.Server>()
        ex.code shouldBe ErrorCode.UNIMPLEMENTED
    }

    test("INTERNAL maps to Transport") {
        val ex = sre(Status.Code.INTERNAL, "crash").toKubeMQException("op")
        ex.shouldBeInstanceOf<KubeMQException.Transport>()
        ex.code shouldBe ErrorCode.INTERNAL
    }

    test("UNAVAILABLE maps to Connection") {
        val ex = sre(Status.Code.UNAVAILABLE, "down").toKubeMQException("op")
        ex.shouldBeInstanceOf<KubeMQException.Connection>()
        ex.code shouldBe ErrorCode.UNAVAILABLE
    }

    test("DATA_LOSS maps to Transport with DATA_LOSS code") {
        val ex = sre(Status.Code.DATA_LOSS, "corrupted").toKubeMQException("op")
        ex.shouldBeInstanceOf<KubeMQException.Transport>()
        ex.code shouldBe ErrorCode.DATA_LOSS
    }

    test("UNAUTHENTICATED maps to Authentication") {
        val ex = sre(Status.Code.UNAUTHENTICATED, "no creds").toKubeMQException("op")
        ex.shouldBeInstanceOf<KubeMQException.Authentication>()
    }

    test("All status codes pass operation through") {
        val codes = listOf(
            Status.Code.OK, Status.Code.CANCELLED, Status.Code.UNKNOWN,
            Status.Code.INVALID_ARGUMENT, Status.Code.DEADLINE_EXCEEDED,
            Status.Code.NOT_FOUND, Status.Code.ALREADY_EXISTS,
            Status.Code.PERMISSION_DENIED, Status.Code.RESOURCE_EXHAUSTED,
            Status.Code.FAILED_PRECONDITION, Status.Code.ABORTED,
            Status.Code.OUT_OF_RANGE, Status.Code.UNIMPLEMENTED,
            Status.Code.INTERNAL, Status.Code.UNAVAILABLE,
            Status.Code.DATA_LOSS, Status.Code.UNAUTHENTICATED,
        )
        for (code in codes) {
            val ex = sre(code).toKubeMQException("test-op")
            ex.operation shouldBe "test-op"
        }
    }
})

package io.kubemq.sdk.unit.common

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kubemq.sdk.common.Validation
import io.kubemq.sdk.exception.KubeMQException

class ValidationTest : FunSpec({

    context("validateChannel") {

        test("valid simple channel") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannel("my-channel", "test")
            }
        }

        test("valid channel with dots") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannel("events.orders.new", "test")
            }
        }

        test("valid channel with slashes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannel("events/orders/new", "test")
            }
        }

        test("valid channel with underscores and dashes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannel("my_channel-name", "test")
            }
        }

        test("valid channel with colons") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannel("ns:channel", "test")
            }
        }

        test("valid channel with wildcard *") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannel("events.*", "test")
            }
        }

        test("valid channel with wildcard >") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannel("events.>", "test")
            }
        }

        test("blank channel throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateChannel("", "send")
            }
            ex.message shouldBe "Channel name is required"
            ex.operation shouldBe "send"
        }

        test("whitespace-only channel throws") {
            shouldThrow<KubeMQException.Validation> {
                Validation.validateChannel("   ", "send")
            }
        }

        test("channel exceeding 256 chars throws") {
            val longName = "a".repeat(257)
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateChannel(longName, "send")
            }
            ex.message!! shouldContain "exceeds 256 chars"
        }

        test("channel exactly 256 chars is valid") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannel("a".repeat(256), "send")
            }
        }

        test("channel ending with dot throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateChannel("channel.", "send")
            }
            ex.message!! shouldContain "cannot end with '.'"
        }

        test("channel with invalid characters throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateChannel("channel name!", "send")
            }
            ex.message!! shouldContain "invalid characters"
        }

        test("channel with @ character throws") {
            shouldThrow<KubeMQException.Validation> {
                Validation.validateChannel("user@channel", "send")
            }
        }
    }

    context("validateChannelNoWildcard") {

        test("valid channel without wildcard passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateChannelNoWildcard("my-channel", "send")
            }
        }

        test("channel with * throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateChannelNoWildcard("events.*", "send")
            }
            ex.message!! shouldContain "Wildcards not allowed"
        }

        test("channel with > throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateChannelNoWildcard("events.>", "send")
            }
            ex.message!! shouldContain "Wildcards not allowed"
        }

        test("blank channel throws before wildcard check") {
            shouldThrow<KubeMQException.Validation> {
                Validation.validateChannelNoWildcard("", "send")
            }
        }
    }

    context("validateNotEmpty") {

        test("non-empty string passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateNotEmpty("hello", "field", "op")
            }
        }

        test("blank string throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateNotEmpty("", "clientId", "connect")
            }
            ex.message shouldBe "clientId is required"
            ex.operation shouldBe "connect"
        }

        test("whitespace-only string throws") {
            shouldThrow<KubeMQException.Validation> {
                Validation.validateNotEmpty("   ", "clientId", "connect")
            }
        }
    }

    context("validateBodyOrMetadata") {

        test("non-empty metadata passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateBodyOrMetadata("meta", ByteArray(0), "send")
            }
        }

        test("non-empty body passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateBodyOrMetadata("", byteArrayOf(1, 2, 3), "send")
            }
        }

        test("both non-empty passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateBodyOrMetadata("meta", byteArrayOf(1), "send")
            }
        }

        test("both empty throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateBodyOrMetadata("", ByteArray(0), "send")
            }
            ex.message!! shouldContain "At least one of metadata or body is required"
        }
    }

    context("validatePositive") {

        test("positive value passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validatePositive(1, "count", "op")
            }
        }

        test("zero throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validatePositive(0, "count", "op")
            }
            ex.message shouldBe "count must be > 0"
        }

        test("negative value throws") {
            shouldThrow<KubeMQException.Validation> {
                Validation.validatePositive(-5, "count", "op")
            }
        }
    }

    context("validateRange") {

        test("value in range passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateRange(5, 1, 10, "value", "op")
            }
        }

        test("value at min boundary passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateRange(1, 1, 10, "value", "op")
            }
        }

        test("value at max boundary passes") {
            shouldNotThrow<KubeMQException> {
                Validation.validateRange(10, 1, 10, "value", "op")
            }
        }

        test("value below min throws") {
            val ex = shouldThrow<KubeMQException.Validation> {
                Validation.validateRange(0, 1, 10, "value", "op")
            }
            ex.message!! shouldContain "must be between 1 and 10"
        }

        test("value above max throws") {
            shouldThrow<KubeMQException.Validation> {
                Validation.validateRange(11, 1, 10, "value", "op")
            }
        }
    }
})

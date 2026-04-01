package io.kubemq.sdk.unit.client

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.ConnectionStateMachine

class ConnectionStateMachineTest : FunSpec({

    test("initial state is Idle") {
        val sm = ConnectionStateMachine()
        sm.state.value shouldBe ConnectionState.Idle
        sm.isClosed shouldBe false
        sm.isReady shouldBe false
    }

    // --- Valid transitions ---

    test("Idle -> Connecting is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting) shouldBe true
        sm.state.value shouldBe ConnectionState.Connecting
    }

    test("Connecting -> Ready is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready) shouldBe true
        sm.state.value shouldBe ConnectionState.Ready
        sm.isReady shouldBe true
    }

    test("Connecting -> Closed is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Closed) shouldBe true
        sm.state.value shouldBe ConnectionState.Closed
        sm.isClosed shouldBe true
    }

    test("Ready -> Reconnecting is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Reconnecting(1)) shouldBe true
        sm.state.value shouldBe ConnectionState.Reconnecting(1)
    }

    test("Ready -> Closed is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Closed) shouldBe true
        sm.isClosed shouldBe true
    }

    test("Reconnecting -> Ready is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Reconnecting(1))
        sm.transitionTo(ConnectionState.Ready) shouldBe true
        sm.isReady shouldBe true
    }

    test("Reconnecting -> Closed is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Reconnecting(1))
        sm.transitionTo(ConnectionState.Closed) shouldBe true
        sm.isClosed shouldBe true
    }

    // --- Invalid transitions ---

    test("Idle -> Ready is invalid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Ready) shouldBe false
        sm.state.value shouldBe ConnectionState.Idle
    }

    test("Idle -> Closed is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Closed) shouldBe true
        sm.state.value shouldBe ConnectionState.Closed
    }

    test("Idle -> Reconnecting is invalid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Reconnecting(1)) shouldBe false
        sm.state.value shouldBe ConnectionState.Idle
    }

    test("Connecting -> Reconnecting is valid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Reconnecting(1)) shouldBe true
        sm.state.value shouldBe ConnectionState.Reconnecting(1)
    }

    test("Connecting -> Idle is invalid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Idle) shouldBe false
        sm.state.value shouldBe ConnectionState.Connecting
    }

    test("Ready -> Idle is invalid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Idle) shouldBe false
        sm.state.value shouldBe ConnectionState.Ready
    }

    test("Ready -> Connecting is invalid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Connecting) shouldBe false
        sm.state.value shouldBe ConnectionState.Ready
    }

    test("Reconnecting -> Connecting is invalid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Reconnecting(1))
        sm.transitionTo(ConnectionState.Connecting) shouldBe false
    }

    test("Reconnecting -> Idle is invalid") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Reconnecting(1))
        sm.transitionTo(ConnectionState.Idle) shouldBe false
    }

    test("Closed is terminal - no transitions allowed") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Closed)

        sm.transitionTo(ConnectionState.Idle) shouldBe false
        sm.transitionTo(ConnectionState.Connecting) shouldBe false
        sm.transitionTo(ConnectionState.Ready) shouldBe false
        sm.transitionTo(ConnectionState.Reconnecting(1)) shouldBe false
        sm.transitionTo(ConnectionState.Closed) shouldBe false

        sm.state.value shouldBe ConnectionState.Closed
    }

    test("Reconnecting preserves attempt number") {
        val sm = ConnectionStateMachine()
        sm.transitionTo(ConnectionState.Connecting)
        sm.transitionTo(ConnectionState.Ready)
        sm.transitionTo(ConnectionState.Reconnecting(3))
        val state = sm.state.value
        state shouldBe ConnectionState.Reconnecting(3)
        (state as ConnectionState.Reconnecting).attempt shouldBe 3
    }
})

# Changelog

## [1.0.1] - 2026-05-31

### Improvements
- Update gRPC 1.71.0 → 1.81.0, Protobuf 4.29.3 → 4.35.0, OpenTelemetry 1.46.0 → 1.62.0, and kotlinx-coroutines 1.10.2 → 1.11.0 for security and currency
- Enhance burn-in service: config-based metrics, detailed status endpoints, robust reporting

## [1.0.0] - 2026-03-29

### Added
- Initial release of KubeMQ Kotlin SDK
- PubSub client (events, events store, subscriptions)
- Queues client (stream API, simple API)
- CQ client (commands, queries)
- Kotlin coroutines and Flow support
- DSL builders for all message types
- Connection lifecycle with automatic reconnection
- OpenTelemetry observability (optional)
- BOM module for dependency management
- 13 example programs
- Burn-in soak test application

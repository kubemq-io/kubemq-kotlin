# Contributing to KubeMQ Kotlin SDK

Thank you for your interest in contributing. This guide covers the development workflow.

## Prerequisites

- JDK 11 or higher (17 recommended for development)
- Kotlin 2.0+
- Gradle 8.x (included via wrapper)
- A running KubeMQ broker for integration tests

## Building

```bash
# Full build with tests
./gradlew build

# Compile only
./gradlew compileKotlin

# Run tests
./gradlew test

# Run tests with coverage
./gradlew koverVerify

# Run linter
./gradlew detekt
```

## Project Structure

```
kubemq-kotlin/
  kubemq-sdk-kotlin/       # Core SDK
  kubemq-spring-boot-starter/  # Spring Boot auto-configuration
  kubemq-sdk-kotlin-bom/   # BOM for dependency management
  examples/                 # Example programs
  burnin/                   # Burn-in soak test application
```

## Code Style

- Follow Kotlin coding conventions
- The project uses detekt for static analysis (`./gradlew detekt`)
- Use DSL builders for public-facing configuration classes
- Mark public API with explicit `public` visibility modifier
- Use `internal` for SDK internals

## Testing

Unit tests use Kotest and MockK:

```bash
./gradlew test
```

For integration tests against a live broker:

```bash
# Ensure KubeMQ is running on localhost:50000
./gradlew test -Pkubemq.integration=true
```

## Pull Request Process

1. Fork the repository and create a feature branch
2. Write tests for new functionality
3. Ensure `./gradlew check` passes
4. Ensure `./gradlew detekt` passes with no warnings
5. Update documentation if adding public API
6. Submit a pull request with a clear description

## Commit Messages

Use clear, descriptive commit messages:

```
Add queue message retry policy support

- Add RetryPolicy configuration to QueueMessage
- Add exponential backoff with jitter
- Update documentation with retry examples
```

## Releasing

Releases are published to Maven Central automatically when a GitHub Release is created. Maintainers handle the release process.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

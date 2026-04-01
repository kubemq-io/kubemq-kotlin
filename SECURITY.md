# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.x | Yes |

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, send an email to: security@kubemq.io

Include:

- A description of the vulnerability
- Steps to reproduce the issue
- Potential impact assessment
- Suggested fix (if any)

## Response Timeline

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 5 business days
- **Fix release**: Dependent on severity, typically within 30 days

## Security Measures

The KubeMQ Kotlin SDK implements the following security practices:

- **TLS Support**: All broker connections support TLS encryption, including mutual TLS
- **Token Authentication**: Bearer token authentication via configuration or environment variables
- **No Credential Logging**: Sensitive values (tokens, TLS keys) are never logged
- **Dependency Scanning**: Dependencies are regularly audited for known vulnerabilities
- **Signed Artifacts**: Published Maven Central artifacts are GPG-signed

## Best Practices

When using the SDK:

1. Always use TLS in production environments
2. Store authentication tokens in environment variables or secret managers, not in code
3. Use the minimum required permissions for your KubeMQ client
4. Keep the SDK updated to the latest version
5. Review the COMPATIBILITY.md for supported dependency versions

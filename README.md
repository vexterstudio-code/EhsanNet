# v2rayNG (Android)

An Android proxy and VPN client written in Kotlin and Jetpack Compose, rewritten as a root-level Gradle Android project targeting Android SDK 36 (JDK 21, AGP 9.1.1, Gradle 9.3.1).

## Core Features

- **Server & Protocol Management**: Supports creating, editing, importing, exporting, QR scanning/sharing, and duplicating profiles for VMess, VLESS, Shadowsocks, Trojan, SOCKS, HTTP, WireGuard, Hysteria2, MASQUE, Policy Groups, Proxy Chains, and Custom JSON configs.
- **Xray/V2Ray JSON Configuration Builder**: Generates full client and speed-test configurations with inbound/outbound rules, DNS, Mux, Fragment, Observatory, and routing rulesets.
- **Subscriptions & User Assets**: Manages subscription groups with usage/traffic parsing, regex remark filtering, entry/exit proxy chaining, WorkManager background updates, and custom geoip/geosite asset management.
- **Routing & Per-App Proxy**: Custom domain/IP/port/protocol/process routing rules with China/Iran/Russia/Global presets and per-app split-tunneling configuration.
- **Diagnostics & Backup**: TCP delay testing (TCPing), TLS certificate SHA-256 fingerprint fetching, Logcat viewer, local ZIP backup/restore, and WebDAV sync.
- **System Integration**: Quick Settings Tile, App Widget, Tasker plugin support, Boot receiver, and multi-language Material 3 Compose UI with light/dark/dynamic theming.

## Building

```bash
gradle assembleDebug
gradle :app:testDebugUnitTest
```

## Notes & Limitations

- The external Go/NDK native binaries (`libv2ray.aar` from `AndroidLibXrayLite` and `libhev-socks5-tunnel.so` from `hev-socks5-tunnel`) are not bundled in this source repository.
- `CoreNativeManager` and `TProxyService` include reflective/guarded native bridges and report a clear status message if native binaries are not packaged in `app/libs/`. All configuration parsing, JSON generation, subscription management, routing rules, TCPing delay checks, TLS SHA-256 fetching, QR scanning/generation, and backup/restore run natively in Kotlin.

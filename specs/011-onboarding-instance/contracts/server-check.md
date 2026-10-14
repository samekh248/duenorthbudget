# Contract: Server check

`ServerProbe.check(address: ServerAddress): ServerCheck` in `:core:budget`. Runs on `Dispatchers.IO`, is cancellable, and makes no write to the server.

## Requests

For each candidate in `address.candidates`, in order, stopping at the first that connects:

1. `GET {candidate}/account/needs-bootstrap`, no token.
2. If (1) did not list sign-in methods: `GET {candidate}/account/login-methods`, no token.

Timeouts: 10 s connect, 15 s read. A TLS handshake failure stops at that candidate (no fallback to `http://`).

## Outcome rules

| Observation | Outcome |
|---|---|
| No candidate connects (refused, DNS, timeout) | `Unreachable(NoAnswer \| Timeout)` |
| TLS handshake fails | `Unreachable(CertificateNotTrusted)` |
| (1) and (2) both answer, but neither is Actual's JSON (`status` + `data`, or a `methods` list) | `NotActual(httpStatus)` |
| (1) says `bootstrapped: false` | `NotSetUp` |
| Neither endpoint answers with Actual's JSON and the server answers 5xx (a proxy in front of a stopped server) | `Unreachable(NoAnswer)` |
| Actual JSON, but no sign-in methods listed (an older server) | `Ready` with password only |
| Methods listed, none of `password` / `openid` | `Unsupported(methods)` |
| Methods include `password` and/or `openid` | `Ready(address, secure, methods)` |

Method entries are accepted as plain strings or as objects with a `method` field; objects with `active: false` are ignored. Field names are checked against the Actual sync-server source when this is implemented.

## Tests (JVM, `LocalActualServer`)

`LocalActualServer` gains `needs-bootstrap` with switches for `bootstrapped`, method lists, and a "not Actual" mode that serves an HTML page. Tests cover each row above, the `https://` then `http://` order for a scheme-less address, the no-downgrade rule after a TLS failure, sub-path addresses, and cancellation leaving no saved instance.

## Callers

- Onboarding address step: the outcome picks the next page or the message.
- Settings (spec 009): `probeReachability()` uses `check` instead of `loginMethods()`, so an unreachable server is shown as not reached.

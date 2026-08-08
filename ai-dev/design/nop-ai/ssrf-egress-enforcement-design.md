# AI HTTP SSRF Egress Enforcement Design

> Status: active
> Last Reviewed: 2026-08-08
> Source: Plan 336 (DR-4a), `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`

## Decision

DR-4a (approved 2026-08-08): a validating `IDnsResolver` implementation
(`SsrfGuardDnsResolver`) rejects internal / cloud-metadata addresses and returns a single
pinned `InetAddress`. It is injected via the existing `HttpClientConfig.dnsResolver` seam.
No public `IHttpClient` contract change.

## Enforcement Layers

### Layer 1 — Pre-flight text validation (executor level)

`SsrfAddressGuard.validateHost(host)` is called by both `HttpRequestExecutor.validateUrl`
and `GraphqlQueryExecutor.validateUrl` before any transport call. It normalizes encoded IP
representations (decimal, hex, IPv6-mapped) via `InetAddress.getByName()` (local parse only —
no DNS for IP literals), and rejects internal, loopback, link-local, any-local, IPv6 ULA,
carrier-grade NAT, and cloud-metadata addresses.

IP-literal detection uses a conservative heuristic (`looksLikeIpLiteral`): only strings
composed exclusively of digits, dots, colons, hex letters a–f, and encoding prefixes (0x/0b/0o)
are treated as IP literals. Real hostnames always contain at least one non-hex letter (g–z)
or a dash, so they are never misclassified. This prevents `InetAddress.getByName()` from
triggering DNS resolution on hostnames.

When a denied target is detected, the executor returns an error result and **never calls**
`IHttpClient.fetch` / `fetchAsync`. This is verified by no-request transport assertions in
`HttpRequestExecutorTest` and `GraphqlQueryExecutorTest`.

### Layer 2 — Transport-level resolver (connection pinning + redirect-hop enforcement)

`SsrfGuardDnsResolver` implements `io.nop.http.api.IDnsResolver`. It validates every resolved
address (including all addresses in a multi-record A/AAAA set), rejects any set containing an
internal address (DNS-rebinding protection), and returns a single pinned address (connection
pinning). Unresolved or policy-ambiguous targets throw `UnknownHostException` (fail-closed).

## Client Support

| Client | Consults `HttpClientConfig.dnsResolver`? | Redirect-hop protection |
|--------|------------------------------------------|------------------------|
| Apache HttpClient | Yes — `ApacheHttpClientHelper.createConnectionManager` wires it into the connection manager's `DnsResolver`, consulted on every new connection including after redirects | Full |
| JDK HttpClient | No — `JdkHttpClient.start()` ignores the resolver | Pre-flight only |
| OkHttp | No — receives a pre-built `OkHttpClient` | Pre-flight only |

Production deployments using AI HTTP tools should wire `SsrfGuardDnsResolver` into
`HttpClientConfig.dnsResolver` alongside a supporting client (Apache) for full SSRF coverage.

## Rejected Alternatives

- **Validator-local fix in `HttpRequestExecutor` only**: would leave `GraphqlQueryExecutor`
  vulnerable (same validator-to-transport gap). Rejected — both executors now share
  `SsrfAddressGuard`, and the resolver provides transport-level coverage for both.
- **Public `IHttpClient` contract change**: the existing `IDnsResolver` seam is sufficient
  for Apache HttpClient. No new `IHttpClient` method needed.
- **Manual redirect following at executor level**: would couple the executor to transport
  internals. The resolver seam handles redirects transparently for supporting clients.

# NET-02 — MQTT Connection Security Audit

> Mission: security-audit (roadmap item 7, deliverable NET-02)
> Date: 2026-09-19. Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3.
> Scope: `nop-network/nop-vertx/` — `nop-vertx-mqtt-client` (EMPTY module — verified),
> `nop-vertx-mqtt-server` (VertxMqttServer, IMqttAuthChecker/SimpleMqttAuthChecker,
> MqttConnection, MqttSessionManager, MqttTopicMatcher, MqttServerMessageService).
> Method: full read of every main source in the module (16 files); wiring/consumer grep
  (library-only, no in-repo consumers); test cross-check (4 test classes).

## Verified controls

1. **Anonymous/unknown-user rejection in the simple checker**
   (`SimpleMqttAuthChecker.checkAuthAsync` L30-36): null/empty username → reject;
   unknown user → reject; wrong password → reject. The `Objects.equals(null,null)`
   anonymous-passes-empty-map trap is explicitly defended against (in-code comment);
   with the default empty `users` map the checker fails CLOSED (all connections
   rejected). Reject path returns a proper `CONNECTION_REFUSED_BAD_USERNAME_OR_PASSWORD`
   (VertxMqttServer L89).
2. **clientId-aware auth entry** (`IMqttAuthChecker` 4-arg default method): MQTT CONNECT
   primary identity (clientId) is available to auth implementations (IoT
   clientId=deviceKey pattern) without breaking existing 3-arg implementations.
3. **Session takeover hygiene** (`MqttSessionManager.addConnection` L33-40): a new
   connection with the same clientId closes the old connection and drops its
   subscriptions (takeover cannot leave two live sessions per identity); disconnect
   cleanup removes session + subscriptions (VertxMqttServer L113-114).
4. **Topic matcher is injection-free** (`MqttTopicMatcher.matches`): wildcard semantics
   apply to the FILTER side only; published topic names are compared literally — a
   client publishing to a topic containing `+`/`#` characters cannot expand another
   client's filter scope. Filter and topic are also null-guarded.
5. **Listener isolation** (`VertxMqttServer.wrapHandler.onPublish` L144-154): one
   failing in-process publish listener cannot break the business handler or other
   listeners (typed error log). QoS ack ladder (PUBACK/PUBREC/PUBREL/PUBCOMP) handled.
6. **Tests exist for the security-relevant behavior** (`TestMqttServerFix` auth,
   `TestMqttTopicMatcher`, `TestMqttConnection`, `TestMqttMessageRouting`).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-N2-1 | MEDIUM (P2) — structural | nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/VertxMqttServer.java:77-97 (`if (authChecker != null) ... else acceptEndpoint(endpoint)`) | **Fail-open default when no auth checker is wired.** `handleEndpoint` accepts EVERY connection when `authChecker == null` — an integrator that constructs/wires `VertxMqttServer` without an `IMqttAuthChecker` bean gets an unauthenticated broker with no warning (the @Inject setter leaves it null in plain construction; no beans.xml ships a default). Contrasts with the platform's fail-closed conventions elsewhere (BashExecutor refuses when sandbox==null; StreamOpsHttpServer refuses non-loopback without token). Module is a library with no in-repo consumer today, so no live attack path in-repo — the exposure is the integrator-facing default posture. | Fail closed: require an explicit authChecker or an explicit `allowAnonymous=true` opt-in (warn-log when anonymous mode active); at minimum WARN at start when authChecker == null. |
| F-N2-2 | MEDIUM (P2) — structural | .../impl/MqttSessionManager.java:51-84 (subscribe/`findConnections`), .../bus/MqttServerMessageService.java:56-134, .../impl/MqttConnection.java:112-136 (subscribe handler), no ACL seam in `IMqttHandler`/`IMqttConnection` | **No topic-level authorization model exists.** Authentication is connection-level only; any ACCEPTED client may SUBSCRIBE to `#` (receiving ALL device traffic routed through `MqttServerMessageService`) and PUBLISH to any topic consumed by app subscribers. There is no per-client topic ACL and NO seam to add one (IMqttHandler exposes no authorize callback; subscribeHandler registers filters directly). For an IoT broker surface this is a tenant-isolation gap by construction — isolation is only achievable via network-level separation. | Add an authorizing hook (e.g. `IMqttAuthChecker.authorizeSubscribe(clientId, filter)` / `authorizePublish(clientId, topic)` default-deny or default-allow documented) before registering subscriptions / dispatching publishes; document the trust boundary. |
| F-N2-3 | LOW (P3) | .../auth/SimpleMqttAuthChecker.java:19-37 | **Plaintext password map + non-constant-time compare.** Users map holds cleartext passwords (config-supplied), compared via `Objects.equals` (timing side channel — theoretical on local network; MQTT passwords are high-entropy-optional in practice). No hashed-credential option. | Support digested credentials (e.g. SHA-256 entries) and constant-time compare (`MessageDigest.isEqual`); LOW priority. |

## Explicit no-finding statements

- TLS: no downgrade introduced by the module — it performs no TLS logic at all; the
  transport is entirely the integrator-supplied Vert.x `MqttServerOptions` (constraint
  recorded: platform provides no TLS config surface or guidance for MQTT; deployment
  checklist item for item 9).
- No auth material is logged (username/password never appear in log statements; the
  reject log prints clientId only, L87).
- No topic-injection vector found (control #4); no path from client input into file
  paths, SQL, or commands anywhere in the module.
- `nop-vertx-mqtt-client` contains no code (empty pom-only module, jar verified
  empty) — nothing to audit client-side; recorded as a coverage gap, not a finding
  (the roadmap's "MQTT 连接安全" client angle does not exist in-repo).

## Adjudication (Phase 3 input)

- F-N2-1: `remediation-target` (fail-closed/anonymous opt-in) — successor item 9.
- F-N2-2: `remediation-target (design gap — authz seam or documented boundary)` —
  successor item 9; if adjudicated no-fix, the trust boundary MUST be documented for
  integrators.
- F-N2-3: `watch-only residual`.

## Owner mapping

- F-N2-1..F-N2-3 → item 9. No overlap with AUTH (JWT/authn at platform edge, not
  MQTT), CRED (no credential storage here), NET-03 (stream message bus is a separate
  IMessageService surface — MQTT bus bridge is in this module and owned here).

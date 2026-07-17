# nop-gateway: Dynamic Configuration & Hot-Reload Design

**Date**: 2026-07-17
**Scope**: `nop-service-framework/nop-gateway/`, `nop-core-framework/nop-config/`, `nop-kernel/nop-xlang/` (XDSL delta)
**Status**: active
**Inspiration**: APISIX (etcd-based), Spring Cloud Gateway (actuator + routes), Kong (DB + admin API)

---

## 1. Configuration Model

### Decision: Hybrid (XDSL + Admin API + In-Memory Merge)

| Dimension | A: Pure XDSL | B: Admin API + DB | C: Hybrid (chosen) |
|-----------|-------------|-------------------|-------------------|
| GitOps | ✅ full | ❌ split brain | ✅ XDSL as base + DB as delta |
| Runtime changes | ❌ restart required | ✅ instant | ✅ instant |
| Delta customization | ✅ native | ❌ not applicable | ✅ XDSL delta for base, API delta for runtime |
| Atomic rollback | ❌ | ✅ per version | ✅ per version |
| Cluster coordination | ❌ file per node | ✅ DB as source of truth | ✅ DB + config center |
| Operational complexity | low | medium | medium |

**Reasoning**: Nop's reversible computation principle (可逆计算) directly maps to the hybrid model:
- **XDSL as source model**: `app.gateway.xml` is the Git-controlled, reviewable, Delta-customizable source of truth
- **Admin API changes as Delta overrides**: Runtime changes are stored as a "delta layer" over the XDSL base, not as a separate, disconnected state
- **In-memory merge**: Both sources are parsed into the same `GatewayModel` structure; the Admin API shadow writes are applied as overlays on top of the parsed XDSL model

### How Split-File Composition Works

```
app.gateway.xml              # Base: routes, interceptors, provider configs
├── app-routes.gateway.xml   # Can be separated: routes only
├── app-providers.gateway.xml # Can be separated: AI provider configs only
└── _delta/                  # Delta customization per environment
    └── default/
        └── nop/main/app.gateway.xml  # Prod overrides

Runtime State (Admin API):
└── GatewayRuntimeConfig     # In-memory delta overlay on parsed XDSL model
```

The XDSL `x:extends` mechanism already supports multi-file composition. Routes and providers can be split using `x:gen-extends`:

```xml
<gateway x:schema="/nop/schema/gateway.xdef"
         x:extends="base.gateway.xml">
    <x:gen-extends>
        <gateway x:extends="app-routes.gateway.xml"/>
        <gateway x:extends="app-providers.gateway.xml"/>
    </x:gen-extends>
</gateway>
```

### Configuration Sources Precedence (high → low)

1. Admin API runtime overrides (in-memory, per-gateway-instance or DB-backed)
2. Delta layer (`_delta/{env}/...`)
3. Base XDSL (`app.gateway.xml` + `x:gen-extends` includes)
4. Module defaults (`gateway-defaults.beans.xml`)

### Constraints

- Admin API overrides must be serializable to a traceable form (DB row or JSON log) so that a restart does not lose runtime state.
- On startup, the merge is: parse XDSL base → apply Admin API deltas from DB/JSON → build in-memory `GatewayModel`.
- The XDSL file is never modified by the Admin API. Runtime deltas are stored separately.

---

## 2. Configuration Distribution

### Decision: Layered strategy — nop-config polling (fallback) + VFS watch (single-node) + optional Redis Pub/Sub (cluster fast-path)

**Single-instance mode** (default, dev, small deployments):
- The existing `ResourceCacheEntry<GatewayHandler>` in `GatewayHttpFilter` already provides file-watch-based hot reload via `ResourceComponentManager`.
- When `app.gateway.xml` changes on disk, the `ResourceComponentManager` evicts the cached component model and reloads on next request.
- Admin API changes are applied in-memory to an `AtomicReference<GatewayModel>` held by `GatewayHandler`.

**Cluster mode** (multi-instance, HA):
- Primary channel: **nop-config** (`IConfigSource` + `ConfigChangeApplier`). The gateway subscribes to config keys `nop.gateway.config.<version>` and applies changes when the config value updates.
- Fast-path: optional **Redis Pub/Sub** channel (`nop-gateway-config-changes`). When config changes are published, all instances apply immediately without waiting for the next nop-config poll cycle.
- Fallback: if both config center and Redis are unavailable, the gateway continues with the last valid in-memory config. Health check reports `DEGRADED_CONFIG_SOURCE`.

**Rejected alternatives**:
- **etcd**: Heavy dependency. Nop is a framework (not Kubernetes-nativelike APISIX/Higress). Adding etcd as a requirement would make the gateway unreasonably heavy for single-instance deployments.
- **etcd via nop-config plugin**: Possible in future but not baseline. `IConfigSource` is pluggable; an etcd-backed `IConfigSource` implementation can be added as an optional module.
- **Signal-based (SIGHUP)**: Unreliable in containerized environments, no coordination across instances.

### Cluster Distribution Flow

```
Admin API (any instance)
  └→ 1. Validate new config
  └→ 2. Persist to DB (gateway_config_versions table)
  └→ 3. Write to nop-config (trigger version bump)
  └→ 4. Publish Redis message (optional)
  └→ 5. Apply locally

Each instance:
  nop-config poll ─→ detects version change ─→ applies
  └ or ─→ Redis subscriber ─→ applies immediately
  └→ Validates locally
  └→ AtomicReference<GatewayModel>.set(newModel)
  └→ Reports apply success/failure to health check endpoint
```

### Configuration Key Design

```
nop.gateway.config.version    # Monotonic version number (long)
nop.gateway.config.data       # Serialized full GatewayModel JSON (for nop-config source)
```

Or for large configs: store in DB, nop-config only carries the version + a content hash. Instances fetch the actual data from DB or file store.

---

## 3. Atomic Configuration Update

### Two-Phase Update Protocol

```
Phase 1: VALIDATE
  ├── parse config source (XDSL / JSON / DB row) into GatewayModel structure
  ├── validate all routes (match patterns, upstream reachability)
  ├── validate all interceptors (bean resolution, XPL compilation)
  ├── validate all provider configs (credential format, endpoint URL format)
  ├── if ANY route/filter/provider is invalid → REJECT entire update
  └── if all valid → return validated GatewayModel (immutable)

Phase 2: APPLY
  ├── If version <= currentVersion → REJECT (stale update detection)
  ├── freeze(validatedModel, cascade=true)  // make immutable
  ├── validatedModel.init()                  // build TriePathRouter indexes
  ├── currentModelRef.set(validatedModel)   // AtomicReference swap
  └── increment local version counter

Phase 3: CONFIRM (cluster only)
  ├── report success to health/status endpoint
  └── if any instance fails → cluster-wide rollback (coordinator re-issues previous config)
```

### Stale Update Detection

Each `GatewayModel` snapshot carries a monotonically increasing `version` field. The apply phase rejects any update where `incomingVersion <= currentVersion`. This prevents:

- Two admins pushing conflicting changes simultaneously
- Delayed network messages applying old config over newer ones
- Race between nop-config poll and Redis Pub/Sub

### All-or-Nothing Semantics

If 5 out of 100 routes are invalid:
- **No partial update**. The validator rejects the entire batch.
- Error response enumerates which 5 routes failed and why.
- The gateway stays on the previous valid configuration.
- This applies to both XDSL file reloads and Admin API calls.

### Copy-on-Write Pattern

```pseudocode
currentModel = AtomicReference<GatewayModel>

onRequest(request):
    model = currentModel.get()  // always get latest
    route = model.router.matchPath(request.path)
    ...

applyUpdate(newConfig):
    validated = validateAndBuild(newConfig)
    if validated == null: throw ValidationError
    currentModel.set(validated)  // atomic swap
```

**In-flight requests** continue with the `GatewayModel` reference they obtained at request start. The old object is garbage collected when all in-flight requests complete. This is safe because `GatewayModel` (and its children) are immutable after `freeze(true)`.

---

## 4. Per-Route vs Global Configuration

### Current State

| Scope | Current Location | Examples |
|-------|-----------------|----------|
| Global | `GatewayModel.interceptors` | Logging, auth, rate limiting |
| Per-route | `GatewayRouteModel` | match, invoke, forward, streaming, requestMapping, responseMapping |

### Extended Model: Provider Configs

AI gateway needs a third scope — **provider** — that is global but AI-provider-specific:

```xdef
<gateway>
    <!-- Global: providers -->
    <providers xdef:body-type="list" xdef:key-attr="id">
        <provider id="!string" xdef:name="GatewayProviderModel"
                  type="!string"  <!-- openai, claude, ollama, custom -->
                  baseUrl="!string">
            <credentials xdef:body-type="list" xdef:key-attr="id">
                <credential id="!string" xdef:name="GatewayCredentialModel"
                            apiKey="!string" activeFrom="datetime" retireAt="datetime"/>
            </credentials>
            <rateLimit maxRequests="int" windowSec="int"/>
            <timeout connectMs="int" readMs="int"/>
        </provider>
    </providers>

    <!-- Per-route: references provider -->
    <route id="chat-route">
        <match path="/v1/chat/completions"/>
        <providerRef>openai-default</providerRef>
        <!-- filter chain specific to this route -->
        <filters xdef:body-type="list">
            <filter id="rate-limit" bean="nopRateLimitFilter">
                <props>
                    <property name="tokensPerMinute">100</property>
                </props>
            </filter>
            <filter id="content-filter" bean="nopContentFilter"/>
        </filters>
    </route>
</gateway>
```

### How Delta Applies to Both Levels

- **Global delta**: `_delta/{env}/nop/main/app.gateway.xml` overrides the entire provider config section or individual providers via `x:override`.
- **Per-route delta**: Individual routes can be overridden by `id` via the `x:override` mechanism.
- **Admin API**: Can target any scope — `POST /api/admin/gateway/providers/{id}` updates a single provider; `POST /api/admin/gateway/routes/{id}` updates a single route.

Both levels converge to the same `GatewayModel` structure. The model's `init()` method rebuilds indexes for both the global TriePathRouter (interceptors) and per-route configurations.

---

## 5. Filter Chain Hot Reload

### Architecture

The current interceptor model (`IGatewayInterceptor`) already supports the chain-of-responsibility pattern. The change is to make it hot-reloadable by scoping the chain per-route and using volatile references.

```
GatewayRouteModel
  └── filterChain: AtomicReference<FilterChain>

FilterChain:
  └── filters: List<IGatewayInterceptor>   // resolved from IoC or inline
  └── proceed(request, ctx):
        iterate filters, each calling next()
```

### Hot-Reload Flow

```pseudocode
updateFilters(routeId, newFilterConfigs):
    // 1. Build new chain from IoC beans
    newChain = FilterChainBuilder.build(newFilterConfigs, beanContainer)
    //    → resolves "nopRateLimitFilter" from IoC
    //    → calls IGatewayInterceptor.setProperties(props)
    //    → validate chain (no duplicate IDs, valid bean types)

    // 2. Atomic swap on target route
    route.filterChain.set(newChain)

    // 3. In-flight requests → still hold reference to old FilterChain
    //    Old chain → GC when last in-flight request completes
```

### IoC Integration

Filters reference IoC beans via the `bean` attribute:

```xml
<filter id="logging" bean="nopLoggingGatewayInterceptor"/>
```

`FilterChainBuilder` resolves beans from `BeanContainer.instance().getBean(beanName)`. Since IoC beans can themselves be reconfigured (via `@r-cfg:` reactive config or `nop-config`), a filter's internal state can change independently of the chain structure:

- **Structure change** (add/remove/reorder filters): `route.filterChain.set(newChain)` — atomic swap
- **Parameter change** (e.g., rate limit threshold): Bean's own config injection (`@InjectValue @r-cfg:`) handles this without touching the chain

### Thread Safety

- `GatewayRouteModel.filterChain` is `AtomicReference<FilterChain>` (not `volatile`) to prevent the "failed CAS" problem in concurrent swap scenarios.
- `FilterChain` is immutable after construction. Each request gets a consistent view.
- For per-request state, `IGatewayContext` holds mutable scoped data.

---

## 6. Provider Credential Rotation

### Problem

AI provider API keys must support rotation without disruption:
1. A key is compromised → must be disabled immediately
2. A key is expiring → new key must be active before old key expires
3. Migrations should not drop in-flight requests

### Credential Lifecycle Model

```pseudocode
credential = {
  id: "openai-key-v6",
  apiKey: "sk-...",
  status: "active" | "retired",
  activeFrom: "2026-07-01T00:00:00Z",  // when this key became primary
  retireAt:  "2026-07-15T00:00:00Z",   // after this time, reject new requests
  retiredAt: null                        // actual retirement timestamp
}
```

### Rotation Algorithm

```pseudocode
onRequest(request, route):
    providerConfig = getProviderConfig(route.providerRef)
    credential = providerConfig.selectCredential(currentTime)
    if credential == null:
        return 503 NoValidCredential

    request.setHeader("Authorization", "Bearer " + credential.apiKey)
    // In-flight request → carries the credential snapshot obtained at request start

rotateCredential(providerRef, newApiKey, rotationPolicy):
    provider = getProviderConfig(providerRef)
    newCred = createCredential(newApiKey, status="active", activeFrom=now)

    switch rotationPolicy:
        case IMMEDIATE:
            oldCred.status = "retired"
            oldCred.retiredAt = now
            provider.addCredential(newCred)
        case GRACEFUL:
            oldCred.retireAt = now + gracePeriod (e.g., 5 min)
            oldCred.status = "retiring"
            provider.addCredential(newCred)
            // New requests use newCred (prefer latest activeFrom)
            // In-flight requests finishing within gracePeriod → oldCred still valid
            scheduleTask(gracePeriod): oldCred.status = "retired"
```

### SelectCredential Logic

```pseudocode
selectCredential(now):
    // Return the latest active credential that is valid at `now`
    return credentials.stream()
        .filter(c → c.activeFrom <= now && (c.retiredAt == null || c.retiredAt > now))
        .max(Comparator.comparing(c → c.activeFrom))
```

### Impact on GatewayModel

Provider credentials are per-provider in the `GatewayModel`. When credential data changes via Admin API, the provider's config is updated and the model is **not** fully rebuilt — the provider object uses `AtomicReference<List<Credential>>` so the route matching and execution path is unaffected.

---

## 7. Route Versioning and Rollback

### Decision: DB-backed snapshots with Admin API access

**Storage**: `gateway_config_versions` table

| Column | Type | Description |
|--------|------|-------------|
| id | sequence | auto increment |
| version | bigint | monotonic version number |
| config_snapshot | jsonb | full GatewayModel snapshot |
| commit_message | varchar | human-readable description |
| author | varchar | who made the change |
| created_at | timestamp | when |
| parent_version | bigint | previous version (for rollback chain) |

**API surface**:

```
GET    /api/admin/gateway/config              # current active config (version + summary)
GET    /api/admin/gateway/config/versions     # list versions (paginated)
GET    /api/admin/gateway/config/versions/{v} # full snapshot at version v
POST   /api/admin/gateway/config/rollback     # rollback to version v
POST   /api/admin/gateway/config/validate     # validate config without applying

# Per-section (minimizing diff size):
PUT    /api/admin/gateway/routes
PUT    /api/admin/gateway/providers
PUT    /api/admin/gateway/interceptors
DELETE /api/admin/gateway/routes/{id}
```

### Rollback Mechanics

```pseudocode
rollback(targetVersion):
    // 1. Load snapshot for targetVersion from DB
    snapshot = configVersionRepo.getByVersion(targetVersion)
    if snapshot == null: return 404

    // 2. Validate the old config against current runtime (not all old configs are safe to restore)
    if not validate(snapshot): return 422

    // 3. Create a NEW version entry containing the rolled-back snapshot
    //    (do NOT modify the old entry — version history is append-only)
    newVersion = createVersionEntry(
        config: snapshot.config,
        message: "rollback to v" + targetVersion,
        parentVersion: currentVersion
    )

    // 4. Apply as normal config update
    applyUpdate(newVersion)
```

### GitOps Integration

XDSL files remain the primary Git-controlled source. Admin API changes can optionally be auto-committed to a separate branch (`gateway-runtime-config`) in a configured Git repository:

```
Admin API change
  └→ persist to DB
  └→ optionally: commit app.gateway.xml (merged with runtime delta) to Git
  └→ Git push triggers CI/CD validation pipeline
```

This bridges the gap between "operational hotfix" and "reviewed Git change." The XDSL-based Git flow is the source of truth; Admin API changes are temporary deltas that should be merged back to XDSL for permanent retention.

---

## 8. Integration with Nop Platform

### Component Diagram

```

┌─────────────────────────────────────────────────────────────┐
│                   GatewayHttpFilter                         │
│  (IHttpServerFilter, entry point per request)               │
└──────────────────────┬──────────────────────────────────────┘
                       │ delegates to
                       ▼
┌──────────────────────────────────────────────────────────────┐
│                   GatewayHandler                             │
│  - AtomicReference<GatewayModel> currentModel                │
│  - RouteExecutor, InvokeProcessor, ForwardProcessor, etc     │
│  - loadModel(): merge XDSL + Admin API deltas → GatewayModel │
└──────────┬──────────────────────────────────┬────────────────┘
           │ uses                             │ uses
           ▼                                  ▼
┌─────────────────────────┐    ┌──────────────────────────────┐
│   GatewayModel           │    │   nop-config (IConfigSource) │
│  - routes (TriePathRouter)│   │   - polling or push          │
│  - interceptors          │    │   - ConfigChangeApplier     │
│  - providers             │    └──────────┬───────────────────┘
│  - frozen after init()   │               │ triggers
└─────────────────────────┘               ▼
                                  ┌──────────────────────┐
                                  │ ConfigChangeHandler   │
                                  │ - parse new config    │
                                  │ - validate            │
                                  │ - swap AtomicReference│
                                  └──────────────────────┘
```

### Nop Config Integration

The `ConfigChangeApplier` pattern already exists in `nop-config`. The gateway uses it:

```java
class GatewayConfigWatcher {
    private final ConfigChangeApplier applier;

    GatewayConfigWatcher(IConfigProvider configProvider) {
        this.applier = new ConfigChangeApplier(
            configProvider.getConfigExecutor(),
            this::onConfigChange
        );
        // Watch for config key changes
        configProvider.addChangeListener(CFG_GATEWAY_CONFIG_VERSION, (oldVal, newVal) -> {
            applier.requestUpdate();
        });
    }

    void onConfigChange() {
        String versionStr = CFG_GATEWAY_CONFIG_VERSION.get();
        String configJson = fetchConfigData(versionStr); // from DB or nop-config
        GatewayConfigUpdate update = parseAndValidate(configJson);
        gatewayHandler.applyUpdate(update);
    }
}
```

### IoC Integration for Filters

- Filter beans are resolved lazily via `BeanContainer.instance().getBean(name)` at filter-chain build time.
- Filter beans can themselves use `@InjectValue @r-cfg:` for their own hot-reloadable properties, independent of the gateway filter chain structure.
- Example: a `RateLimitFilter` can reload its threshold from config without the gateway rebuilding the filter chain.

### XDSL Validation via XDef

The existing `gateway.xdef` schema provides structural validation. For runtime changes:
- XDSL file reloads: validated through the standard `DslModelParser`
- Admin API changes: validated against the same XDef schema by serializing the incoming JSON to a temporary `GatewayModel` and running `DslModelParser` validation

### Health and Degradation

**When config source is unavailable**:

| Scenario | Behavior |
|----------|----------|
| nop-config down at startup | Gateway loads only XDSL file. Logs warning `GATEWAY_CONFIG_SOURCE_UNAVAILABLE`. Health check returns `DEGRADED`. |
| nop-config down during runtime | Last valid model continues in memory. New Admin API writes fail. |
| Invalid config pushed via Admin API | Rejected with detailed error. Previous config remains active. |
| Redis Pub/Sub disconnected | Falls back to nop-config polling. No service disruption. |
| DB unavailable for Admin API reads | GET /admin/* endpoints fail. Gateway continues serving traffic with last model. |
| XDSL file deleted | Gateway starts with empty model (no routes). |

**Health check endpoints**:

```
GET /health/gateway
  → status: "UP" | "DEGRADED" | "DOWN"
  → configVersion: 42
  → configSource: "xdsl+admin-api" | "xdsl-only" | "none"
  → lastApplyTime: "2026-07-17T10:00:00Z"
  → routesActive: 23
  → providersActive: 5
  → errors: []
```

---

## 9. Rejected Alternatives Summary

| Alternative | Reason for Rejection |
|-------------|---------------------|
| etcd as primary config store | Operational overhead disproportional to Nop's target deployment range (single-node to small/medium cluster) |
| Pure DB-based config | Losses GitOps, Delta customization, and XDSL's `x:extends` composition |
| Full XDSL-only, no Admin API | Cannot support runtime credential rotation, gradual rollouts, or emergency route disabling without restart |
| In-place mutation of GatewayModel | Thread-unsafe; in-flight requests would see partially updated state |
| Per-route locking during update | Over-engineered; Copy-on-Write with AtomicReference is simpler and proven in practice |
| No versioning (just overwrite) | No rollback capability; violates "reversible computation" principle |
| etcd via nop-config plugin | Possible future extension (pluggable `IConfigSource`), but not part of baseline design |

---

## 10. Migration Path from Current Implementation

The current `GatewayHttpFilter` uses `ResourceCacheEntry<GatewayHandler>` with a single XDSL file. The migration to dynamic configuration:

1. **Phase 1** (immediate): Make `GatewayHandler` hold `AtomicReference<GatewayModel>` instead of a final reference.
2. **Phase 2**: Add `GatewayConfigWatcher` that listens to nop-config changes and triggers model reload.
3. **Phase 3**: Add Admin API endpoints with DB-backed versioning.
4. **Phase 4**: Add provider/credential model to `gateway.xdef` and `GatewayModel`.
5. **Phase 5**: Add per-route filter chain hot reload.

Each phase is backward-compatible: if no Admin API or nop-config is configured, the gateway falls back to XDSL-file-based loading, which is the current behavior.

---

## References

- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/impl/GatewayHandler.java` — current handler, uses final `GatewayModel` reference
- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java` — entry point with `ResourceCacheEntry`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/gateway.xdef` — current schema
- `nop-core-framework/nop-config/src/main/java/io/nop/config/impl/ConfigChangeApplier.java` — config change polling/routing pattern
- `nop-core-framework/nop-config/src/main/java/io/nop/config/source/IConfigSource.java` — config source abstraction
- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/ConfigExpressionProcessor.java` — reactive config (`@r-cfg:`) for filter bean parameters

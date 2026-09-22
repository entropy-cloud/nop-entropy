# CORE-03 — IoC/Config Value Injection Safety Audit

> Mission: security-audit (roadmap item 1, deliverable CORE-03)
> Date: 2026-09-18. Auditor: ZCode session (plan 2026-09-17-0831, Phase 2).
> Scope: `@cfg:`/`@sec:`/`@InjectValue` value resolution chain (`nop-config`), the
> `AESTextCipher` crypto core (`nop-commons`), NopIoC member visibility (`nop-ioc`,
> `nop-core` reflection model), secret-leak sweep results.
> Method: full read of DefaultConfigValueEnhancer, ConfigStarter.newValueEnhancer,
> AESTextCipher (key derivation v1/legacy, IV handling, decrypt dispatch),
> ClassModelBuilder member discovery; grep sweeps (Java + DSL); PMD/SpotBugs
> security-rule cross-check; wiring trace of `nopOrmColumnBinderEnhancer`.

## Verified controls

1. **`@sec:` decryption is single-path**: the only main-code handling of the `@sec:`
   prefix is `DefaultConfigValueEnhancer.doEnhance` (L66-69) → `cipher.decrypt(...)`;
   result flows into `StaticValue.valueOf(...)` and is returned as the config value.
   Decrypted values are not logged, not embedded in error params, not persisted.
2. **Private member injection is impossible at the reflection-model level**:
   `ClassModelBuilder.discoverDeclaredFields` (nop-kernel/nop-core,
   ClassModelBuilder.java:396-416) skips `Modifier.isPrivate` fields;
   `discoverDeclaredMethods` (L279-281) skips private methods and additionally blocks
   `ForbiddenObjectMethods` (getClass/notify/finalize...). Matches the documented
   NopIoC contract (docs-for-ai/02-core-guides/ioc-and-config.md).
3. **Constructor autowiring prefers public constructors**
   (BeanDefinitionBuilder.autowireConstructorArgs, L518-527): public-first sort —
   no preference inversion that could pick an unexpected synthetic constructor.
4. **Secret-leak sweep clean** (Phase 1 evidence): zero `@cfg:`/`@InjectValue` with
   password/secret/token/encrypt-ish keys in main code of the three module groups;
   zero `@sec:` values in DSL resources; the only secret-ish `_vfs` config hit is a
   codegen test template with an EMPTY password (commented `nop-test` example =
   plan-328-adjudicated test-default pattern).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-C3-1 | MEDIUM | nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java:67-72, 82, 206-214, 225-247; nop-kernel/nop-commons/src/main/java/io/nop/commons/CommonConfigs.java:61-64; nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java:461-470; nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:36 | When no encryption key is configured (both `nop.config.encrypt-key` and `nop.crypt.default-enc-key` default to `""`), `@sec:` config values and `enc`-tagged ORM columns are encrypted/decrypted with a **publicly derivable** key: v1 path = PBKDF2(empty password, `DEFAULT_V1_SALT` fixed public constant), legacy path = `MD5("" + "")` with the hardcoded static IV literal `"*(<K:00a9mf8ia7Nn3^y34%FER{3/"` at AESTextCipher.java:70. There is no fail-closed and no startup/first-use warning; `decrypt()` proceeds silently (GCM tag still protects integrity, but confidentiality vs. anyone with source access is nil). The default `nopOrmColumnBinderEnhancer` bean wiring (orm-defaults.beans.xml:36) does not inject a cipher key, so column encryption silently inherits the same weak default. | Phase 3 fix candidate: (a) emit a one-time WARN (or fail-closed under a strict flag) when `@sec:`/`enc` is used with an empty enc-key; (b) consider requiring explicit opt-in for the empty-key development mode; (c) document key configuration as a deployment prerequisite next to the existing `@sec:` guide section. Classification follows plan-328 precedent that *default values* are deployment constraints — the framework-defect component is the silent, warning-less weak-key operation, not the empty default itself. |
| F-C3-PMD-1 | NONE (false positive, recorded) | AESTextCipher.java:235 | PMD `HardCodedCryptoKey` hit on `buildV1SecretKey` variable `factory`: the flagged line is `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` — an algorithm name, not a key. Disposition: false positive; no remediation. (Watch item folded into F-C3-1: `DEFAULT_V1_SALT` public-constant fallback when saltKey empty.) | None (FP). |

## Explicit no-finding statements

- No secret-bearing `@cfg:` key in the audited module groups is logged, embedded in
  exception params, or persisted (sweep + enhancer code path verified).
- `@sec:` decryption cannot be bypassed by another enhancement path (single
  implementation of the prefix check; ConfigStarter's pluggable enhancer class is
  deployment-trusted configuration, same trust class as beans.xml itself).
- No injection into private fields possible (reflection model excludes them before
  the IoC layer ever sees a member).
- SpotBugs: zero security-critical bug types (no hard-coded password, no SQL/crypto
  patterns) across the three module groups; PMD: only the dispositioned FP above.

## Adjudication (Phase 3 input)

- F-C3-1: `remediation-target` (MEDIUM) — fail-loud/strict-mode hardening; distinct
  from roadmap item 2's "JWT enc-key empty default" (different mechanism: JWT HMAC
  keys in nop-biz-auth-core vs config/column cipher here; no double ownership).
- F-C3-PMD-1: `adjudicated-no-fix` (false positive).

## Owner mapping

- F-C3-1 successor owner: roadmap item 9 consolidation triage (fix phase batch).
- Boundary: `AESTextCipher` v1 format design is plan-334 heritage (completed;
  per-message random IV verified in code); nop-credential Vault KMS integration is
  item 3's scope (uses its own delegation, not this default path).

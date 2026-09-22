# CORE-01 — Key Management Controls Audit

> Mission: security-audit (roadmap item 1, deliverable CORE-01)
> Date: 2026-09-18. Auditor: ZCode session (plan 2026-09-17-0831, Phase 2).
> Scope: `nop-core-framework/nop-security/` key management (`io.nop.security.key`).
> Method: full read of all 6 classes in package + consumer/wiring search (repo-wide,
> main+test) + focused test review. Contract carrier: `IKeyManager` Javadoc plus
> implementation behavior and focused tests (`docs-for-ai/03-modules/` has no dedicated
> nop-security owner doc — per plan).

## Scope verified

| Control class | Path | Verified behavior |
|---|---|---|
| IKeyManager | nop-core-framework/nop-security/src/main/java/io/nop/security/key/IKeyManager.java | Interface: getCertificate/getPublicKey/getPrivateKey/getCertificateIds |
| DefaultKeyManager | .../key/DefaultKeyManager.java | Loads JKS keystore from VFS resource; resolves keys only from configured store |
| CompositeKeyManager | .../key/CompositeKeyManager.java | First-match-wins across ordered managers |
| KeyBean / KeySetBean | .../key/KeyBean.java, KeySetBean.java | JWKS-style key metadata beans |
| KeySetHelper | .../key/KeySetHelper.java | Publishes JWKS from certificates (public material only) |

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-C1-1 | LOW | CompositeKeyManager.java:20-45 | Key-ID collision across sources resolves silently first-match-wins; no diagnostic when two managers expose the same keyId/certId. Mis-ordered configuration shadows a key with no trace. | Log a WARN (or fail at init) when building the composite list if duplicate IDs are detectable; at minimum document first-match ordering in Javadoc. |
| F-C1-2 | LOW | DefaultKeyManager.java:20 | Default `storeType = KEY_STORE_JKS` — legacy keystore format (weaker integrity than PKCS12, JDK default since 9 is PKCS12). | Change default to PKCS12, or document JKS-vs-PKCS12 guidance at the injection site. |
| F-C1-3 | LOW | DefaultKeyManager.java:39-44 | `destroy()` nulls the KeyStore but never zeros the `storePassword` char[]; password stays in heap until GC. | Zero the char[] in destroy(). |

## Explicit no-finding statements (controls verified clean)

- **No public-seam private-key exposure in-platform**: repo-wide search (main + test
  code) found ZERO consumers of `IKeyManager` / `KeySetHelper` outside the
  `io.nop.security.key` package itself; no `beans.xml` wiring references any key
  manager. The control surface is dormant framework utility — no live path currently
  surfaces `getPrivateKey()` to callers. (Contract carried by Javadoc + tests:
  `TestKeyManager` covers load→sign→verify through `DefaultKeyManager`;
  `TestKeySetHelperJwks` covers JWKS encoding regression.)
- **KeySetHelper publishes only public material**: JWKS output contains
  modulus/exponent/alg/kid/use only; private keys never enter KeyBean.
- **Key resolution confined to configured sources**: `DefaultKeyManager` reads a single
  configured VFS keystore; no path resolves keys from arbitrary locations.
- **Key-set rotation semantics**: `KeySetBean.getKeyById` linear lookup by `kid`;
  multiple keys per set supported. No rotation API exists to abuse — rotation is a
  consumer concern (none in-platform today).

## Adjudication (Phase 3 input)

- All three findings: `remediation-target` (LOW) — candidates for roadmap Phase 3
  hardening batch; none blocking.
- Dormant-surface observation recorded for item 9 consolidation: if a future consumer
  wires IKeyManager into an HTTP seam (e.g. JWKS endpoint), re-audit exposure before
  shipping.

## Owner mapping

- All findings owned by roadmap item 9 consolidation triage (fix phase).
- No overlap with plans 333/335/336 (auth/browser/bash/SSRF) or sibling audit plans
  (item 2 auth / item 3 credential) — those own their own key consumers (e.g. JWT
  signing keys are handled by `nop-biz-auth-core`, audited by item 2).

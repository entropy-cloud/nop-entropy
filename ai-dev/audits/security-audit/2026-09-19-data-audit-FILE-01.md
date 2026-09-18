# FILE-01 — File Upload/Download Path Traversal Controls Audit

> Mission: security-audit (roadmap item 8, deliverable FILE-01)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2344-10, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3 (P0=CRITICAL … P3=LOW).
> Scope: upload/download filename sanitization, storage key construction, download
> path authorization, content-type/extension validation — `nop-biz-file-core`
> (`NopFileStoreBizModel`, `AbstractGraphQLFileService`, `MediaTypeHelper`,
> `UploadRequestBean`), `nop-file-dao` (`DaoResourceFileStore`), `nop-file-service`
> wiring, boundary reads of `nop-spring-file` REST controller and
> `nop-graphql-core` WebContent rendering.
> Method: full read of every file above; cross-check against the plugin-coordinate
> whitelist precedent (CORE-02 §"Path traversal defense") and owner doc
> `docs-for-ai/03-modules/nop-file.md`.

## Verified control chain (upload → storage key → download)

1. **Storage key construction is whitelist-validated at the single construction
   site**: `DaoResourceFileStore.newPath` (`nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:246-269`)
   rejects `bizObjName` failing `StringHelper.isValidSimpleVarName` (Java
   identifier, no `$` — StringHelper.java:1968-1970) and `fileExt` failing
   `[A-Za-z0-9]+` (L250-251) BEFORE concatenation. Path = bucket?/bizObjName/YYYY/MM/DD/`UUID`.ext
   — all variable segments validated; the client-supplied **fileName never
   enters the storage path**. This is a stronger per-segment whitelist than the
   CORE-02 coordinate regex precedent (same defense class).
2. **Double validation on the GraphQL entry**: `NopFileStoreBizModel.upload`
   (`nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/NopFileStoreBizModel.java:100-113`)
   re-validates bizObjName (checkBizObjName L94-98) before `saveFile`; `newPath`
   validates again (defense-in-depth for direct `IFileStore` consumers that
   bypass the BizModel).
3. **REST multipart filename is basename-normalized**: `SpringFileService.upload`
   (`nop-spring/nop-spring-file/src/main/java/io/nop/file/spring/web/SpringFileService.java:47`)
   applies `StringHelper.fileFullName` = `lastPart(path,'/')` (StringHelper.java:2897-2899).
   The direct GraphQL path stores fileName verbatim — harmless because fileName
   is only persisted to DB and echoed back (see 5) and never used for filesystem
   addressing.
4. **Download resolves by DB primary key, not by path**: `NopFileStoreBizModel.download`
   (L115-127) → `DaoResourceFileStore.getFile` (L158-162) → `requireEntityById(fileId)`;
   `filePath` is read from the DB row, never from the request. A traversal-shaped
   `fileId` simply fails entity lookup (fail-closed).
5. **Response header injection blocked at render**: `GraphQLResponseHelper.consumeWebContent`
   (`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/utils/GraphQLResponseHelper.java:112-118`)
   URL-encodes the filename into `Content-Disposition: attachment` — CRLF in a
   stored fileName cannot split headers; the `attachment` disposition also
   prevents inline rendering of uploaded HTML/SVG.
6. **Extension whitelist is case-normalized and fail-closed when configured**:
   `checkFileExt` (NopFileStoreBizModel.java:81-92) lowercases both sides with
   `Locale.ROOT` before comparison; pinned by
   `nop-biz-file-core/src/test/.../TestNopFileStoreBizModel.java`.
7. **Upload size is enforced on the declared length and the byte stream**:
   `checkMaxSize` (L75-79, default 16MB via `@cfg:nop.file.upload.max-size|16777216`
   L63) plus `LimitedInputStream(is, record.getLength())` /
   `LimitedInputStream(is, maxLength)` in `saveFile` (DaoResourceFileStore.java:199-209)
   — a client under-declaring length cannot push extra bytes past the declared
   cap; a client over-declaring is rejected up front.
8. **Chunk upload is a non-functional stub**: all three operations of
   `ChunkFileUploadHandler` (`nop-biz-file-core/.../chunk/ChunkFileUploadHandler.java:17-32`)
   throw `UnsupportedOperationException` (pinned by TestChunkFileUploadHandler) —
   no chunked-upload attack surface exists.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-F1-1 | LOW | `DaoResourceFileStore.java:246-269`; `LocalResourceStore.java:40-48,64-69` (`nop-kernel/nop-core`) | Traversal defense is validated at the `newPath` construction site only; `LocalResourceStore` itself does no canonicalization (`new File(dir, relativePath)`). Any future caller that passes an unvalidated path into `resourceStore.saveResource`/`getResource` silently loses the defense — same caller-side-validation pattern flagged as F-C4-1 in CORE-04 (SqlBuilder), i.e. a recurring structural pattern, not a live vulnerability. | Phase 3 hardening candidate: add a `..`/absolute-path rejection inside `LocalResourceStore.getResource/saveResource` (defense at render boundary), or hard-deprecate direct unvalidated use. Cross-reference F-C4-1 for the shared pattern. |
| F-F1-2 | LOW | `NopFileStoreBizModel.java:115-127` (download `contentType` param); `GraphQLResponseHelper.java:110` | Download honors a client-supplied `contentType` query param over the stored MIME type. Combined with `isPublic=true` files this lets a link-crafter serve a stored file as `text/html`; practical impact is bounded because Content-Disposition is always `attachment` when fileName is non-blank (L112-116) and the file must already be readable by the victim. No CRLF injection path (header value written through Spring `HttpHeaders.set`, filename URL-encoded). | Optional hardening: restrict the override to a MIME-type-safe allowlist (or ignore the param for non-public files and trust the stored `mimeType`). |
| F-F1-3 | LOW (config default) | `NopFileStoreBizModel.java:69-72` (`@cfg:nop.file.upload.allowed-file-exts|` default empty) | Default extension whitelist is empty = all extensions accepted (16MB cap only). Upload of `.html`/`.svg`/`.svgx` content is possible for any user holding `NopFileStore:mutation`; stored-XSS-by-proxy is mitigated by the `attachment` disposition (control 5), but deployments serving files through other consumers (OSS CDN `externalPath`, see owner doc FileStatusBean) may render inline. | Deployment hardening checklist (item 9): set `nop.file.upload.allowed-file-exts` per application; document inline-rendering risk for OSS/CDN-backed stores (`getExternalPath` extension point). |

## Explicit no-finding statements

- No path traversal path found on either upload or download: every storage path
  segment is whitelist-validated (bizObjName identifier check, ext `[A-Za-z0-9]+`,
  fileId server-generated UUID) and downloads resolve strictly via DB entity id.
- No header injection via stored fileName (URL-encoded before Content-Disposition).
- No unbounded upload stream (declared-length + LimitedInputStream double bound).
- Chunked upload not implemented (explicit stub) — no surface.
- `MediaTypeHelper` config source is developer-controlled VFS resource
  (`/nop/file/media-type.json`), not user input.

## Adjudication (Phase 3 input)

- F-F1-1: `remediation-target` (LOW, defense-in-depth; same family as F-C4-1).
- F-F1-2: `remediation-target` (LOW, optional hardening).
- F-F1-3: `adjudicated deployment-configuration constraint` — pointer for item 9
  deployment hardening checklist (parallel to AUTH-01 enc-key disposition).

## Owner mapping

- F-F1-1..F-F1-3 successor owner: roadmap item 9 consolidation triage.
- Download *authorization* semantics (who may fetch which fileId) is FILE-02
  scope, not re-audited here. No overlap with item 1 (CORE-02 plugin path
  whitelist — different module) or item 3 (credentials).

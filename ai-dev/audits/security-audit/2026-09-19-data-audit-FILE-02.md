# FILE-02 — File Storage Access Control Audit

> Mission: security-audit (roadmap item 8, deliverable FILE-02)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2344-10, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3 (P0=CRITICAL … P3=LOW).
> Scope: object-level authorization on download/delete, `isPublic` handling, dedup
> side channels, metadata leakage — `NopFileStoreBizModel.loadFileRecord`/
> `checkUploadAuth`, `DefaultBizAuthChecker`, `DaoResourceFileStore`
> (attach/detach/copyFile/changePublic/isUniqueRef/getFileStatus),
> `NopFileRecordBizModel` + nop-file xmeta/action-auth surface, `OrmFileComponent`
> boundary.
> Method: full read of the classes above plus wiring
> (`app-file-core.beans.xml`, `app-file-dao.beans.xml`, `_service.beans.xml`,
> `biz-defaults.beans.xml`) and the nop-file ORM/action-auth models.

## Verified controls

1. **Object-level read check on download (non-public files)**:
   `NopFileStoreBizModel.loadFileRecord` (`nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/NopFileStoreBizModel.java:135-141`)
   calls `bizAuthChecker.forceCheckAuth(bizObjName, bizObjId, fieldName, ctx)`
   for every `isPublic=false` record.
   `DefaultBizAuthChecker.checkAuth` (`nop-service-framework/nop-biz/src/main/java/io/nop/biz/impl/DefaultBizAuthChecker.java:43-66`)
   enforces this by invoking the owning biz object's `__get` with the record's
   `bizObjId` — the full CrudBizModel row-level (data-auth) path — plus field-level
   auth when the GraphQL field def declares `auth`, and throws on unknown
   fieldName (L53-58).
2. **Temp files are fail-closed on download**: temp uploads carry
   `bizObjId = "__TEMP__"` (`DaoResourceFileStore.saveFile`
   `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:178-183`);
   `forceCheckAuth` → `__get("__TEMP__")` fails entity lookup, so an unattached
   file cannot be downloaded through the auth-checked path by anyone other than
   paths that legitimately re-attach it.
3. **bizAuthChecker is wired in the default assembly**: `nopBizAuthChecker`
   (`DefaultBizAuthChecker`, `ioc:default="true"`) is registered in
   `nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:89`
   and nop-file-service `_service.beans.xml` extends `service-base.beans.xml`;
   the `@Nullable` injection on NopFileStoreBizModel only degrades when an
   application deliberately removes the nop-biz defaults (see finding F-F2-3).
4. **Cross-object attachment is blocked**: `attachFile` (DaoResourceFileStore.java:363-372)
   throws `ERR_FILE_ATTACH_FILE_NOT_SAME_OBJ` unless `record.bizObjName` equals
   the attaching entity's name (OrmFileComponent flush path); `detachFile`
   (L333-349) only deletes when the (bizObjName, bizObjId, fieldName) triple
   matches and only removes the physical file when `isUniqueRef` (L351-360)
   finds no other record sharing `originFileId`.
5. **FileStatus metadata requires triple match**: `getRecord` (L298-310) throws
   `ERR_FILE_NOT_ALLOW_ACCESS_FILE` unless (fileId, bizObjName, objId, fieldName)
   all match — entity GraphQL consumers cannot read file metadata of files
   attached to other objects.
6. **No content-addressed dedup on upload**: `saveFile` never computes or
   compares `fileHash`; dedup exists only as the explicit `copyFile` operation
   (L375-394) which shares `filePath` under a new record with
   `isPublic=false` hard-set. Consequence: **no upload-oracle dedup side
   channel** (an attacker cannot probe file existence by re-uploading known
   content and observing dedup behavior).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-F2-1 | MEDIUM (P2) | `nop-file/nop-file-service/src/main/java/io/nop/file/service/entity/NopFileRecordBizModel.java` (plain CrudBizModel); `nop-file/nop-file-web/src/main/resources/_vfs/nop/file/auth/_nop-file.action-auth.xml` (only coarse `NopFileRecord:query` / `:mutation` FNPTs); `nop-file/model/nop-file.orm.xml:29-67` (all columns published, incl. `filePath`, `bizObjId`, `isPublic`) | `NopFileRecord` CRUD has **no object-level authorization**: any holder of the coarse `NopFileRecord:mutation` permission can (a) flip any record's `isPublic` to true — which then bypasses the download auth check entirely (NopFileStoreBizModel.java:137) — (b) delete other users' file records (breaks their entity file bindings; physical file orphaned or removed via isUniqueRef on ORM cascade), and (c) enumerate all users' file metadata (`fileName`, `bizObjId`, `createdBy`, `filePath`) via `:query`. In the demo wiring these permissions sit under the "test-orm-nop-file" menu, but nothing restricts them to admins at the module level. | Add a default xmeta/xbiz `data-auth` or `@Auth` on NopMetaFileRecord-equivalent here: NopFileRecord save/update/delete (at minimum guard `isPublic` changes to owners/admins), or mark `isPublic` non-updatable via GraphQL and expose a dedicated owner-checked action (mirror datav `requireOwner` precedent in `NopDatavExportTaskBizModel.downloadExportFile:237-249`). |
| F-F2-2 | LOW (P3) | `NopFileStoreBizModel.checkUploadAuth` (`nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/NopFileStoreBizModel.java:129-133`) | Upload-time authorization runs only when `bizObjId` is non-empty and checks **read** (`__get`) semantics, not write: a user who can read entity X can create file records pre-attached to X (`bizObjName=X`, `bizObjId=<X id>`) without write permission on X. Download of such records still requires read on X, so exposure equals the reader's own visibility; impact is record pollution / linking files to objects the user cannot write. Empty-`bizObjId` uploads (temp files) perform no object check at all — bounded only by the GraphQL `NopFileStore:mutation` permission and the 16MB cap; cleanup of `__TEMP__` rows is an application duty (owner doc notes it). | Optional: require write-ish permission for attach-on-upload (e.g. `__update` probe or a dedicated check), and document a temp-file TTL/cleanup job for deployments. |
| F-F2-3 | LOW (P3, wiring constraint) | `NopFileStoreBizModel.java:44-51,137` (`@Nullable IBizAuthChecker`) | If an application assembles file-core without the nop-biz defaults (`nopBizAuthChecker` bean absent), non-public file download silently degrades to no object check (fail-open on missing bean). Default wiring includes the checker (verified biz-defaults.beans.xml:89), so this is a conditional degradation, not a default-state vulnerability. | Fail-fast instead of null-tolerant: log a WARN at startup when `bizAuthChecker == null`, or make the bean required with an explicit allow-anonymous escape hatch. |

## Explicit no-finding statements

- No dedup side channel: upload does not content-hash or dedup; `fileHash` is
  only copied by `copyFile` (DaoResourceFileStore.java:387).
- No metadata leak via `FileStatusBean`: triple-match guard verified
  (getRecord L298-310); `externalPath`/`previewPath` are null in the base store.
- No unauthorized `changePublic` path: sole production caller is
  `OrmFileComponent.changePublic` (flush-time component op,
  `nop-persistence/nop-orm/.../OrmFileComponent.java:44-50`); no GraphQL action
  exposes it. (The `isPublic` **column** exposure is finding F-F2-1, a distinct
  surface.)
- Download of temp files is fail-closed (entity lookup failure), verified via
  control 2.
- Export/report file downloads in datav are owner-checked
  (`NopDatavExportTaskBizModel.downloadExportFile` `@Auth` + `requireOwner`,
  nop-datav-service L237-249) — no gap there.

## Adjudication (Phase 3 input)

- F-F2-1: `remediation-target` (MEDIUM — object-level authz gap on the file
  record admin surface; the sharpest edge is `isPublic` flip enabling
  auth-check bypass).
- F-F2-2: `remediation-target` (LOW) or `adjudicated design constraint` — read
  semantics for attach-on-upload is defensible (exposure bounded by reader
  visibility); recommend adjudication with the write-probe option recorded.
- F-F2-3: `adjudicated deployment-configuration constraint` (default wiring
  verified safe); item 9 deployment checklist pointer.

## Owner mapping

- F-F2-1, F-F2-2 successor owner: roadmap item 9 (fix work item candidate in
  nop-file). ORM xmeta changes touch the nop-file model files (Protected Area:
  plan-first if schema-level; auth annotations are service-layer).
- No overlap with item 2 (AUTH — token enforcement), item 4 (API — GraphQL
  coarse-grained auth mechanics), or item 7 (AI). FILE-01 owns traversal/content
  type; this report owns authorization/exposure only.

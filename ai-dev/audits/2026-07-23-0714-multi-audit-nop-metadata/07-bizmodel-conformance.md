> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Dimension 07: BizModel Conformance

### [Dimension07-01] NopMetaQualityCheckpointBizModel.delete override missing `@Name` annotation on `id` parameter

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java:235`
- **Evidence**:
  ```java
  @Override
  public boolean delete(String id, IServiceContext context) {
      notifySchedulerUnregister(id);
      return super.delete(id, context);
  }
  ```
- **Severity**: P2
- **Status**: The override drops the `@Name("id")` annotation. Java parameter annotations are not inherited in overrides.
- **Risk**: When called via I*Biz dynamic proxy, the BizProxyInvocationHandler cannot map the parameter without @Name.
- **Suggestion**: Add `@Name("id")` to the method signature.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension07-02] NopMetaDataContractBizModel mutation methods bypass requireEntity() data auth

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java:41,70,131`
- **Evidence**:
  ```java
  public NopMetaDataContract approve(@Name("id") String id, IServiceContext context) {
      NopMetaDataContract entity = dao().getEntityById(id);  // should be requireEntity()
      ...
  ```
- **Severity**: P2
- **Status**: Five mutation methods load entities via `dao().getEntityById()` rather than `requireEntity(id, action, context)`. Three of those (`approve`, `reject`, `checkContract`) also skip `checkDataAuth()` entirely.
- **Risk**: Users with access to the GraphQL mutation endpoint can change contract lifecycle states without any data permission check.
- **Suggestion**: Replace `dao().getEntityById(id)` with `requireEntity(id, "approve"/"reject"/"checkContract", context)`.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension07-03] NopMetaTagLabelBizModel uses BeanContainer.getBeanByType() service locator

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTagLabelBizModel.java:45,53`
- **Evidence**:
  ```java
  LineageTagPropagationService svc = BeanContainer.getBeanByType(LineageTagPropagationService.class);
  return svc.propagateTags(entityType, entityId, tagId, context);
  ```
- **Severity**: P3
- **Status**: BizModel retrieves dependencies via static `BeanContainer.getBeanByType()` rather than `@Inject` fields.
- **Risk**: Dependencies hidden from IoC static analysis. Tests must mock static BeanContainer. Errors occur at invocation time, not at startup.
- **Suggestion**: Declare both as `@Inject`-ed protected fields.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension07-04] `AutoClassificationService` and `LineageTagPropagationService` violate naming convention

- **File**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/AutoClassificationService.java`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/LineageTagPropagationService.java`
- **Evidence**:
  ```java
  public class AutoClassificationService {
  public class LineageTagPropagationService {
  ```
- **Severity**: P3
- **Status**: Nop convention avoids `*Service` suffix (reserved for Spring convention). These are utility helpers named with prohibited suffix.
- **Suggestion**: Rename to `*Processor` suffix to align with Nop naming conventions.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension07-05] NopMetaModuleBizModel exceeds appropriate BizModel complexity (586 lines)

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java`
- **Evidence**: 586 lines, 17 methods (11 private), implementing ORM parsing, XML DSK resolution, cross-module aggregation, manifest building, event publishing.
- **Severity**: P3
- **Status**: The BizModel hosts operations across multiple domains. At least `importOrmModel`, `generateManifest`, and `buildGlobalClassNameToModuleId` meet the criteria for Processor extraction per service-layer.md.
- **Suggestion**: Extract `OrmModelImportProcessor` and `ManifestGenerationProcessor`.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension07-06] NopMetaDataContractBizModel uses reflective self-call via bizObjectManager()

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java:165-168`
- **Evidence**:
  ```java
  return (NopMetaDataContract) bizObjectManager().getBizObject("NopMetaDataContract")
          .invoke("submitForApproval", Map.of("id", contractId), null, context);
  ```
- **Severity**: P3
- **Status**: Private helper uses reflective BizObject invocation for a method provided by `approval-support.xbiz`.
- **Risk**: If `approval-support.xbiz` is not deployed, the reflective call fails at runtime.
- **Suggestion**: Document the xbiz dependency in the class javadoc.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension07-07] 7 BizModels load "before" snapshots via dao().getEntityById() in save overrides

- **File**: `NopMetaTableBizModel.java:87-103`, `NopMetaEntityBizModel.java:28`, `NopMetaEntityFieldBizModel.java:29`, `NopMetaModuleBizModel.java:125`, `NopMetaTagBizModel.java:30`, `NopMetaClassificationBizModel.java:29`, `NopMetaGlossaryTermBizModel.java:43`
- **Evidence**:
  ```java
  NopMetaTable before = id != null ? dao().getEntityById(id) : null;
  ```
- **Severity**: P3
- **Status**: The `dao().getEntityById()` for loading "before" snapshot is acceptable for read-only event building, but loads without data auth check.
- **Risk**: If user has permission to call `save` but not to read the entity, snapshot creation could leak info through event publish path.
- **Suggestion**: Use `requireEntity(id, "save", context)` for event snapshot loading.
- **Confidence**: speculative
- **Review Status**: unreviewed

---

### [Dimension07-08] NopMetaReconciliationConfigBizModel uses non-standard constructor injection

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaReconciliationConfigBizModel.java:76-80`
- **Evidence**:
  ```java
  @Inject
  public NopMetaReconciliationConfigBizModel(IReconciliationProcessor reconciliationService) {
      setEntityName(NopMetaReconciliationConfig.class.getName());
      ...
  }
  ```
- **Severity**: P3
- **Status**: Only BizModel in the module using constructor injection. All 39 others use no-arg constructor + `@Inject` field pattern.
- **Suggestion**: Refactor to standard no-arg constructor + `@Inject` setter pattern.
- **Confidence**: likely
- **Review Status**: unreviewed

---

## Summary

| ID | Finding | Severity | Confidence |
|----|---------|----------|------------|
| 07-01 | NopMetaQualityCheckpointBizModel.delete missing `@Name("id")` | P2 | certain |
| 07-02 | NopMetaDataContractBizModel mutation methods bypass requireEntity() | P2 | certain |
| 07-03 | BeanContainer.getBeanByType() service locator | P3 | certain |
| 07-04 | *Service naming convention violation | P3 | certain |
| 07-05 | NopMetaModuleBizModel excessive complexity (586 lines) | P3 | likely |
| 07-06 | Reflective self-call via bizObjectManager() | P3 | likely |
| 07-07 | 7 BizModels use dao().getEntityById() for snapshot loading | P3 | speculative |
| 07-08 | Non-standard constructor injection | P3 | likely |

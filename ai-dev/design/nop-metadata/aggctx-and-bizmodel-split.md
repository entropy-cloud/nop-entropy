# AggregationContext & BizModel Split Design

> Status: completed
> Last Reviewed: 2026-07-22

## Decision 1: AggregationContext → AggregationHelper + extracted resolvers

**AggregationContext.java (1854 lines → ~464 lines)**

Keep in `AggregationContext`:
- Per-request state fields + constructor + getters/setters
- Inner data types: `MeasureSpec`, `DimensionSpec`, `JoinMeasureSpec`, `JoinDimensionSpec`, `JoinField`, `CrossDbFieldSpec`, `CrossDbMeasureSpec`, `CrossDbDimensionSpec`, `CrossDbField`
- `MemAggAccumulator` + subclasses (`SumAcc`, `CountAcc`, `AvgAcc`, `MinAcc`, `MaxAcc`, `CountDistinctAcc`)
- `JoinFieldResolverFn` functional interface

Extract to `AggregationHelper.java` (same package, package-level utility class):
- All public static helper methods (34 methods): `safeAlias`, `buildResult`, `aggSqlOf`, `executeJdbcQuery`, `collectRows`, `requireName`, `resolveTableColumnNames`, `resolveExternalFieldOrThrow`, `resolveSharedDataSourceOrThrow`, `resolveEntityColumns`, `rewriteFilterToColumns`, `resolveEntityFieldColumn`, `buildNameToExprTable`, `buildJoinNameToExprTable`, `nameResolverFor`, `buildOrderByClause`, `loadMeasures`, `loadDimensions`, `endpointTypeOf`, `newArrayHolder`, `containsIgnoreCase`, `equalsStr`, `crossDbAliasOf`, `getCaseInsensitiveObj`, `findKeyIgnoreCase`, `safeProductName`, `messageOf`, `externalTableFromForJoin`, `buildEntityFromClause`, `isEntityTableVisible`, `checkTableExists`, `toBigDecimal`, `buildFromClause`, `buildExternalAggregationSql`, `collectBindParams`, `buildExternalExternalJoinSql`, `buildMixedSameDbJoinSql`, `buildCrossDbNameToAliasTable`, `resolveAndValidateLookupKeys`, `memoryGroupBy`, `truncateCrossDb`, `loadJoinMeasuresWithResolver`, `loadJoinDimensionsWithResolver`

Extract resolvers to separate top-level classes (same package):
- `CrossDbFieldResolver` → separate file (was inner class, 206 lines)
- `JoinFieldResolver` → separate file (was inner class, 87 lines)
- `JoinExternalSideResolver` → separate file (was inner class, 78 lines)
- `JoinMixedSideResolver` → separate file (was inner class, 120 lines)

Rationale: resolvers are service classes with database access, not data types. Extracting them significantly reduces AggregationContext line count while keeping the true data types co-located.

## Decision 2: NopMetaTableBizModel → NopMetaTableQueryAction (Helper)

**NopMetaTableBizModel.java (902 lines → 389 lines)**

Keep in `NopMetaTableBizModel`:
- CRUD overrides (save/delete)
- Dispatch methods with `@BizMutation`/`@BizQuery` annotations
- Helper methods used by dispatch methods
- Delegates data access execution to `NopMetaTableQueryAction` helper

Extract to `NopMetaTableQueryAction.java` (same package, plain helper class, no `@BizModel`):
- Private query execution methods: `queryEntityData`, `queryExternalData`, `querySqlData`
- Supporting components: `SqlSelectFieldExtractor`, `MetaTableFieldResolver`, `MetaDataSourceResolver`, `MetaTableReferenceResolver`, `FilterToSqlTranslator`
- DTO conversion helpers: `buildProfileResultDTO`

Note: Two `@BizModel` approach was attempted but Nop framework does not support duplicate `@BizModel` names on separate classes. The final design uses a single `@BizModel("NopMetaTable")` class (NopMetaTableBizModel) that delegates to the helper class NopMetaTableQueryAction via constructor-injected composition.

## Decision 3: NopMetaLineageEdgeBizModel → NopMetaLineageEdgeQueryAction (Helper)

**NopMetaLineageEdgeBizModel.java (885 lines → 168 lines)**

Keep in `NopMetaLineageEdgeBizModel`:
- CRUD + `recordLineage` mutation
- `@BizQuery`/`@BizMutation` dispatch methods for all lineage actions
- Config injection: `configuredMaxEdges`, `configuredMaxTables`

Extract to `NopMetaLineageEdgeQueryAction.java` (same package, plain helper class):
- Graph traversal: `getUpstream`, `getDownstream`, `getLineagePath`, `getImpactAnalysis`
- SQL extraction: `extractLineageFromSql`, `extractColumnLineageFromSql`, `extractMeasureLineage`
- Shared helpers: `bfsForward`, `bfsForwardByColumn`, `buildLineageGraph`, `buildTableNameIndex`, `loadExistingTableIds`, all upsert/delete methods
- Inner types: `LineageGraph`, `LineageExtractResult`

Rationale: Lineage-specific implementation is fully contained in the helper class. BizModel acts as thin GraphQL dispatch layer with no implementation logic.

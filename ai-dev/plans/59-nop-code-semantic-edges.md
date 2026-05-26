# Plan 59: nop-code Semantic Edge Model Implementation

> Plan Status: **completed**
> Created: 2026-05-26
> Module: `nop-code`

## Purpose

Implement the semantic edge model from `ai-dev/design/nop-code/semantic-edge-design.md`. This adds LLM-ready semantic relationship detection on top of existing AST-based analysis.

## Goals

1. Define `CodeSemanticEdge` model in `nop-code-core` with confidence levels and relation types
2. Define `ISemanticEdgeExtractor` interface in `nop-code-core`
3. Implement 3 deterministic extractors: name-similarity, doc-keyword, annotation-pattern
4. Add `nop_code_semantic_edge` ORM table
5. Wire extractors into ProjectAnalyzer pipeline
6. Add BizModel for semantic edge CRUD and queries
7. Persist and query semantic edges via GraphQL

## Non-Goals

- LLM-based extractors (far future, requires nop-ai integration)
- `findSemanticPath` query (depends on this plan, separate follow-up)
- Community detection integration with semantic edges (optimization, separate)

## Current Baseline

- All nop-code modules build and test successfully
- `nop-code-core` has SymbolTable, CallGraph, CodeSymbol models
- `ProjectAnalyzer` produces `ProjectAnalysisResult` with symbols, calls, inheritances, annotations
- All semantic edge model components, extractors, ORM table, and BizModel implemented and tested

## Exit Criteria

- [x] `CodeSemanticEdge` model class exists in `nop-code-core` with all fields from design doc
- [x] `EdgeConfidence` enum (EXTRACTED=10, INFERRED=20, AMBIGUOUS=30) exists
- [x] `SemanticRelationType` enum with 8 predefined types exists
- [x] `ISemanticEdgeExtractor` interface exists with extract/requiresLlm/estimatedTokens methods
- [x] `NameSimilarityExtractor` (name-sim) generates semantically_similar_to edges
- [x] `DocKeywordExtractor` (doc-keyword) generates conceptually_related_to edges
- [x] `AnnotationPatternExtractor` (annotation-pattern) generates conceptually_related_to edges
- [x] `nop_code_semantic_edge` ORM table defined in nop-code.orm.xml
- [x] `ProjectAnalysisResult` has `semanticEdges` field populated by extractors
- [x] `NopCodeSemanticEdgeBizModel` exists with standard CRUD
- [x] `CodeIndexService` persists semantic edges to DB during indexing
- [x] All existing tests pass + new tests for extractors

## Execution

### Slice 1: Core model + interfaces
- [x] Create CodeSemanticEdge, EdgeConfidence, SemanticRelationType in nop-code-core
- [x] Create ISemanticEdgeExtractor interface
- [x] Add semanticEdges to ProjectAnalysisResult
- [x] Tests for model classes

### Slice 2: Deterministic extractors
- [x] Implement NameSimilarityExtractor
- [x] Implement DocKeywordExtractor
- [x] Implement AnnotationPatternExtractor
- [x] Register extractors in ProjectAnalyzer
- [x] Tests for each extractor

### Slice 3: ORM + Service integration
- [x] Add nop_code_semantic_edge table to nop-code.orm.xml
- [x] Run codegen to generate DAO entities
- [x] Add NopCodeSemanticEdgeBizModel (generated)
- [x] Wire semantic edge persistence in CodeIndexService
- [x] Integration tests (existing tests pass)

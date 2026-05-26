# Plan 59: nop-code Semantic Edge Model Implementation

> Plan Status: **in_progress**
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
- No semantic edge model, extractors, or ORM table exist

## Exit Criteria

- [ ] `CodeSemanticEdge` model class exists in `nop-code-core` with all fields from design doc
- [ ] `EdgeConfidence` enum (EXTRACTED=10, INFERRED=20, AMBIGUOUS=30) exists
- [ ] `SemanticRelationType` enum with 8 predefined types exists
- [ ] `ISemanticEdgeExtractor` interface exists with extract/requiresLlm/estimatedTokens methods
- [ ] `NameSimilarityExtractor` (name-sim) generates semantically_similar_to edges
- [ ] `DocKeywordExtractor` (doc-keyword) generates conceptually_related_to edges
- [ ] `AnnotationPatternExtractor` (annotation-pattern) generates conceptually_related_to edges
- [ ] `nop_code_semantic_edge` ORM table defined in nop-code.orm.xml
- [ ] `ProjectAnalysisResult` has `semanticEdges` field populated by extractors
- [ ] `NopCodeSemanticEdgeBizModel` exists with standard CRUD
- [ ] `CodeIndexService` persists semantic edges to DB during indexing
- [ ] All existing tests pass + new tests for extractors

## Execution

### Slice 1: Core model + interfaces
- [ ] Create CodeSemanticEdge, EdgeConfidence, SemanticRelationType in nop-code-core
- [ ] Create ISemanticEdgeExtractor interface
- [ ] Add semanticEdges to ProjectAnalysisResult
- [ ] Tests for model classes

### Slice 2: Deterministic extractors
- [ ] Implement NameSimilarityExtractor
- [ ] Implement DocKeywordExtractor
- [ ] Implement AnnotationPatternExtractor
- [ ] Register extractors in ProjectAnalyzer
- [ ] Tests for each extractor

### Slice 3: ORM + Service integration
- [ ] Add nop_code_semantic_edge table to nop-code.orm.xml
- [ ] Run codegen to generate DAO entities
- [ ] Add NopCodeSemanticEdgeBizModel
- [ ] Wire semantic edge persistence in CodeIndexService
- [ ] Integration tests

package io.nop.metadata.service;

/**
 * This file has been split into three smaller test files:
 * <ul>
 *   <li>{@link TestAggregationCategoricalAndTemporal} — categorical, temporal, countDistinct,
 *       expression measures, having/orderBy, arithmetic having, save-time validation</li>
 *   <li>{@link TestAggregationExternalJoinAndPagination} — external↔external JOIN (same/cross DB),
 *       mixed-endpoint JOIN, side/failure paths, JOIN having/orderBy, JOIN expression,
 *       cross-DB memory GROUP BY</li>
 *   <li>{@link TestAggregationEntityJoinAndComplex} — entity aggregation, entity temporal
 *       granularity bucketing, entity↔entity JOIN, JOIN failure paths, refactor regression</li>
 * </ul>
 * <p>Shared helpers are in {@link TestAggregationHelper}.
 *
 * @see TestAggregationCategoricalAndTemporal
 * @see TestAggregationExternalJoinAndPagination
 * @see TestAggregationEntityJoinAndComplex
 * @see TestAggregationHelper
 */
public class TestNopMetaAggregationBizModel {
    // Tests moved to the three split files above.
    protected TestNopMetaAggregationBizModel() {
    }
}

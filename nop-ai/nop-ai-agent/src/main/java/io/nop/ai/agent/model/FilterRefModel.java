package io.nop.ai.agent.model;

import io.nop.ai.agent.model._gen._FilterRefModel;

/**
 * W3-2 (declarative filter chain): an ordered reference to a named filter
 * (declared in {@code <filter-definitions>}). The {@code ref} attribute is the
 * filter ID; the optional {@code points} attribute overrides the default
 * lifecycle-point mapping (D2):
 * <ul>
 *   <li>An input-filter with no {@code points} maps to {@code PRE_CALL}
 *       (request boundary, single trigger per request).</li>
 *   <li>An output-filter with no {@code points} maps to {@code POST_CALL}
 *       (response boundary, single trigger per request).</li>
 *   <li>When {@code points} is set, the filter is registered at each named
 *       lifecycle point instead of the default.</li>
 * </ul>
 */
public class FilterRefModel extends _FilterRefModel {
    public FilterRefModel() {
    }
}

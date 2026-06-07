package io.nop.code.core.model;

import java.util.List;

public interface IHeuristicEdgeSynthesizer {
    String getSynthesizerId();

    List<CodeMethodCall> synthesize(HeuristicContext context);
}

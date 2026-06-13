package io.nop.ai.agent.compact;

import io.nop.ai.agent.session.CompactionResult;

public interface IContextCompactor {

    CompactionResult compact(CompactionContext ctx);
}

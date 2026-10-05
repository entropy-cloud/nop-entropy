package io.nop.auth.service.mock;

import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.ShardSelection;

import java.util.HashMap;
import java.util.Map;

/**
 * plan 2320 编译适配：IShardSelector死面移除（isSupportShard/selectShards零调用方已删），
 * 本类仅保留selectShard并复用接口缺省方法（missing-shard-policy等缺省NONE，
 * 维持既有测试语义：按实体名查Map返回固定分片）。
 */
public class MockShardSelector implements IShardSelector {
    private Map<String, ShardSelection> shardSelections = new HashMap<>();

    public void addShardSelection(String entityName, ShardSelection selection) {
        shardSelections.put(entityName, selection);
    }

    @Override
    public ShardSelection selectShard(String entityName, String shardProp, Object shardValue) {
        return shardSelections.get(entityName);
    }
}

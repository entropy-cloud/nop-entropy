package io.nop.auth.service.mock;

import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.ShardSelection;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockShardSelector implements IShardSelector {
    private Map<String, ShardSelection> shardSelections = new HashMap<>();

    public void addShardSelection(String entityName, ShardSelection selection) {
        shardSelections.put(entityName, selection);
    }

    @Override
    public boolean isSupportShard(String entityName) {
        return shardSelections.containsKey(entityName);
    }

    @Override
    public ShardSelection selectShard(String entityName, String shardProp, Object shardValue) {
        return shardSelections.get(entityName);
    }

    @Override
    public List<ShardSelection> selectShards(String entityName, String shardProp, Object beginValue, Object endValue) {
        ShardSelection selection = shardSelections.get(entityName);
        if (selection == null)
            return Collections.emptyList();
        return Collections.singletonList(selection);
    }
}
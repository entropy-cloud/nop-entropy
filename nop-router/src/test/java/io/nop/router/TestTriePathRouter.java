package io.nop.router;

import io.nop.router.trie.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTriePathRouter {
    @Test
    public void testMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/test", "a");
        router.addPathPattern("/test/{id}", "b");
        router.addPathPattern("/test/{id}/data", "c");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/test/3/data");
        assertEquals(Arrays.asList("test", "3", "data"), result.getPath());
        RouteValue<String> v = result.getValue().get(0);
        assertEquals(Arrays.asList(null, "id", null), v.getVarNames());
        assertEquals("c", v.getValue());
    }
}

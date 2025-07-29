package io.nop.tool.counter;

import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

public class TestSourceCounter {
    @Disabled
    @Test
    public void testCount() {
        File dir = new File("c:/can/nop/nop-entropy");
        SourceCounter counter = new SourceCounter();
        counter.count(dir);

        Map<String, Long> results = counter.getResults();
        System.out.println(JsonTool.serialize(results, true));
    }
}

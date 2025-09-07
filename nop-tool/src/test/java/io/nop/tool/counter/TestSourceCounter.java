package io.nop.tool.counter;

import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

@Disabled
public class TestSourceCounter {
    @Test
    public void testCount() {
        File dir = new File("c:/can/nop/nop-entropy");
        Map<String, Map<String, Long>> allData = new TreeMap<>();

        for (File moduleDir : dir.listFiles()) {
            if (!moduleDir.isDirectory())
                continue;

            if (!moduleDir.getName().startsWith("nop-"))
                continue;

            if (moduleDir.getName().startsWith("."))
                continue;

            if (moduleDir.getName().startsWith("_"))
                continue;

            if (moduleDir.getName().startsWith("docs"))
                continue;

            if (moduleDir.getName().equals("deploy"))
                continue;

            if (moduleDir.getName().equals("nop-biz-batch"))
                continue;

            if (moduleDir.getName().equals("nop-demo"))
                continue;

            if (moduleDir.getName().equals("nop-web-amis-editor"))
                continue;

            if (moduleDir.getName().equals("nop-web-site"))
                continue;

            SourceCounter counter = new SourceCounter();
            counter.count(moduleDir);

            Map<String, Long> results = counter.getResults();
            allData.put(moduleDir.getName(), results);
        }

        System.out.println(JsonTool.serialize(allData, true));

        Map<String, AtomicLong> allSum = new TreeMap<>();
        for (Map<String, Long> item : allData.values()) {
            item.forEach((key, value) -> {
                allSum.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(value);
            });
        }

        System.out.println("=======\n" + JsonTool.serialize(allSum, true));
    }

    @Disabled
    @Test
    public void testOrm() {
        File dir = new File("c:/can/nop/nop-entropy/nop-orm");
        SourceCounter counter = new SourceCounter();
        counter.setLogDetails(true);
        counter.count(dir);

        System.out.println(JsonTool.serialize(counter.getResults(), true));
    }
}

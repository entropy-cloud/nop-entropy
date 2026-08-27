package io.nop.batch.dsl.manager;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.dsl.model.BatchConsumerModel;
import io.nop.batch.dsl.model.BatchTaskModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.nop.batch.dsl.BatchDslErrors.ERR_BATCH_TASK_CONSUMER_FOR_TAG_NO_TAGGER;
import static io.nop.batch.dsl.BatchDslErrors.ERR_BATCH_TASK_NO_LOADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * consumer配置了forTag但任务未配置tagger时属于无效配置：
 * 多consumer场景会被静默替换为空消费者（数据全部丢弃），单consumer场景forTag被静默忽略。
 * 必须在构建期显式报错而不是静默吞掉。
 */
public class TestBatchTaskForTagValidation {

    private BatchConsumerModel newConsumer(String name, String forTag) {
        BatchConsumerModel consumer = new BatchConsumerModel();
        consumer.setName(name);
        consumer.setForTag(forTag);
        return consumer;
    }

    private ModelBasedBatchTaskBuilderFactory newFactory(BatchTaskModel model) {
        return new ModelBasedBatchTaskBuilderFactory(model, null, null, null, null, null, null, null);
    }

    @Test
    public void testForTagWithoutTaggerRejected() {
        BatchTaskModel model = new BatchTaskModel();
        model.setTaskName("test.forTag");
        model.setConsumers(new ArrayList<>(Arrays.asList(
                newConsumer("c1", "tag1"), newConsumer("c2", "tag2"))));

        NopException ex = assertThrows(NopException.class,
                () -> newFactory(model).newTaskBuilder(null));
        assertEquals(ERR_BATCH_TASK_CONSUMER_FOR_TAG_NO_TAGGER.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testSingleForTagConsumerWithoutTaggerRejected() {
        // 单consumer + forTag + 无tagger：HEAD上forTag被静默忽略，同样必须报错
        BatchTaskModel model = new BatchTaskModel();
        model.setTaskName("test.forTag.single");
        model.setConsumers(new ArrayList<>(List.of(newConsumer("only", "tag1"))));

        NopException ex = assertThrows(NopException.class,
                () -> newFactory(model).newTaskBuilder(null));
        assertEquals(ERR_BATCH_TASK_CONSUMER_FOR_TAG_NO_TAGGER.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testPlainConsumersWithoutForTagPassValidation() {
        // 未使用forTag时不触发校验，构建继续走到缺loader的既有报错（证明校验放行）
        BatchTaskModel model = new BatchTaskModel();
        model.setTaskName("test.forTag.plain");
        model.setConsumers(new ArrayList<>(Arrays.asList(
                newConsumer("c1", null), newConsumer("c2", null))));

        NopException ex = assertThrows(NopException.class,
                () -> newFactory(model).newTaskBuilder(null));
        assertEquals(ERR_BATCH_TASK_NO_LOADER.getErrorCode(), ex.getErrorCode());
    }
}

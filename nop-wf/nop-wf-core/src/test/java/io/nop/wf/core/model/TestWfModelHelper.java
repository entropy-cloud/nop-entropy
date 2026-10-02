package io.nop.wf.core.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.wf.core.model.utils.WfModelHelper;
import org.junit.jupiter.api.Test;

import static io.nop.wf.core.NopWfCoreErrors.ARG_PATH;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_INVALID_WF_FILE_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI5: WfModelHelper 路径推导语义测试。wfName 从不同资源前缀下的文件路径推导，
 * 无目录分隔符时显式报错而非越界。
 */
public class TestWfModelHelper {
    @Test
    public void testGuessWfNameFromNopWfPath() {
        assertEquals("demo/approval", WfModelHelper.guessWfNameFromFilePath("/nop/wf/demo/approval/v1.xwf"));
    }

    @Test
    public void testGuessWfNameFromWfNamespace() {
        // wf: 前缀剥离后保留首个路径分隔符
        assertEquals("/demo/approval", WfModelHelper.guessWfNameFromFilePath("wf:/demo/approval/v2.xwf"));
    }

    @Test
    public void testGuessWfNameFromResolveNamespace() {
        assertEquals("demo", WfModelHelper.guessWfNameFromFilePath("resolve-wf:demo/v1.xwf"));
    }

    @Test
    public void testGuessWfNameWithoutDirectoryThrows() {
        try {
            WfModelHelper.guessWfNameFromFilePath("approval-v1.xwf");
            fail("expect ERR_WF_INVALID_WF_FILE_PATH");
        } catch (NopException e) {
            assertEquals(ERR_WF_INVALID_WF_FILE_PATH.getErrorCode(), e.getErrorCode());
            assertEquals("approval-v1.xwf", e.getParam(ARG_PATH));
        }
    }
}

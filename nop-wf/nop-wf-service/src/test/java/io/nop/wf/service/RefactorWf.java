/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * 历史一次性迁移工具：startStepId/stepId 等旧字段改名为新契约。所有 wf 测试资源已迁移完毕，
 * 保留类定义供追溯；运行会对源资源做格式重写（副作用）并对 x:extends 继承无本地 start 的
 * 文件抛 NPE，故禁用（与 TestFluxPage.regenerateSnapshots 的 @Disabled 惯例一致）。
 */
@Disabled("历史一次性迁移工具，源资源已迁移完毕")
public class RefactorWf extends BaseTestCase {
    @Test
    public void refactorName() {
        File file = new File(getTestResourcesDir(), "_vfs/nop/wf/test");
        File[] files = file.listFiles();
        for (File subFile : files) {
            File dir = new File(subFile.getParentFile(), StringHelper.fileNameNoExt(subFile.getName()));
            File wfFile = new File(dir, "v1.xwf");
            XNode node = XNodeParser.instance().parseFromResource(new FileResource(wfFile));
            node.setTagName("workflow");
            node.setAttr("x:schema", "/nop/schema/wf/wf.xdef");
            node.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");
            node.removeAttr("x:extended");

            refactorNode(node);

            node.saveToResource(new FileResource(wfFile), null);
        }
    }

    void refactorNode(XNode node) {
        XNode start = node.childByTag("start");
        if (start == null)
            return;
        start.renameAttr("startStepId", "startStepName");

        XNode steps = node.childByTag("steps");
        steps.forEachChild(step -> {
            step.renameAttr("id", "name");

            XNode transition = step.childByTag("transition");
            if (transition != null) {
                for (XNode to : transition.getChildren()) {
                    to.renameAttr("stepId", "stepName");
                }
            }

            XNode assignment = step.childByTag("assignment");
            if (assignment != null) {
                String selection = assignment.attrText("selection");
                if ("multipleSelect".equals(selection)) {
                    assignment.setAttr("selection", "multiple");
                } else if ("noSelect".equals(selection)) {
                    assignment.setAttr("selection", "auto");
                } else if ("singleSelect".equals(selection)) {
                    assignment.setAttr("selection", "single");
                } else if ("all".equals(selection)) {
                    assignment.setAttr("selection", "auto");
                }
                assignment.forEachChild(actor -> {
                    actor.renameAttr("id", "actorId");
                    actor.renameAttr("type", "actorType");
                });
                XNode actors = assignment.childByTag("actors");
                if (actors == null) {
                    actors = XNode.make("actors");
                    actors.appendChildren(assignment.detachChildren());
                    assignment.appendChild(actors);
                }

                for (int i = 0, n = actors.getChildCount(); i < n; i++) {
                    XNode actor = actors.child(i);
                    if (actor.attrText("actorModelId") == null) {
                        actor.setAttr("actorModelId", "actor" + (i + 1));
                    }
                }
            }

            XNode refActions = step.childByTag("ref-actions");
            if (refActions != null) {
                refActions.forEachChild(refAction -> {
                    refAction.renameAttr("actionId", "name");
                });
            }
        });

        XNode actions = node.childByTag("actions");
        if (actions != null) {
            actions.forEachChild(action -> {
                action.renameAttr("id", "name");

                XNode transition = action.childByTag("transition");
                if (transition != null) {
                    for (XNode to : transition.getChildren()) {
                        to.renameAttr("stepId", "stepName");
                    }
                }
            });
        }
    }
}

/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.graph.dag.Dag;
import io.nop.core.resource.component.ResourceVersionHelper;
import io.nop.wf.core.model._gen._WfModel;
import io.nop.wf.core.model.analyze.WfModelAnalyzer;

public class WfModel extends _WfModel implements IWorkflowModel, INeedInit {
    private Dag dag;

    public WfModel() {

    }

    @Override
    public Dag getDag() {
        return dag;
    }

    public void setDag(Dag dag) {
        this.dag = dag;
    }

    public WfStepModel getStartStep() {
        return getStep(getStart().getStartStepName());
    }

    public void init() {
        if (getWfName() == null) {
            guessWfNameFromPath();
        }
        new WfModelAnalyzer().analyze(this);
    }

    private void guessWfNameFromPath() {
        String path = resourcePath();
        if (path == null)
            return;

        String fileName = StringHelper.fileNameNoExt(path);
        if (ResourceVersionHelper.endsWithNumberVersion(fileName)) {
            long version = ResourceVersionHelper.getNumberVersion(fileName);
            setWfVersion(version);

            String wfName = ResourceVersionHelper.getModelName(path);
            setWfName(wfName);
        }
    }
}

package io.nop.core.lang.eval;

import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;

public final class EvalRuntime implements IVariableScope {
    private final IEvalScope scope;
    private ExitMode exitMode;
    private EvalFrame currentFrame;
    private IEvalOutput out = DisabledEvalOutput.INSTANCE;

    public EvalRuntime(IEvalScope scope) {
        this.scope = scope;
    }

    public EvalRuntime(IEvalScope scope, IEvalOutput out) {
        this(scope);
        this.out = out;
    }

    public EvalRuntime(IEvalScope scope, EvalFrame frame) {
        this(scope);
        this.currentFrame = frame;
    }

    public IEvalScope getScope() {
        return scope;
    }

    public ExitMode getExitMode() {
        return exitMode;
    }

    public void setExitMode(ExitMode exitMode) {
        this.exitMode = exitMode;
    }

    public IEvalOutput getOut() {
        return out;
    }

    public void setOut(IEvalOutput out) {
        this.out = out;
    }

    public EvalFrame getCurrentFrame() {
        return currentFrame;
    }

    public EvalFrame getFrame(int frameIndex) {
        if (frameIndex <= 0)
            return getCurrentFrame();

        EvalFrame frame = getCurrentFrame();
        for (int i = 0; i < frameIndex; i++) {
            frame = frame.getParentFrame();
            if (frame == null)
                return null;
        }
        return frame;
    }

    public void pushFrame(EvalFrame frame) {
        this.currentFrame = frame;
    }

    public void popFrame() {
        if (currentFrame != null)
            currentFrame = currentFrame.getParentFrame();
    }

    @Override
    public Object getValueByPropPath(String propPath) {
        return scope.getValueByPropPath(propPath);
    }

    public Object getValue(String name) {
        return scope.getValue(name);
    }

    public boolean containsValue(String name) {
        return scope.containsValue(name);
    }

    public Object getLocalValue(String name) {
        return scope.getLocalValue(name);
    }

    public void setLocalValue(String name, Object value) {
        scope.setLocalValue(name, value);
    }

    public void setLocalValue(SourceLocation loc, String name, Object value) {
        scope.setLocalValue(loc, name, value);
    }

    public EvalRuntime getRuntimeForFrame(int frameIndex) {
        EvalFrame frame = getFrame(frameIndex);
        if (frame == null) {
            frame = new EvalFrame(null, new String[0]);
        }
        return new EvalRuntime(scope, frame);
    }
}
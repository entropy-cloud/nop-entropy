package io.nop.ai.shell.io;

import java.util.ArrayList;
import java.util.List;

/**
 * Tee输出实现，将输出同时发送到多个输出目标
 */
public class TeeOutput implements IShellOutput {
    
    private final List<IShellOutput> outputs;
    private final List<IShellOutput> ownedOutputs; // 需要被关闭的输出
    
    public TeeOutput(IShellOutput... outputs) {
        this.outputs = new ArrayList<>();
        this.ownedOutputs = new ArrayList<>();
        for (IShellOutput output : outputs) {
            addOutput(output);
        }
    }
    
    public void addOutput(IShellOutput output) {
        this.outputs.add(output);
    }
    
    public void addOwnedOutput(IShellOutput output) {
        this.outputs.add(output);
        this.ownedOutputs.add(output);
    }
    
    @Override
    public void print(String text) {
        for (IShellOutput output : outputs) {
            output.print(text);
        }
    }
    
    @Override
    public void println(String text) {
        for (IShellOutput output : outputs) {
            output.println(text);
        }
    }
    
    @Override
    public void println() {
        for (IShellOutput output : outputs) {
            output.println();
        }
    }
    
    @Override
    public void flush() {
        for (IShellOutput output : outputs) {
            output.flush();
        }
    }
    
    @Override
    public IShellInput asInput() {
        // Tee输出转换为输入可能不直接支持
        // 可以返回第一个输出的asInput()，或者抛出异常
        if (!outputs.isEmpty()) {
            return outputs.get(0).asInput();
        }
        throw new UnsupportedOperationException("No output available to convert to input");
    }
    
    @Override
    public void close() {
        // 只关闭拥有的输出，不关闭传入的外部输出
        for (IShellOutput output : ownedOutputs) {
            try {
                output.close();
            } catch (Exception e) {
                // 忽略关闭异常
            }
        }
        outputs.clear();
        ownedOutputs.clear();
    }
}
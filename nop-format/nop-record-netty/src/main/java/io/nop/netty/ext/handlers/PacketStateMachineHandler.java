package io.nop.netty.ext.handlers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.fsm.execution.IStateMachine;
import io.nop.netty.NopNettyConstants;
import io.nop.xlang.api.XLang;

public class PacketStateMachineHandler extends ChannelDuplexHandler {
    private final IStateMachine stateMachine;
    private final IEvalScope scope = XLang.newEvalScope();
    private Object stateValue = null;

    public PacketStateMachineHandler(IStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.stateValue = stateMachine.getInitStateValue();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        String event = this.stateMachine.getEvent(NopNettyConstants.PREFIX_RECV, msg, scope);

        PacketProcess process = new PacketProcess(ctx, msg);
        scope.setLocalValue(PacketProcess.VAR_PROCESS, process);

        stateMachine.transit(this.stateValue, event, scope, (stateModel, stateValue) -> {
            this.stateValue = stateValue;
        });

        if (process.shouldDrop()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        // 有可能已经被替换对象, 所以需要从process获取
        ctx.fireChannelRead(process.getMsg());
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
        ctx.flush();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        String event = this.stateMachine.getEvent(NopNettyConstants.PREFIX_SEND, msg, scope);

        PacketProcess process = new PacketProcess(ctx, msg);
        scope.setLocalValue(PacketProcess.VAR_PROCESS, process);

        stateMachine.transit(this.stateValue, event, scope, (stateModel, stateValue) -> {
            this.stateValue = stateValue;
        });

        if (process.shouldDrop()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        // 有可能已经被替换对象, 所以需要从process获取
        ctx.write(process.getMsg(), promise);
    }
}

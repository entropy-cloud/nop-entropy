package io.nop.netty.ext.server;

import io.nop.codec.IPacketCodec;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.fsm.execution.IStateMachine;
import io.nop.fsm.execution.StateMachine;
import io.nop.fsm.model.StateMachineModel;
import io.nop.netty.config.NettyTcpClientConfig;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.impl.ModelBasedPacketCodec;
import io.nop.record.model.PacketCodecModel;

public class ModelBasedTcpClientConfig extends NettyTcpClientConfig {
    private String stateMachinePath;
    private String packetModelPath;

    public ModelBasedTcpClientConfig() {
        setUseChannelGroup(true);
        setChannelCloseOnError(true);
    }

    public String getStateMachinePath() {
        return stateMachinePath;
    }

    public void setStateMachinePath(String stateMachinePath) {
        this.stateMachinePath = stateMachinePath;
    }

    public String getPacketModelPath() {
        return packetModelPath;
    }

    public void setPacketModelPath(String packetModelPath) {
        this.packetModelPath = packetModelPath;
    }

    public IStateMachine loadStateMachine() {
        if (StringHelper.isEmpty(stateMachinePath))
            return null;
        StateMachineModel stm = (StateMachineModel) ResourceComponentManager.instance().loadComponentModel(stateMachinePath);
        return new StateMachine(stm);
    }

    public IPacketCodec<Object> loadPacketCodec(FieldCodecRegistry registry) {
        PacketCodecModel codecModel = (PacketCodecModel) ResourceComponentManager.instance().loadComponentModel(packetModelPath);
        return new ModelBasedPacketCodec(codecModel, registry);
    }
}

package io.nop.ai.agent.talent;

import io.nop.ai.agent.engine.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestITalent {

    @Test
    void interfaceContractCanBeImplemented() {
        ITalent talent = new ITalent() {
            @Override
            public boolean isSupported(AgentExecutionContext ctx) {
                return true;
            }

            @Override
            public void onAttach(AgentExecutionContext ctx) {
            }

            @Override
            public String getInstruction(AgentExecutionContext ctx) {
                return "extra-instruction";
            }

            @Override
            public List<String> getTools(AgentExecutionContext ctx) {
                return List.of("read_file");
            }
        };

        assertTrue(talent.isSupported(null));
        assertEquals("extra-instruction", talent.getInstruction(null));
        assertEquals(List.of("read_file"), talent.getTools(null));
    }

    @Test
    void getToolsReturnsRegistryToolNames() {
        ITalent talent = new ITalent() {
            @Override
            public boolean isSupported(AgentExecutionContext ctx) {
                return false;
            }

            @Override
            public void onAttach(AgentExecutionContext ctx) {
            }

            @Override
            public String getInstruction(AgentExecutionContext ctx) {
                return null;
            }

            @Override
            public List<String> getTools(AgentExecutionContext ctx) {
                return List.of("read_file", "write_file");
            }
        };

        List<String> tools = talent.getTools(null);
        assertEquals(2, tools.size());
        assertEquals("read_file", tools.get(0));
        assertEquals("write_file", tools.get(1));
    }

    @Test
    void instructionMayBeNullForInactiveTalent() {
        ITalent talent = new ITalent() {
            @Override
            public boolean isSupported(AgentExecutionContext ctx) {
                return false;
            }

            @Override
            public void onAttach(AgentExecutionContext ctx) {
            }

            @Override
            public String getInstruction(AgentExecutionContext ctx) {
                return null;
            }

            @Override
            public List<String> getTools(AgentExecutionContext ctx) {
                return List.of();
            }
        };

        assertTrue(null == talent.getInstruction(null));
        assertTrue(talent.getTools(null).isEmpty());
    }
}

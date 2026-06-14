package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the {@link ChannelKind} enum value set matches design §5.3.
 */
public class TestChannelKind {

    @Test
    void hasExactlyFourChannels() {
        ChannelKind[] values = ChannelKind.values();
        assertEquals(4, values.length,
                "ChannelKind must have exactly 4 values per design §5.3");
    }

    @Test
    void valuesMatchDesignSpec() {
        assertEquals(ChannelKind.WEBUI, ChannelKind.valueOf("WEBUI"));
        assertEquals(ChannelKind.API, ChannelKind.valueOf("API"));
        assertEquals(ChannelKind.DM, ChannelKind.valueOf("DM"));
        assertEquals(ChannelKind.GROUP, ChannelKind.valueOf("GROUP"));
    }
}

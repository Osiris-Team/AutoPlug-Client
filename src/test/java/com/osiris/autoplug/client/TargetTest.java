package com.osiris.autoplug.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TargetTest {

    @Test
    public void testFromString() {
        // Test valid input strings
        assertEquals(Target.MINECRAFT_CLIENT, Target.fromString("MINECRAFT_CLIENT"));
        assertEquals(Target.MINECRAFT_SERVER, Target.fromString("MINECRAFT_SERVER"));
        assertEquals(Target.MINDUSTRY_SERVER, Target.fromString("MINDUSTRY_SERVER"));
        assertEquals(Target.MINDUSTRY_CLIENT, Target.fromString("MINDUSTRY_CLIENT"));
        assertEquals(Target.OTHER, Target.fromString("OTHER"));

        // Test invalid input string
        assertNull(Target.fromString("INVALID_TARGET"));
    }
}

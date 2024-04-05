package com.osiris.autoplug.client.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UtilsRandomTest {

    @Test
    void testGenerateNewKey() {
        UtilsRandom utilsRandom = new UtilsRandom();
        int length = 16;

        String key = utilsRandom.generateNewKey(length);

        assertNotNull(key);
        assertEquals(length, key.length());

        // Check if the generated key contains only alphanumeric characters
        assertTrue(key.matches("[a-zA-Z0-9]+"));
    }
}

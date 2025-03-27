package com.osiris.autoplug.client.tasks.updater;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class UtilsUpdaterTest {

    @Test
    public void testValidEntryAboveThreshold() {
        HashSet<UtilsUpdater.Entry> set = new HashSet<>();
        set.add(new UtilsUpdater.Entry("key1", 5));
        set.add(new UtilsUpdater.Entry("key2", 10)); // Highest usage

        UtilsUpdater.Entry result = UtilsUpdater.getValidEntryWithMostUsages(set, 5);
        assertNotNull(result);
        assertEquals("key2", result.key);
        assertEquals(10, result.usage);
    }

    @Test
    public void testNoEntryAboveThreshold() {
        HashSet<UtilsUpdater.Entry> set = new HashSet<>();
        set.add(new UtilsUpdater.Entry("key1", 2));
        set.add(new UtilsUpdater.Entry("key2", 4)); // All below minUsages

        UtilsUpdater.Entry result = UtilsUpdater.getValidEntryWithMostUsages(set, 5);
        assertNull(result);
    }

    @Test
    public void testEntryWithInvalidKey() {
        HashSet<UtilsUpdater.Entry> set = new HashSet<>();
        set.add(new UtilsUpdater.Entry("null", 20));
        set.add(new UtilsUpdater.Entry("0", 25));
        set.add(new UtilsUpdater.Entry("validKey", 15));

        UtilsUpdater.Entry result = UtilsUpdater.getValidEntryWithMostUsages(set, 10);
        assertEquals("validKey", result.key);
    }



}
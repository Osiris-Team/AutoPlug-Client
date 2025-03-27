package com.osiris.autoplug.client.tasks.updater;

import com.osiris.jlib.sort.QuickSort;

import java.util.HashSet;
import java.util.Objects;

/**
 * Utility class for updater-related helper methods like entry sorting and filtering.
 */
public class UtilsUpdater {
    public static Entry getValidEntryWithMostUsages(HashSet<Entry> _set, int minUsages) {
        Entry[] entries = new QuickSort().sort(_set.toArray(new Entry[0]), (thisEl, otherEl) -> {
            int thisUsage = ((Entry) thisEl.obj).usage;
            int otherUsage = ((Entry) otherEl.obj).usage;
            return Integer.compare(thisUsage, otherUsage);
        });

        for (int i = entries.length - 1; i >= 0; i--) {
            int usages = entries[i].usage;
            if (usages < minUsages) return null;
            String key = entries[i].key;
            if (!Objects.equals(key, "null") && !Objects.equals(key, "0"))
                return entries[i];
        }
        return null;
    }

    // Include the Entry class here as well
    public static class Entry {
        public String key;
        public int usage;

        public Object obj;

        public Entry(String key, int usage) {
            this.key = key;
            this.usage = usage;
            this.obj = this; // for QuickSort
        }
    }
}

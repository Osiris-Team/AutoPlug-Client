package com.osiris.autoplug.client.ui;

/** A monotonic estimate for work whose complete byte/work count is not known in advance. */
final class OverallProgress {
    private double value;
    void start() { value = 2; }
    int tick() { if (value < 100) value = Math.min(95, value + (95 - value) * 0.008); return value(); }
    int value() { return (int) value; }
    int complete() { value = 100; return 100; }
}

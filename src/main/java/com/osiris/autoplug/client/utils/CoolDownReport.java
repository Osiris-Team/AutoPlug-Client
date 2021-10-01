/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

public class CoolDownReport {
    private long msPassedSinceLast;
    private long msCoolDown;

    public CoolDownReport(long msPassedSinceLast, long msCoolDown) {
        this.msPassedSinceLast = msPassedSinceLast;
        this.msCoolDown = msCoolDown;
    }

    public long getMsRemaining() {
        return msCoolDown - msPassedSinceLast;
    }

    public boolean isInCoolDown() {
        return msPassedSinceLast < msCoolDown;
    }

    public long getMsPassedSinceLast() {
        return msPassedSinceLast;
    }

    public void setMsPassedSinceLast(long msPassedSinceLast) {
        this.msPassedSinceLast = msPassedSinceLast;
    }

    public long getMsCoolDown() {
        return msCoolDown;
    }

    public void setMsCoolDown(long msCoolDown) {
        this.msCoolDown = msCoolDown;
    }
}

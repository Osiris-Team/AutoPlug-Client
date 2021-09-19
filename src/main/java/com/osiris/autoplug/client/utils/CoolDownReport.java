/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

public class CoolDownReport {
    private boolean outOfCoolDown;
    private long msPassedSinceLast;
    private long msCoolDown;

    public CoolDownReport(boolean outOfCoolDown, long msPassedSinceLast, long msCoolDown) {
        this.outOfCoolDown = outOfCoolDown;
        this.msPassedSinceLast = msPassedSinceLast;
        this.msCoolDown = msCoolDown;
    }

    public long getMsRemaining() {
        return msCoolDown - msPassedSinceLast;
    }

    public boolean isOutOfCoolDown() {
        return outOfCoolDown;
    }

    public void setOutOfCoolDown(boolean outOfCoolDown) {
        this.outOfCoolDown = outOfCoolDown;
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

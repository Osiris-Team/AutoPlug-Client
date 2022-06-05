/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.utils;

import java.awt.*;

public class CompWrapper {
    public Component component;

    public CompWrapper(Component component) {
        this.component = component;
    }

    @Override
    public String toString() {
        return component.getClass().getSimpleName() + "@" + Integer.toHexString(component.hashCode());
    }
}

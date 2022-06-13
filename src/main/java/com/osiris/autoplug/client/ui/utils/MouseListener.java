/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.utils;

import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Wrapper around {@link java.awt.event.MouseListener}
 * which provides Java 8 style listener and results
 * in cleaner/less code.
 */
public class MouseListener implements java.awt.event.MouseListener {
    private Consumer<MouseEvent> onClick, onPressed, onReleased, onEntered, onExited;

    public MouseListener onClick(Consumer<MouseEvent> event) {
        this.onClick = event;
        return this;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (onClick != null) onClick.accept(e);
    }


    public MouseListener onPress(Consumer<MouseEvent> event) {
        this.onPressed = event;
        return this;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (onPressed != null) onPressed.accept(e);
    }


    public MouseListener onRelease(Consumer<MouseEvent> event) {
        this.onReleased = event;
        return this;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (onReleased != null) onReleased.accept(e);
    }


    public MouseListener onEnter(Consumer<MouseEvent> event) {
        this.onEntered = event;
        return this;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (onEntered != null) onEntered.accept(e);
    }


    public MouseListener onExit(Consumer<MouseEvent> event) {
        this.onExited = event;
        return this;
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (onExited != null) onExited.accept(e);
    }
}

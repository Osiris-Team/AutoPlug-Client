/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Magical container that makes layouting
 * as simple as writing basic english. <p>
 */
public class MyContainer extends JPanel {
    /**
     * Styles for this container.
     */
    public Styles styles = new Styles();
    /**
     * Default child component styles. <br>
     */
    public Styles defaultCompStyles = new Styles().center().padding();
    public Map<Component, Styles> compsAndStyles = new HashMap<>();
    public boolean isDebug = false;

    /**
     * Defaults width & height to 100% of the parent.
     */
    public MyContainer() {
        this(100, 100);
    }

    public MyContainer(int widthPercent, int heightPercent) {
        super(new MyLayout(new Dimension(0, 0)));
        setBackground(new Color(0, true)); // transparent
        updateSize(widthPercent, heightPercent);
    }

    public void updateSize(int widthPercent, int heightPercent) {
        int parentWidth, parentHeight;
        Container parent = this.getParent();
        if (parent != null) {
            parentWidth = parent.getWidth();
            parentHeight = parent.getHeight();
        } else { // If no parent provided use the screen dimensions
            parentWidth = java.awt.Toolkit.getDefaultToolkit().getScreenSize().width;
            parentHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        }

        Dimension size = new Dimension(parentWidth / 100 * widthPercent,
                parentHeight / 100 * heightPercent);
        ((MyLayout) getLayout()).minimumSize = size;
        ((MyLayout) getLayout()).preferredSize = size;
        setSize(size);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        updateUI();
    }

    public void updateUI() {
        revalidate();
        repaint();
    }

    /**
     * Access this container in a thread-safe way. <br>
     * Performs {@link #updateUI()} when done running the provided code.
     *
     * @param code to be run in this containers' context.
     */
    public synchronized MyContainer access(Runnable code) {
        code.run();
        updateUI();
        return this;
    }

    /**
     * Adds this component horizontally and
     * additionally returns its {@link Styles}. <p>
     * Its {@link Styles} are pre-filled with
     * {@link #defaultCompStyles} of this container.
     */
    public Styles addH(Component comp) {
        super.add(comp);
        Styles styles = new Styles();
        styles.getMap().putAll(defaultCompStyles.getMap()); // Add defaults
        styles.horizontal();
        compsAndStyles.put(comp, styles);
        return styles;
    }

    /**
     * Adds this component vertically and
     * additionally returns its {@link Styles}.<p>
     * Its {@link Styles} are pre-filled with
     * {@link #defaultCompStyles} of this container.
     */
    public Styles addV(Component comp) {
        super.add(comp);
        Styles styles = new Styles();
        styles.getMap().putAll(defaultCompStyles.getMap()); // Add defaults
        styles.vertical();
        compsAndStyles.put(comp, styles);
        return styles;
    }

    /**
     * @throws IllegalArgumentException when provided layout
     *                                  not of type {@link MyLayout}.
     */
    @Override
    public void setLayout(LayoutManager mgr) {
        if (mgr instanceof MyLayout)
            super.setLayout(mgr);
        else
            throw new IllegalArgumentException("Layout must be of type: " + MyLayout.class.getName());
    }

    /**
     * Returns the styles for the provided child component.
     */
    public Styles getChildStyles(Component comp) {
        return compsAndStyles.get(comp);
    }

}

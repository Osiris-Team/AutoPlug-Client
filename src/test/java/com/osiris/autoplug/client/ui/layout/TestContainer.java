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
public class TestContainer extends JPanel {
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

    public TestContainer() {
        super(new TestLayout(new Dimension(1000, 100)));
    }

    // |c1c2c3|
    // components can be packed together like above
    // |c1 c2 c3|
    // components can have gaps between each other
    // solve by adding component paddings in px for: right, left, top, bottom
    // |c1 c2          c3|
    // can have absolute postions like in this case at the end.
    // solve by adding component positions for: right, left, top, bottom, center
    // default setting for layout: center
    // |c1 +C2+ c3|
    // can be vertically bigger or smaller than others

    /**
     * Access this container in a thread-safe way. <br>
     * Performs {@link #revalidate()} and {@link #repaint()} when done running the provided code.
     *
     * @param code to be run in this containers' context.
     */
    public synchronized TestContainer access(Runnable code) {
        code.run();
        revalidate();
        repaint();
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
     *                                  not of type {@link TestLayout}.
     */
    @Override
    public void setLayout(LayoutManager mgr) {
        if (mgr instanceof TestLayout)
            super.setLayout(mgr);
        else
            throw new IllegalArgumentException("Layout must be of type: " + TestLayout.class.getName());
    }

    /**
     * Returns the styles for the provided child component.
     */
    public Styles getChildStyles(Component comp) {
        return compsAndStyles.get(comp);
    }

}

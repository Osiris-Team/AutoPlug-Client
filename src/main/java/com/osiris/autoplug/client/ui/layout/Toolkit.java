/*
 * Copyright (c) 2011-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;


/**
 * Base class for UI toolkit.
 *
 * @author Nathan Sweet
 */
public abstract class Toolkit<C, T extends C, L extends BaseTableLayout> {
	static public Toolkit instance;

	abstract public Cell obtainCell(L layout);

	abstract public void freeCell(Cell cell);

	abstract public void addChild(C parent, C child);

	abstract public void removeChild(C parent, C child);

	abstract public float getMinWidth(C widget);

	abstract public float getMinHeight(C widget);

	abstract public float getPrefWidth(C widget);

	abstract public float getPrefHeight(C widget);

	abstract public float getMaxWidth(C widget);

	abstract public float getMaxHeight(C widget);

	abstract public float getWidth(C widget);

	abstract public float getHeight(C widget);

	/**
	 * Clears all debugging rectangles.
	 */
	abstract public void clearDebugRectangles(L layout);

	/**
	 * Adds a rectangle that should be drawn for debugging.
	 */
	abstract public void addDebugRectangle(L layout, BaseTableLayout.Debug type, float x, float y, float w, float h);

	/**
	 * @param widget May be null.
	 */
	public void setWidget(L layout, Cell cell, C widget) {
		if (cell.widget == widget) return;
		removeChild((T) layout.table, (C) cell.widget);
		cell.widget = widget;
		if (widget != null) addChild((T) layout.table, widget);
	}

	/**
	 * Interprets the specified value as a size. This can be used to scale all sizes applied to a table. The default implementation
	 * returns the value unmodified.
	 *
	 * @see Value#width(Object)
	 * @see Value#width(Cell)
	 */
	public float width(float value) {
		return value;
	}

	/**
	 * Interprets the specified value as a size. This can be used to scale all sizes applied to a table. The default implementation
	 * returns the value unmodified.
	 *
	 * @see Value#height(Object)
	 * @see Value#height(Cell)
	 */
	public float height(float value) {
		return value;
	}
}

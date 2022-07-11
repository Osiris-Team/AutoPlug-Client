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
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

class SwingToolkit extends Toolkit<Component, Table, TableLayout> {
	static Timer timer;
	static ArrayList<TableLayout> debugLayouts = new ArrayList(0);

	static void startDebugTimer() {
		if (timer != null) return;
		timer = new Timer("TableLayout Debug", true);
		timer.schedule(newDebugTask(), 100);
	}

	static TimerTask newDebugTask() {
		return new TimerTask() {
			public void run() {
				if (!EventQueue.isDispatchThread()) {
					EventQueue.invokeLater(this);
					return;
				}
				for (TableLayout layout : debugLayouts)
					layout.drawDebug();
				timer.schedule(newDebugTask(), 250);
			}
		};
	}

	public Cell obtainCell(TableLayout layout) {
		Cell cell = new Cell();
		cell.setLayout(layout);
		return cell;
	}

	public void freeCell(Cell cell) {
	}

	public void addChild(Component parent, Component child) {
		if (parent instanceof JScrollPane)
			((JScrollPane) parent).setViewportView(child);
		else
			((Container) parent).add(child);
	}

	public void removeChild(Component parent, Component child) {
		((Container) parent).remove(child);
	}

	public float getMinWidth(Component widget) {
		return widget.getMinimumSize().width;
	}

	public float getMinHeight(Component widget) {
		return widget.getMinimumSize().height;
	}

	public float getPrefWidth(Component widget) {
		return widget.getPreferredSize().width;
	}

	public float getPrefHeight(Component widget) {
		return widget.getPreferredSize().height;
	}

	public float getMaxWidth(Component widget) {
		return widget.getMaximumSize().width;
	}

	public float getMaxHeight(Component widget) {
		return widget.getMaximumSize().height;
	}

	public float getWidth(Component widget) {
		return widget.getWidth();
	}

	public float getHeight(Component widget) {
		return widget.getHeight();
	}

	public void clearDebugRectangles(TableLayout layout) {
		if (layout.debugRects != null) debugLayouts.remove(layout);
		layout.debugRects = null;
	}

	public void addDebugRectangle(TableLayout layout, BaseTableLayout.Debug type, float x, float y, float w, float h) {
		if (layout.debugRects == null) {
			layout.debugRects = new ArrayList();
			debugLayouts.add(layout);
		}
		layout.debugRects.add(new DebugRect(type, x, y, w, h));
	}

	static class DebugRect {
		final BaseTableLayout.Debug type;
		final int x, y, width, height;

		public DebugRect(BaseTableLayout.Debug type, float x, float y, float width, float height) {
			this.x = (int) x;
			this.y = (int) y;
			this.width = (int) (width - 1);
			this.height = (int) (height - 1);
			this.type = type;
		}
	}
}

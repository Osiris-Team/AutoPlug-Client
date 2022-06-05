/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import java.awt.*;
import java.util.StringTokenizer;

/**
 * This class implements a combination of the GridBag and Border layouts. The
 * container is subdivided into rows and columns (basically a grid). Each cell
 * can be empty or can contain a component and a component can occupy one or more
 * cells. If a component is smaller than its cell, it can be aligned both horizontally
 * and vertically. The width of a column is the width of its largest component. The
 * height of a row is the height of its tallest component. Furthermore, a row or a
 * column can be expandable, that is its width/height changes depending on the container's
 * size.<BR><BR>
 * <B>General design issues:</B><BR>
 * First, decide the grid size. Consider that some components (like panels) usually
 * spread over more cells so it is a good idea to make the grid a little bigger.<BR><BR>
 * Second, decide which row/column expand (this is done by the setColProp/setRowProp
 * methods).<BR><BR>
 * Third, add components to container (layout) giving proper constraints.<BR><BR>
 * <B>Example:</B><BR><PRE>
 * FlexLayout flexL = new FlexLayout(2, 3, 4, 4); //--- (1)
 * flexL.setColProp(1, FlexLayout.EXPAND);        //--- (2)
 * flexL.setRowProp(2, FlexLayout.EXPAND);        //--- (3)
 * setLayout(flexL);                              //--- (4)
 * <p>
 * add("0,0",       new Label("Name"));         //--- (5)
 * add("0,1",       new Label("Surname"));      //--- (6)
 * add("1,0,x",     textField);                 //--- (7)
 * add("1,1,x",     textField);                 //--- (8)
 * add("0,2,x,x,2", panel);                     //--- (9)
 * </PRE><BR>
 * <I>Description:</I><BR>
 * line 1: creates a FlexLayout of 2 columns and 3 rows. The two 4 represent the gap
 * between each column and each row. To modify this gap use setXgap and setYgap.<BR>
 * line 2 & 3: column 1 and row 2 are set to expandable (the default is USEPREFERRED)
 * remember that row and column id starts from index 0.<BR>
 * line 4: the layout is added to the container.<BR>
 * line 5 & 6: add two labels in position (0,0) and (0,1)
 * line 7 & 8: add two textfields in position (1,0) and (1,1). The x means that the
 * width will be expanded to occupy all column size. Because column 1 is set to expandable,
 * when the container is resized the textfields are resized too.<BR>
 * line 9: adds a panel in position (0,2). Then panel is expanded both horizontally and
 * vertically and spreads over 2 cells in x direction.<BR><BR>
 * The complete command string is:<BR><PRE>
 * "x,y,xa,ya,xs,ys" where:
 * x,y specify the component position (are the column-row indexes)
 * xa specifies the x align of the component and can be:
 * l - for left
 * c - for center
 * r - for right
 * x - exands the component's width
 * (default is l)
 * ya specifies the y align of the component and can be:
 * t - for top
 * c - for center
 * b - for bottom
 * x - exands the component's height
 * (default is c)
 * xs and ys specify the number or columns/rows occupied by the component (default is 1)
 * </PRE>
 * <B>Notes:</B><BR>
 * - if a component occupies more than 1 cell in x direction, its preferred width is NOT
 * taken into account (same for y).<BR>
 * - if a column / row is empty, its size is set to a default value. This value can be
 * changed using setXNullgap/setYNullgap methods.
 * <BR><BR>
 * Have fun!!!
 *
 * @author Andrea Carboni
 */

public class FlexLayout implements LayoutManager {
    public static final int USEPREFERRED = 0; //--- default ---
    public static final int EXPAND = 1;
    private final int width;
    private final int height;
    private final FlexCell[][] cells;
    private final int[] xflags;
    private final int[] yflags;

    //--- flags for rows and columns ---
    private final int[] xpref;
    private final int[] ypref;
    private int xgap;
    private int ygap;
    private int xnullgap = 0; //24
    private int ynullgap = 0; //24
    public int paddingX = 10, paddingY = 10;

    //---------------------------------------------------------------------------

    public FlexLayout(int width, int height) {
        this(width, height, 4, 4);
    }

    //---------------------------------------------------------------------------

    public FlexLayout(int width, int height, int xgap, int ygap) {
        if (width < 1 || height < 1)
            throw new IllegalArgumentException("width & height must be >0");

        this.width = width;
        this.height = height;
        this.xgap = xgap;
        this.ygap = ygap;

        cells = new FlexCell[width][height];

        xflags = new int[width];
        yflags = new int[height];

        xpref = new int[width];
        ypref = new int[height];
    }

    //---------------------------------------------------------------------------

    public void setColProp(int index, int flag) {
        xflags[index] = flag;
    }

    //---------------------------------------------------------------------------

    public void setRowProp(int index, int flag) {
        yflags[index] = flag;
    }

    //---------------------------------------------------------------------------

    public void setNullGaps(int xgap, int ygap) {
        xnullgap = xgap;
        ynullgap = ygap;
    }

    //---------------------------------------------------------------------------

    public int getXgap() {
        return xgap;
    }

    public void setXgap(int xgap) {
        this.xgap = xgap;
    }

    public int getYgap() {
        return ygap;
    }

    public void setYgap(int ygap) {
        this.ygap = ygap;
    }

    public int getXNullgap() {
        return xnullgap;
    }

    public int getYNullgap() {
        return ynullgap;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    //---------------------------------------------------------------------------
    //---
    //--- LayoutManager Interface
    //---
    //---------------------------------------------------------------------------

    public void addLayoutComponent(String name, Component comp) {
        StringTokenizer strTk = new StringTokenizer(name, ",");

        int x = 1;
        int y = 1;
        int xalign = FlexCell.LEFT;
        int yalign = FlexCell.CENTERY;
        int xext = 1;
        int yext = 1;

        if (strTk.hasMoreTokens()) x = Integer.parseInt(strTk.nextToken());
        if (strTk.hasMoreTokens()) y = Integer.parseInt(strTk.nextToken());
        if (strTk.hasMoreTokens()) {
            String align = strTk.nextToken().toLowerCase();

            if (align.equals("l")) xalign = FlexCell.LEFT;
            if (align.equals("c")) xalign = FlexCell.CENTERX;
            if (align.equals("r")) xalign = FlexCell.RIGHT;
            if (align.equals("x")) xalign = FlexCell.EXPANDX;
        }
        if (strTk.hasMoreTokens()) {
            String align = strTk.nextToken().toLowerCase();

            if (align.equals("t")) yalign = FlexCell.TOP;
            if (align.equals("c")) yalign = FlexCell.CENTERY;
            if (align.equals("b")) yalign = FlexCell.BOTTOM;
            if (align.equals("x")) yalign = FlexCell.EXPANDY;
        }
        if (strTk.hasMoreTokens()) xext = Integer.parseInt(strTk.nextToken());
        if (strTk.hasMoreTokens()) yext = Integer.parseInt(strTk.nextToken());

        cells[x][y] = new FlexCell(xalign, yalign, xext, yext, comp);
    }

    //---------------------------------------------------------------------------

    public void removeLayoutComponent(Component comp) {
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                if (cells[x][y].component == comp) {
                    cells[x][y] = null;
                    return;
                }
    }

    //---------------------------------------------------------------------------

    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            calcMaxWidthArray();
            calcMaxHeightArray();

            int maxW = 0;
            int maxH = 0;

            for (int x = 0; x < width; x++) maxW += xpref[x];
            for (int y = 0; y < height; y++) maxH += ypref[y];

            Insets insets = parent.getInsets();

            return new Dimension(insets.left + insets.right + maxW + (width - 1) * xgap,
                    insets.top + insets.bottom + maxH + (height - 1) * ygap);
        }
    }

    //---------------------------------------------------------------------------

    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    //---------------------------------------------------------------------------

    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            FlexCell cell;

            //--- maximun preferred Width & Height calculus for each column ---

            calcMaxWidthArray();
            calcMaxHeightArray();

            //---------------------------------------------------------------------
            //--- Layout Calculus
            //---------------------------------------------------------------------

            Insets i = parent.getInsets();

            int maxWidth = parent.getSize().width - i.left - i.right - (width - 1) * xgap;
            int maxHeight = parent.getSize().height - i.top - i.bottom - (height - 1) * ygap;

            //--- total space occupied by fixed columns (and rows) ---
            int fixedWidth = 0;
            int fixedHeight = 0;

            //--- number of variable columns (and rows) ---
            int varXCount = 0;
            int varYCount = 0;

            //--- width of a variable column (or row) ---
            int varWidth = 0;
            int varHeight = 0;

            //---------------------------------------------------------------------
            //---   Handle Columns
            //---------------------------------------------------------------------

            //--- calc totale space occupied by fixed columns ---

            for (int x = 0; x < width; x++) {
                if (xflags[x] == USEPREFERRED) fixedWidth += xpref[x];
                else varXCount++;
            }

            //--- calc width of a generic variable column ---

            if (varXCount != 0) {
                varWidth = (maxWidth - fixedWidth) / varXCount;
                if (varWidth < 0) varWidth = 0;
            }

            //--- calc width for expandable columns ---

            for (int x = 0; x < width; x++) if (xflags[x] == EXPAND) xpref[x] = varWidth;

            //---------------------------------------------------------------------
            //---   Handle Rows
            //---------------------------------------------------------------------

            //--- calc totale space occupied by fixed rows ---

            for (int y = 0; y < height; y++) {
                if (yflags[y] == USEPREFERRED) fixedHeight += ypref[y];
                else varYCount++;
            }

            //--- calc height of a generic variable row ---

            if (varYCount != 0) {
                varHeight = (maxHeight - fixedHeight) / varYCount;
                if (varHeight < 0) varHeight = 0;
            }

            //--- calc width for expandable rows ---

            for (int y = 0; y < height; y++) if (yflags[y] == EXPAND) ypref[y] = varHeight;

            //---------------------------------------------------------------------
            //---   Do Layout
            //---------------------------------------------------------------------

            int currentX = i.left;
            int currentY = i.top;

            int compX, compY, compW, compH;
            int cellW, cellH;

            int xc, yc;

            for (int y = 0; y < height; y++) {
                currentX = i.left;

                for (int x = 0; x < width; x++) {
                    //--- calculate current cell width and height ---

                    cellW = 0;
                    cellH = 0;

                    cell = cells[x][y];

                    if (cell != null) {
                        for (xc = x; xc < x + cell.xext; xc++) cellW += xpref[xc];
                        for (yc = y; yc < y + cell.yext; yc++) cellH += ypref[yc];

                        Dimension compSize = cell.component.getPreferredSize();

                        //------------------------------------------------------------
                        //--- calculate compX & compW depending on align
                        //------------------------------------------------------------

                        switch (cell.xalign) {
                            case FlexCell.LEFT:
                                compX = currentX;
                                compW = compSize.width;
                                break;

                            case FlexCell.CENTERX:
                                compX = currentX + (cellW - compSize.width) / 2;
                                compW = compSize.width;
                                break;

                            case FlexCell.RIGHT:
                                compX = currentX + cellW - compSize.width;
                                compW = compSize.width;
                                break;

                            case FlexCell.EXPANDX:
                                compX = currentX;
                                compW = cellW;
                                break;

                            default:
                                System.out.println("FlexLayout: invalid X align type");
                                compX = currentX;
                                compW = cellW;
                                break;
                        }

                        //------------------------------------------------------------
                        //--- calculate compY & compH depending on align
                        //------------------------------------------------------------

                        switch (cell.yalign) {
                            case FlexCell.TOP:
                                compY = currentY;
                                compH = compSize.height;
                                break;

                            case FlexCell.CENTERY:
                                compY = currentY + (cellH - compSize.height) / 2;
                                compH = compSize.height;
                                break;

                            case FlexCell.BOTTOM:
                                compY = currentY + cellH - compSize.height;
                                compH = compSize.height;
                                break;

                            case FlexCell.EXPANDY:
                                compY = currentY;
                                compH = cellH;
                                break;

                            default:
                                System.out.println("FlexLayout: invalid Y align type");
                                compY = currentY;
                                compH = cellH;
                                break;
                        }

                        //--- resize component ---

                        //cell.component.setBounds(compX + paddingX, compY + paddingY,
                        cell.component.setBounds(compX, compY,
                                compW + (cell.xext - 1) * xgap,
                                compH + (cell.yext - 1) * ygap
                        );
                    }
                    currentX += xpref[x] + xgap;
                }

                currentY += ypref[y] + ygap;
            }
        }
    }

    //---------------------------------------------------------------------------

    private void calcMaxWidthArray() {
        //--- maximun preferred Width calculus for each column ---

        FlexCell cell;

        int maxPrefW, curPrefW;

        for (int x = 0; x < width; x++) {
            maxPrefW = 0;

            for (int y = 0; y < height; y++) {
                cell = cells[x][y];
                if (cell != null && cell.xext == 1) {
                    curPrefW = cell.component.getPreferredSize().width;
                    if (curPrefW > maxPrefW) maxPrefW = curPrefW;
                }
            }
            if (maxPrefW == 0) maxPrefW = xnullgap;

            xpref[x] = maxPrefW;
        }
    }

    //---------------------------------------------------------------------------

    private void calcMaxHeightArray() {
        //--- maximun preferred Height calculus for each row ---

        FlexCell cell;

        int maxPrefH, curPrefH;

        for (int y = 0; y < height; y++) {
            maxPrefH = 0;

            for (int x = 0; x < width; x++) {
                cell = cells[x][y];
                if (cell != null && cell.yext == 1) {
                    curPrefH = cell.component.getPreferredSize().height;
                    if (curPrefH > maxPrefH) maxPrefH = curPrefH;
                }
            }
            if (maxPrefH == 0) maxPrefH = ynullgap;

            ypref[y] = maxPrefH;
        }
    }
}

class FlexCell {
    //--- X align constants ---

    public static final int LEFT = 0;
    public static final int CENTERX = 1;
    public static final int RIGHT = 2;
    public static final int EXPANDX = 3;

    //--- Y align constants ---

    public static final int TOP = 0;
    public static final int CENTERY = 1;
    public static final int BOTTOM = 2;
    public static final int EXPANDY = 3;

    public int xalign, xext;
    public int yalign, yext;

    public Component component;

    //---------------------------------------------------------------------------

    public FlexCell(int xalign, int yalign, int xext, int yext, Component c) {
        this.xalign = xalign;
        this.yalign = yalign;
        this.xext = xext;
        this.yext = yext;

        component = c;
    }
}




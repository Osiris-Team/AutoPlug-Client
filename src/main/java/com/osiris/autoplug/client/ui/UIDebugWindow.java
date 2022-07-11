/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.ui.layout.BaseTableLayout;
import com.osiris.autoplug.client.ui.layout.VL;
import com.osiris.autoplug.client.ui.utils.CompWrapper;
import com.osiris.autoplug.core.logger.AL;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Objects;

public class UIDebugWindow extends JFrame {
    public UIDebugWindow() {
        try {
            GeneralConfig generalConfig = new GeneralConfig();
            if (generalConfig.autoplug_system_tray_theme.asString().equals("light")) {
                if (!FlatLightLaf.setup()) throw new Exception("Returned false!");
            } else if (generalConfig.autoplug_system_tray_theme.asString().equals("dark")) {
                if (!FlatDarkLaf.setup()) throw new Exception("Returned false!");
            } else if (generalConfig.autoplug_system_tray_theme.asString().equals("darcula")) {
                if (!FlatDarculaLaf.setup()) throw new Exception("Returned false!");
            } else {
                AL.warn("The selected theme '" + generalConfig.autoplug_system_tray_theme.asString() + "' is not a valid option! Using default.");
                if (!FlatLightLaf.setup()) throw new Exception("Returned false!");
            }
        } catch (Exception e) {
            AL.warn("Failed to init GUI light theme!", e);
        }
        this.setName("AutoPlug-Tray/UI-Debug");
        this.setTitle("AutoPlug-Tray/UI-Debug");
        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width, screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        int width = (screenWidth / 2), height = screenHeight / 2;
        this.setLocation((screenWidth / 2) - (width / 2), (screenHeight / 2) - (height / 2)); // Position frame in mid of screen
        this.setSize(width, height);
        this.setVisible(true);
        this.setLayout(new BoxLayout(this.getContentPane(), BoxLayout.Y_AXIS));
        loadDataFromMainWindow();
    }

    private void fillTreeNodes(DefaultMutableTreeNode parentNode, CompWrapper comp) {
        DefaultMutableTreeNode currentNode = new DefaultMutableTreeNode(comp);
        parentNode.add(currentNode);
        if (comp.component instanceof Container) { // is container?
            Container container = (Container) comp.component;
            for (Component childComp : container.getComponents()) {
                fillTreeNodes(currentNode, new CompWrapper(childComp));
            }
        }
    }

    private void loadDataFromMainWindow() {
        Objects.requireNonNull(MainWindow.GET);
        JSplitPane splitPane = new JSplitPane();
        splitPane.setResizeWeight(0.7);
        this.add(splitPane);

        JTree tree = new JTree();
        splitPane.setLeftComponent(tree);
        tree.setShowsRootHandles(true);
        tree.setEditable(false);

        VL lyRight = new VL(splitPane);
        splitPane.setRightComponent(lyRight);
        lyRight.addV(new JLabel("Double-click an item on the left, to display its details here."))
                .center();

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Inspecting contents of " + MainWindow.class.getSimpleName());
        fillTreeNodes(rootNode, new CompWrapper(MainWindow.GET.getRootPane()));

        DefaultTreeModel model = new DefaultTreeModel(rootNode);
        tree.setModel(model);

        final Component[] beforeComponent = {null};
        final Color[] beforeColor = {null};
        tree.addTreeSelectionListener(e -> {
            // Returns the last path element of the selection.
            // This method is useful only when the selection model allows a single selection.
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                    tree.getLastSelectedPathComponent();

            if (node == null) {
                // Expand all when root node is selected
                // TODO test this
                for (int i = 0; i < tree.getRowCount(); i++) {
                    tree.expandRow(i);
                }
                return; // Nothing is selected.
            }
            if (beforeComponent[0] != null) { // Restore before component
                beforeComponent[0].setBackground(beforeColor[0]);
                beforeComponent[0].revalidate();
                beforeComponent[0].repaint();
                if (beforeComponent[0] instanceof VL)
                    ((VL) beforeComponent[0]).debug(BaseTableLayout.Debug.none);
            }

            CompWrapper comp = (CompWrapper) node.getUserObject();
            Color oldBackgroundColor = comp.component.getBackground();
            comp.component.setBackground(new Color(33, 211, 255, 117)); // Mark component, with blueish color

            if (comp.component instanceof VL)
                ((VL) comp.component).debug();

            lyRight.clear();
            JLabel title = new JLabel(comp.toString());
            title.putClientProperty("FlatLaf.style", "font: 100% $semibold.font");
            lyRight.addV(title).fill();

            JTextField txtFullClassName = new JTextField(comp.getClass().getName());
            txtFullClassName.setToolTipText("The full class name/path for this object.");
            txtFullClassName.setEnabled(false);
            lyRight.addV(txtFullClassName).fill();

            // EXTENDING/SUPER CLASSES
            Class<?> clazz = comp.component.getClass().getSuperclass();
            String extendingClasses = clazz.getSimpleName() + " -> ";
            while (!clazz.equals(Object.class)) {
                clazz = clazz.getSuperclass();
                extendingClasses += clazz.getSimpleName() + " -> ";
            }
            extendingClasses += "END";
            JTextField txtExtendingClasses = new JTextField(extendingClasses);
            txtExtendingClasses.setToolTipText("The super-classes names for this object.");
            txtExtendingClasses.setEnabled(false);
            lyRight.addV(txtExtendingClasses).fill();

            // INTERFACES
            Class<?>[] interfaces = comp.component.getClass().getInterfaces();
            String implementingInterfaces = "";
            for (Class<?> i : interfaces) {
                implementingInterfaces += i.getSimpleName() + " ";
            }
            JTextField txtImplInterfaces = new JTextField(implementingInterfaces);
            txtImplInterfaces.setToolTipText("The implemented interfaces names for this object.");
            txtImplInterfaces.setEnabled(false);
            lyRight.addV(txtImplInterfaces).fill();

            // DIMENSIONS
            JTextField txtDimensions = new JTextField(comp.component.getWidth() + " x " + comp.component.getHeight() + " pixels");
            txtDimensions.setToolTipText("The width x height for this object.");
            txtDimensions.setEnabled(false);
            lyRight.addV(txtDimensions).fill();

            // LOCATION
            JTextField txtLocation = new JTextField("x: " + comp.component.getLocation().x + " y: " + comp.component.getLocation().y + " pixels");
            txtLocation.setToolTipText("The location for this object.");
            txtLocation.setEnabled(false);
            lyRight.addV(txtLocation).fill();

            lyRight.updateUI(); // to avoid UI bug where leftover UI from before was being shown
            comp.component.revalidate();
            comp.component.repaint();
            beforeComponent[0] = comp.component;
            beforeColor[0] = oldBackgroundColor;
        });

        // Expand all
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}

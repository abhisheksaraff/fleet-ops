package com.fleetops.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * One side of a split pane that can be dragged to any size, collapsed to nothing and brought back
 * to the size it had before. Double-clicking the divider toggles it.
 */
final class SplitRegion {

    /** Which side of the split the region is on. */
    enum Side { FIRST, SECOND }

    private static final int REMEMBER_FROM = 80;

    private final JSplitPane split;
    private final Side side;
    private final boolean vertical;
    private int expanded;
    private int pending = -1;
    private Runnable onChange = () -> {};

    SplitRegion(JSplitPane split, Side side, Component content, int defaultExpanded) {
        this.split = split;
        this.side = side;
        this.vertical = split.getOrientation() == JSplitPane.VERTICAL_SPLIT;
        this.expanded = defaultExpanded;

        content.setMinimumSize(new Dimension(0, 0));
        split.setResizeWeight(side == Side.FIRST ? 0.0 : 1.0);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setOneTouchExpandable(false);

        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            int size = size();
            if (size >= REMEMBER_FROM) {
                expanded = size;
            }
            onChange.run();
        });
        split.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (pending >= 0 && total() > 0) {
                    int wanted = pending;
                    pending = -1;
                    setSize(wanted);
                }
            }
        });
        if (split.getUI() instanceof BasicSplitPaneUI ui && ui.getDivider() != null) {
            ui.getDivider().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        toggle();
                    }
                }
            });
        }
    }

    void onChange(Runnable listener) {
        this.onChange = listener;
    }

    boolean isOpen() {
        return pending >= 0 ? pending > 0 : size() > 4;
    }

    void open() {
        setSize(expanded);
    }

    void close() {
        setSize(0);
    }

    void toggle() {
        if (isOpen()) {
            close();
        } else {
            open();
        }
    }

    /** The size the region comes back to when it is opened. */
    int expandedSize() {
        return expanded;
    }

    void setExpandedSize(int pixels) {
        if (pixels >= REMEMBER_FROM) {
            expanded = pixels;
        }
    }

    /** Sets the region's width (or height); shown at once if the window is laid out, else on first layout. */
    void setSize(int pixels) {
        int total = total();
        if (total <= 0) {
            pending = Math.max(0, pixels);
            onChange.run();
            return;
        }
        int clamped = Math.max(0, Math.min(pixels, total - split.getDividerSize()));
        int location = side == Side.FIRST ? clamped : total - clamped - split.getDividerSize();
        split.setDividerLocation(location);
    }

    private int total() {
        return vertical ? split.getHeight() : split.getWidth();
    }

    private int size() {
        int location = split.getDividerLocation();
        return side == Side.FIRST ? location : total() - location - split.getDividerSize();
    }
}

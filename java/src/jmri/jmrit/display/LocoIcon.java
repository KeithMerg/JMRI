package jmri.jmrit.display;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import jmri.InstanceManager;
import jmri.jmrit.catalog.NamedIcon;
import jmri.jmrit.logix.TrackerTableAction;
import jmri.jmrit.roster.RosterEntry;
import jmri.jmrit.throttle.ThrottleFrameManager;
import jmri.jmrit.throttle.interfaces.ThrottleControllerUI;
import jmri.util.swing.JmriMouseEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An icon that displays the position of a loco on a panel.<p>
 * The icon can always be repositioned and its popup menu is always active.
 *
 * @author Bob Jacobsen Copyright (c) 2002
 * @author Daniel Boudreau Copyright (C) 2008, 2010
 */
public class LocoIcon extends PositionableLabel {

    public static final String WHITE = Bundle.getMessage("White");  //loco background colors
    public static final String GREEN = Bundle.getMessage("Green");
    public static final String GRAY = Bundle.getMessage("Gray");
    public static final String RED = Bundle.getMessage("Red");
    public static final String BLUE = Bundle.getMessage("Blue");
    public static final String YELLOW = Bundle.getMessage("Yellow");

    public static final Color COLOR_BLUE = new Color(40, 140, 255);

    private int _dockX = 0;
    private int _dockY = 0;
    private Color _locoColor;

    public LocoIcon(Editor editor) {
        // super ctor call to make sure this is an icon label
        super(new NamedIcon("resources/icons/markers/loco-white.gif",
                "resources/icons/markers/loco-white.gif"), editor);
        _locoColor = Color.WHITE;
        setDisplayLevel(Editor.MARKERS);
        setShowToolTip(false);
        //setEditable(false);
        _text = true; //Markers are an icon with text
        setPopupUtility(new PositionablePopupUtil(this, this) {       // need this class for Font Edit
            @Override
            public void setFixedTextMenu(JPopupMenu popup) {
            }

            @Override
            public void setTextMarginMenu(JPopupMenu popup) {
            }

            @Override
            public void setTextBorderMenu(JPopupMenu popup) {
            }

            @Override
            public void setTextJustificationMenu(JPopupMenu popup) {
            }
        });
    }

    @Override
    public Positionable deepClone() {
        LocoIcon pos = new LocoIcon(_editor);
        return finishClone(pos);
    }

    protected Positionable finishClone(LocoIcon pos) {
        if (_entry != null) {
            pos.setRosterEntry(getRosterEntry());
        }
        pos.setText(getText());
        return super.finishClone(pos);
    }

    // Marker tool tips are always disabled
    @Override
    public void setShowToolTip(boolean set) {
        super.setShowToolTip(false);
    }

    // Markers are always positionable
    @Override
    public void setPositionable(boolean enabled) {
        super.setPositionable(true);
    }

    // Markers always have a popup menu
    @Override
    public boolean doViemMenu() {
        return false;
    }

    ThrottleControllerUI tf = null;

    /**
     * Pop-up only if right click and not dragged
     */
    @Override
    public boolean showPopUp(JPopupMenu popup) {
        if (_editor.isLocoMarkerPopupDisabled()) {
            return false;
        }
        if (_entry != null) {
            popup.add(new AbstractAction("Throttle") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    tf = InstanceManager.getDefault(ThrottleFrameManager.class).createThrottleFrame();
                    tf.setRosterEntry(_entry);
                    tf.toFront();
                }
            });
        }
        popup.add(makeLocoIconMenu());
        if (isEditable()) {
            getEditor().setShowAlignmentMenu(this, popup);
            getEditor().setShowCoordinatesMenu(this, popup);
            popup.add(makeDockingMenu());
            popup.add(makeDockMenu());
            getPopupUtility().setTextFontMenu(popup);
        } else {
            setRotateMenu(popup);
            if (_entry == null) {
                setTextEditMenu(popup);
            }
            popup.add(makeDockMenu());
            getPopupUtility().setTextFontMenu(popup);
            getEditor().setRemoveMenu(this, popup);
        }
        return true;
    }

    ButtonGroup locoButtonGroup = null;

    protected JMenu makeLocoIconMenu() {
        JMenu iconMenu = new JMenu(Bundle.getMessage("LocoColor"));
        locoButtonGroup = new ButtonGroup();
        String[] colors = getLocoColors();
        for (String color : colors) {
            addLocoMenuEntry(iconMenu, color);
        }
        return iconMenu;
    }

    // loco icons
    NamedIcon white = new NamedIcon("resources/icons/markers/loco-white.gif",
            "resources/icons/markers/loco-white.gif");
    NamedIcon green = new NamedIcon("resources/icons/markers/loco-green.gif",
            "resources/icons/markers/loco-green.gif");
    NamedIcon gray = new NamedIcon("resources/icons/markers/loco-gray.gif",
            "resources/icons/markers/loco-gray.gif");
    NamedIcon red = new NamedIcon("resources/icons/markers/loco-red.gif",
            "resources/icons/markers/loco-red.gif");
    NamedIcon blue = new NamedIcon("resources/icons/markers/loco-blue.gif",
            "resources/icons/markers/loco-blue.gif");
    NamedIcon yellow = new NamedIcon("resources/icons/markers/loco-yellow.gif",
            "resources/icons/markers/loco-yellow.gif");

    public void addLocoMenuEntry(JMenu iconMenu, final String color) {
        JRadioButtonMenuItem r = new JRadioButtonMenuItem(color);
        r.addActionListener(new ActionListener() {
            final String desiredColor = color;

            @Override
            public void actionPerformed(ActionEvent e) {
                setLocoColor(desiredColor);
            }
        });
        locoButtonGroup.add(r);
        iconMenu.add(r);
    }

    public void setLocoColor(String color) {
        log.debug("Set loco color to {}", color);
        if (color.equals(WHITE)) {
            super.updateIcon(white);
            _locoColor = Color.WHITE;
            setForeground(Color.black);
        }
        if (color.equals(GREEN)) {
            super.updateIcon(green);
            _locoColor = Color.GREEN;
            setForeground(Color.black);
        }
        if (color.equals(GRAY)) {
            super.updateIcon(gray);
            _locoColor = Color.GRAY;
            setForeground(Color.white);
        }
        if (color.equals(RED)) {
            super.updateIcon(red);
            _locoColor = Color.RED;
            setForeground(Color.white);
        }
        if (color.equals(BLUE)) {
            super.updateIcon(blue);
            _locoColor = COLOR_BLUE;
            setForeground(Color.white);
        }
        if (color.equals(YELLOW)) {
            super.updateIcon(yellow);
            _locoColor = Color.YELLOW;
            setForeground(Color.black);
        }
    }

    public static String[] getLocoColors() {
        return new String[]{WHITE, GREEN, GRAY, RED, BLUE, YELLOW};
    }

    public Color getLocoColor() {
        return _locoColor;
    }

    protected RosterEntry _entry = null;

    public void setRosterEntry(RosterEntry entry) {
        _entry = entry;
    }

    public RosterEntry getRosterEntry() {
        return _entry;
    }

    protected JMenuItem makeDockingMenu() {
        JMenuItem dockingMenu = new JMenuItem(Bundle.getMessage("setDockingLocation"));
        dockingMenu.addActionListener(new ActionListener() {
            Editor ed;
            LocoIcon loco;

            ActionListener init(Editor e, LocoIcon l) {
                ed = e;
                loco = l;
                return this;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                ed.setSelectionsDockingLocation(loco);
            }
        }.init(getEditor(), this));
        return dockingMenu;
    }

    public void setDockingLocation(int x, int y) {
        _dockX = x;
        _dockY = y;
    }

    public int getDockX() {
        return _dockX;
    }

    public int getDockY() {
        return _dockY;
    }

    public void dock() {
        setLocation(_dockX, _dockY);
    }

    protected JMenuItem makeDockMenu() {
        JMenuItem dockMenu = new JMenuItem(Bundle.getMessage("dockIcon"));
        dockMenu.addActionListener(new ActionListener() {
            Editor ed;
            LocoIcon loco;

            ActionListener init(Editor e, LocoIcon l) {
                ed = e;
                loco = l;
                return this;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                ed.dockSelections(loco);
            }
        }.init(getEditor(), this));
        return dockMenu;
    }

    /**
     * Called at load time to get "background" color
     */
    public void init() {
        NamedIcon icon = (NamedIcon) getIcon();
        String name = icon.getURL();
        if (name != null) {
            if (name.endsWith("loco-white.gif")) {
                _locoColor = Color.WHITE;
            } else if (name.endsWith("loco-green.gif")) {
                _locoColor = Color.GREEN;
            } else if (name.endsWith("loco-gray.gif")) {
                _locoColor = Color.GRAY;
            } else if (name.endsWith("loco-red.gif")) {
                _locoColor = Color.RED;
            } else if (name.endsWith("loco-blue.gif")) {
                _locoColor = COLOR_BLUE;
            } else if (name.endsWith("loco-yellow.gif")) {
                _locoColor = Color.YELLOW;
            }
        }
    }

    /**
     * Dropped on an OBlock's track: start a Tracker for the marker's train
     * there, or, with a {@link TrackMarkerDropHandler} active, let it take
     * the drop first.
     */
    @Override
    public void doMouseReleased(JmriMouseEvent event) {
        List<Positionable> selections = _editor.getSelectedItems(event);
        for (Positionable selection : selections) {
            if (selection instanceof IndicatorTrack) {
                IndicatorTrack t = (IndicatorTrack) selection;
                jmri.jmrit.logix.OBlock block = t.getOccBlock();
                if (block != null) {
                    for (TrackMarkerDropHandler handler : java.util.ServiceLoader.load(TrackMarkerDropHandler.class)) {
                        if (handler.isActive()) {
                            // the handler may put a train there, detected a moment later
                            handler.markerDropped(this, block, _editor.getTargetPanel(),
                                    TrackMarkerDropHandler.Usual.TRACKER,
                                    () -> whenOccupied(block, () -> startTracker(block)));
                            return;
                        }
                    }
                    startTracker(block);
                }
                break;
            }
        }
    }

    /**
     * Set display attributes for Tracker, and start one for this marker's
     * train in an OBlock.
     *
     * @param block the OBlock
     */
    private void startTracker(@javax.annotation.Nonnull jmri.jmrit.logix.OBlock block) {
        block.setMarkerForeground(getForeground());
        block.setMarkerBackground(_locoColor);
        PositionablePopupUtil util = getPopupUtility();
        block.setMarkerFont(util.getFont());
        String name = getText(); // rotated icons have null text
        if (name == null || name.length() == 0) {
            name = getUnRotatedText();
        }
        InstanceManager.getDefault(TrackerTableAction.class).markNewTracker(block, name, this);
        dock();
    }

    /** How long to wait for a Block a train was put in to show occupied, ms. */
    static final int OCCUPIED_WAIT_MS = 2000;

    /**
     * Run something once a Block shows occupied, or after
     * {@link #OCCUPIED_WAIT_MS} if it doesn't. A drop handler may put a train
     * in the Block (a simulated one is detected a moment later), and a Tracker
     * can't be started in an empty OBlock. An OBlock's state has other bits
     * too, so only the occupied bit is looked at.
     *
     * @param block the Block the train was placed in
     * @param then  what to run
     */
    static void whenOccupied(@javax.annotation.Nonnull jmri.Block block, @javax.annotation.Nonnull Runnable then) {
        if ((block.getState() & jmri.Block.OCCUPIED) != 0) {
            then.run();
            return;
        }
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        javax.swing.Timer timeout = new javax.swing.Timer(OCCUPIED_WAIT_MS, null);
        java.beans.PropertyChangeListener occupied = new java.beans.PropertyChangeListener() {
            @Override
            public void propertyChange(java.beans.PropertyChangeEvent e) {
                if ((block.getState() & jmri.Block.OCCUPIED) != 0) {
                    finish();
                }
            }

            void finish() {
                block.removePropertyChangeListener(this);
                timeout.stop();
                if (done.compareAndSet(false, true)) {
                    // block state changes come on the layout thread (Swing thread)
                    jmri.util.ThreadingUtil.runOnGUIEventually(then::run);
                }
            }
        };
        timeout.addActionListener(e -> {
            block.removePropertyChangeListener(occupied);
            if (done.compareAndSet(false, true)) {
                then.run();
            }
        });
        timeout.setRepeats(false);
        block.addPropertyChangeListener(occupied);
        timeout.start();
        if ((block.getState() & jmri.Block.OCCUPIED) != 0) {   // in case it became occupied while listening started
            occupied.propertyChange(null);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(LocoIcon.class);
}

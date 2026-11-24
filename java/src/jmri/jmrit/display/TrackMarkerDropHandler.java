package jmri.jmrit.display;

import java.awt.Component;

import javax.annotation.Nonnull;
import javax.swing.JPopupMenu;

import jmri.Block;
import jmri.InvokeOnGuiThread;

/**
 * Something that takes a loco marker dropped on a Block's track in operating
 * mode, on a Layout Editor panel or on a Control Panel Editor panel's OBlock
 * track, before the drop does what it does without one.
 * <p>
 * Implementations are found with {@link java.util.ServiceLoader}
 * ({@code @ServiceProvider(service = TrackMarkerDropHandler.class)}), so the
 * panel editors need to know nothing of them. With no handler
 * {@link #isActive() active}, a dropped marker does only what it always has.
 *
 * @author Keith Bruce Copyright (C) 2026
 * @since 5.19.2
 */
public interface TrackMarkerDropHandler {

    /**
     * What a dropped marker does with no handler active.
     */
    enum Usual {
        /** On a Layout Editor panel: train tracking is told the marker's train is in the Block. */
        TRAIN_TRACKING,
        /** On a Control Panel Editor panel: a Tracker is started for the marker's train in the OBlock. */
        TRACKER
    }

    /**
     * @return true if this handler takes dropped markers now
     */
    @InvokeOnGuiThread
    boolean isActive();

    /**
     * A loco marker was dropped on the track of a Block. Called only while
     * this handler is {@link #isActive() active}.
     *
     * @param marker  the marker
     * @param block   the Block of the track it was dropped on
     * @param parent  the panel, for the handler's own windows
     * @param usual   what the drop does with no handler
     * @param doUsual run this to do it as well; not run, it isn't done
     */
    @InvokeOnGuiThread
    void markerDropped(@Nonnull LocoIcon marker, @Nonnull Block block, @Nonnull Component parent,
            @Nonnull Usual usual, @Nonnull Runnable doUsual);

    /**
     * Add this handler's items for a Block to the menu the Block's track
     * shows in operating mode. Called only while this handler is
     * {@link #isActive() active}.
     *
     * @param popup the menu
     * @param block the Block
     */
    @InvokeOnGuiThread
    void addBlockMenuItems(@Nonnull JPopupMenu popup, @Nonnull Block block);
}

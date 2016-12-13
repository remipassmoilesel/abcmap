package org.abcmap.core.managers;

import org.abcmap.core.events.ProjectEvent;
import org.abcmap.core.log.CustomLogger;
import org.abcmap.core.events.manager.*;
import org.abcmap.gui.*;
import org.abcmap.gui.components.dock.Dock;
import org.abcmap.gui.components.map.CachedMapPane;
import org.abcmap.gui.utils.GuiUtils;
import org.abcmap.gui.windows.DetachedWindow;
import org.abcmap.gui.windows.MainWindow;
import org.abcmap.gui.windows.MainWindowMode;
import org.abcmap.gui.windows.crop.CropConfigurationWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * Manage all operations around GUI. Some can be delegated to others managers like DialogManager.
 */
public class GuiManager implements HasEventNotificationManager {

    private static final CustomLogger logger = LogManager.getLogger(GuiManager.class);

    /**
     * Sub manager used to manage all operations around dialogs.
     */
    private final DialogManager dialogManager;

    /**
     * List of operations to launch AFTER gui initialization
     */
    private ArrayList<Runnable> initialisationOperations;

    /**
     * List of all windows of program
     */
    private HashMap<Windows, JFrame> registeredWindows;

    private ProjectManager projectm;
    private EventNotificationManager notifm;
    private Window wizardDetachedWindow;
    private ArrayList<CachedMapPane> mapPanes;

    public GuiManager() {

        this.projectm = MainManager.getProjectManager();

        this.notifm = new EventNotificationManager(this);
        notifm.setDefaultListener(new GuiUpdater());

        // listen project manager events
        projectm.getNotificationManager().addObserver(this);

        initialisationOperations = new ArrayList<>();
        registeredWindows = new HashMap<>();

        dialogManager = new DialogManager();
    }

    public DetachedWindow getWizardDetachedWindow() {
        return (DetachedWindow) registeredWindows.get(Windows.DETACHED_WIZARD);
    }

    /**
     * Update GUI regarding to various events
     */
    private class GuiUpdater implements EventListener {

        @Override
        public void notificationReceived(org.abcmap.core.events.manager.Event arg) {

            // rename main window when project change
            if (ProjectEvent.isNewProjectLoadedEvent(arg)) {
                String name = projectm.getProject().getFinalPath().getFileName().toString();
                getMainWindow().setTitle(name);
            }

        }
    }

    /**
     * Return dialog manager, a utility used to display dialogs and messages to user.
     *
     * @return
     */
    public DialogManager getDialogManager() {
        return dialogManager;
    }

    /**
     * Register a window in GUI manager. All windows of software have to be registered here because program can have to hide windows
     * in order to cacth screen
     *
     * @param name
     * @param w
     */
    public void registerWindow(Windows name, JFrame w) {
        registeredWindows.put(name, w);
    }

    /**
     * Get manual import window
     *
     * @return
     */
    public Window getManualImportWindow() {
        return registeredWindows.get(Windows.MANUAL_IMPORT);
    }

    /**
     * Get robot import window
     *
     * @return
     */
    public Window getAutoImportWindow() {
        return registeredWindows.get(Windows.ROBOT_IMPORT);
    }

    /**
     * Return all windows of software. Some may be null.
     *
     * @return
     */
    public Collection<JFrame> getAllWindows() {
        return registeredWindows.values();
    }

    /**
     * Add an operation which will be run AFTER gui initialization, when all windows will be set up.
     * <p>
     * All operations will be run on Event Dispatch Thread of Swing
     *
     * @param operation
     */
    public void addInitialisationOperation(Runnable operation) {
        initialisationOperations.add(operation);
    }

    /**
     * Execute init operations on EDT
     */
    private void runIntialisationOperations() {

        GuiUtils.throwIfNotOnEDT();

        for (Runnable runnable : initialisationOperations) {
            runnable.run();
        }

    }

    /**
     * Build GUI of software
     */
    public void constructGui() {

        GuiUtils.throwIfNotOnEDT();

        GuiBuilder gb = new GuiBuilder();
        gb.constructGui();

        gb.registerWindows();

        getMainWindow().setWindowMode(MainWindowMode.SHOW_MAP);

    }

    /**
     * Construct and show software GUI on current Thread
     *
     * @throws InvocationTargetException
     * @throws InterruptedException
     */
    public void constructAndShowGui() throws Exception {

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                constructGui();
                getMainWindow().setVisible(true);
                runIntialisationOperations();
            }
        });

    }


    /**
     * Configure GUI look and feel
     */
    public void configureUiManager() {
        GuiUtils.configureUIManager(UIManager.getSystemLookAndFeelClassName());
        GuiUtils.setDefaultUIFont(GuiStyle.DEFAULT_SOFTWARE_FONT);
    }

    /**
     * Hide all windows on this thread
     *
     * @param val
     * @throws InvocationTargetException
     * @throws InterruptedException
     */
    public void setAllWindowVisibles(final boolean val) {

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    for (final Window w : getAllWindows()) {
                        if (w != null) {
                            w.setVisible(val);
                        }
                    }
                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            // should never happen
            logger.error(e);
        }

    }

    /**
     * Show specified window and hide all others
     *
     * @param winToShow
     */
    public void showOnlyWindow(Window winToShow) {

        GuiUtils.throwIfNotOnEDT();

        Collection<JFrame> allWindows = getAllWindows();

        if (allWindows.contains(winToShow) == false) {
            throw new IllegalArgumentException("Unknown window: " + winToShow.getClass().getName() + " " + winToShow);
        }

        // hide all windows
        for (Window w : allWindows) {
            if (w.isVisible() == false) {
                continue;
            }
            if (winToShow.equals(w) == false && getWizardDetachedWindow().equals(w) == false) {
                w.setVisible(false);
            }
        }

        // show specified window if necessary
        if (winToShow.isVisible() == false) {
            winToShow.setVisible(true);
        }

    }

    /**
     * Get main window of software
     *
     * @return
     */
    public MainWindow getMainWindow() {
        return (MainWindow) registeredWindows.get(Windows.MAIN);
    }

    /**
     * Return list of all visible windows of software
     *
     * @return
     */
    public ArrayList<Component> getVisibleWindows() {

        Collection<JFrame> toCheck = getAllWindows();
        ArrayList<Component> output = new ArrayList<Component>();

        for (Component comp : toCheck) {
            if (comp != null && comp.isVisible()) {
                output.add(comp);
            }
        }

        return output;
    }

    /**
     * Predefined cursor
     *
     * @return
     */
    public Cursor getDrawingCursor() {
        return GuiCursor.CROSS_CURSOR;
    }

    /**
     * Predefined cursor
     *
     * @return
     */
    public Cursor getNormalCursor() {
        return GuiCursor.NORMAL_CURSOR;
    }

    /**
     * Predefined cursor
     *
     * @return
     */
    public Cursor getMoveCursor() {
        return GuiCursor.MOVE_CURSOR;
    }

    /**
     * Predefined cursor
     *
     * @return
     */
    public Cursor getClickableCursor() {
        return GuiCursor.HAND_CURSOR;
    }

    /**
     * Affect software icon to window
     *
     * @param window
     */
    public void setWindowIconFor(Window window) {
        window.setIconImage(GuiIcons.WINDOW_ICON.getImage());
    }


    /**
     * Afficher la fenêtre de recadrage
     *
     * @param image
     */
    public void showCropWindow(BufferedImage image) {

        GuiUtils.throwIfNotOnEDT();

        getCropWindow().setImage(image);
        getCropWindow().setVisible(true);
    }

    public CropConfigurationWindow getCropWindow() {
        return (CropConfigurationWindow) registeredWindows.get(Windows.CROP_CONFIG);
    }

    public DetachedWindow getRobotImportWindow() {
        return (DetachedWindow) registeredWindows.get(Windows.ROBOT_IMPORT);
    }

    /**
     * Search for an interaction group and try to show it in dock.
     * <p>
     * If nothing is found, throw an exeption
     *
     * @param clss
     */
    public void showGroupInDock(Class clss) {
        showGroupInDock(clss.toString());
    }

    /**
     * Search for an interaction group and try to show it in dock.
     * <p>
     * If nothing is found, throw an exeption
     *
     * @param className
     */
    public void showGroupInDock(String className) {

        Dock[] docks = new Dock[]{getMainWindow().getEastDock(), getMainWindow().getWestDock()};

        for (int i = 0; i < docks.length; i++) {

            Dock d = docks[i];
            boolean r = d.showFirstMenuPanelCorrespondingThis(className);

            if (r) {
                return;
            }
        }

        throw new IllegalStateException("Unable to show: " + className);
    }

    @Override
    public EventNotificationManager getNotificationManager() {
        return notifm;
    }
}

package org.abcmap.gui.components.map;

import net.miginfocom.swing.MigLayout;
import org.abcmap.core.events.CacheRenderingEvent;
import org.abcmap.core.events.ProjectEvent;
import org.abcmap.core.events.manager.EventNotificationManager;
import org.abcmap.core.events.manager.HasEventNotificationManager;
import org.abcmap.core.log.CustomLogger;
import org.abcmap.core.managers.DrawManager;
import org.abcmap.core.managers.LogManager;
import org.abcmap.core.managers.MainManager;
import org.abcmap.core.project.Project;
import org.abcmap.core.rendering.CachedRenderingEngine;
import org.abcmap.gui.components.geo.MapNavigationBar;
import org.abcmap.gui.tools.MapTool;
import org.abcmap.gui.utils.GuiUtils;
import org.geotools.geometry.jts.ReferencedEnvelope;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * Display a map by using a partial cache system
 * <p>
 * Cache is managed by a RenderedPartialFactory. This partial factory produce portions of map and store it in database.
 * <p>
 */
public class CachedMapPane extends JPanel implements HasEventNotificationManager {

    private static final CustomLogger logger = LogManager.getLogger(CachedMapPane.class);

    /**
     * Project associated with this panel
     */
    private final Project project;

    /**
     * Rendering engine associated with pane
     */
    private final CachedRenderingEngine renderingEngine;

    /**
     * If set to true, panel will ask to current tool to paint if needed
     */
    private boolean acceptPaintFromTool;

    /**
     * If true, it is the first time panel is rendering
     */
    private boolean firstTimeRender;

    /**
     * World envelope (positions) of map rendered on panel
     */
    private ReferencedEnvelope currentWorlEnvelope;

    /**
     * Various mouse listeners which allow user to control map with mouse
     */
    private CachedMapPaneMouseController mouseControl;

    /**
     * Optional navigation bar in bottom of map
     */
    private MapNavigationBar navigationBar;

    /**
     * If set to true, more information are displayed
     */
    private boolean debugMode;

    /**
     * Minimal zoom factor relative to project width
     */
    //private double minZoomFactor;

    /**
     * Maximal zoom factor relative to project width
     */
    private double maxZoomFactor;

    private final EventNotificationManager notifm;
    private final DrawManager drawm;

    public CachedMapPane(Project p) {
        super(new MigLayout("fill"));

        drawm = MainManager.getDrawManager();
        this.acceptPaintFromTool = false;

        setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        addComponentListener(new RefreshMapComponentListener());

        maxZoomFactor = 3;

        this.project = p;
        this.renderingEngine = new CachedRenderingEngine(project);

        // first time render whole project
        firstTimeRender = true;

        // repaint when new partials are ready
        notifm = new EventNotificationManager(this);
        notifm.setDefaultListener((ev) -> {

            // new partials are ready, only repaint
            if (CacheRenderingEvent.isNewPartialsEvent(ev)) {
                CachedMapPane.this.repaint();
            }

            // map changed, prepare new and repaint
            else if (CacheRenderingEvent.isPartialsDeletedEvent(ev) || ev instanceof ProjectEvent) {
                refreshMap();
            }

        });

        // listen rendering new partials
        renderingEngine.getNotificationManager().addObserver(this);

        // listen map modifications
        MainManager.getProjectManager().getNotificationManager().addObserver(this);

        setDebugMode(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // don't paint before all is ready
        if (firstTimeRender) {
            if (debugMode) {
                logger.warning(this.getClass().getSimpleName() + ".paintComponent(): Call rejected, before first rendering operation");
            }
            return;
        }

        // improve quality of painting
        Graphics2D g2d = (Graphics2D) g;
        GuiUtils.applyQualityRenderingHints(g2d);

        // paint map
        renderingEngine.paint(g2d);

        // if debug mode enabled, paint world envelope asked
        if (debugMode) {
            paintGrid(g2d);
        }

        // let tool paint if needed
        if (acceptPaintFromTool && drawm.getCurrentTool() != null) {
            drawm.getCurrentTool().drawOnMainMap(g2d);
        }

    }

    /**
     * Paint grid for debug purposes
     *
     * @param g2d
     */
    private void paintGrid(Graphics2D g2d) {

        AffineTransform worldToScreenTransform = getWorldToScreenTransform();
        if (worldToScreenTransform != null) {

            Point2D blc = new Point2D.Double(currentWorlEnvelope.getMinX(), currentWorlEnvelope.getMinY());
            Point2D urc = new Point2D.Double(currentWorlEnvelope.getMaxX(), currentWorlEnvelope.getMaxY());
            blc = worldToScreenTransform.transform(blc, null);
            urc = worldToScreenTransform.transform(urc, null);

            int x = (int) blc.getX();
            int y = (int) urc.getY();
            int w = (int) Math.abs(urc.getX() - blc.getX());
            int h = (int) Math.abs(urc.getY() - blc.getY());

            g2d.setColor(Color.blue);
            int st = 2;
            g2d.setStroke(new BasicStroke(st));
            g2d.drawRect(x + st, y + st, w - st * 2, h - st * 2);

        }
    }


    /**
     * Refresh list of partials to display in component
     */
    public void refreshMap() {

        Dimension panelDimensions = getSize();

        // panel is not displayed yet, do not render
        if (panelDimensions.getWidth() < 1 || panelDimensions.getHeight() < 1) {
            if (debugMode) {
                logger.warning(this.getClass().getSimpleName() + ".refreshMap(): Call rejected, component too small " + panelDimensions);
            }
            return;
        }

        if (this.isVisible() == false) {
            if (debugMode) {
                logger.warning(this.getClass().getSimpleName() + ".refreshMap(): Call rejected, component not visible");
            }
            return;
        }

        // first time we have to render map,
        // render whole project
        if (firstTimeRender) {
            resetDisplay();
            firstTimeRender = false;
        }

        // ensure that envelope is valid and proportional to component
        checkWorldEnvelope();

        // prepare map to render
        try {
            renderingEngine.prepareMap(currentWorlEnvelope, panelDimensions);
        } catch (Exception e) {
            logger.error(e);
        }

        // repaint component
        repaint();

    }

    /**
     * Ensure that envelope is valid and proportional to component
     */
    private void checkWorldEnvelope() {

        Dimension panelDimensions = getSize();

        double coeffPx = panelDimensions.getWidth() / panelDimensions.getHeight();
        double coeffWu = currentWorlEnvelope.getWidth() / currentWorlEnvelope.getHeight();

        if (Math.abs(coeffPx - coeffWu) > 0.001) {

            double widthWu = currentWorlEnvelope.getWidth();
            double heightWu = widthWu / coeffPx;

            double minx = currentWorlEnvelope.getMinX();
            double miny = currentWorlEnvelope.getMinY();
            double maxx = currentWorlEnvelope.getMaxX();
            double maxy = miny + heightWu;

            currentWorlEnvelope = new ReferencedEnvelope(minx, maxx, miny, maxy, currentWorlEnvelope.getCoordinateReferenceSystem());
        }

    }

    /**
     * Pixel scale can be used to translate summary pixels to world unit
     *
     * @return
     */
    public double getScale() {
        return renderingEngine.getScale();
    }

    /**
     * @param direction
     */
    private void zoomEnvelope(int direction) {

        double projectWorldWidth = project.getMaximumBounds().getWidth();

        double minx;
        double maxx;
        double miny;
        double maxy;

        ReferencedEnvelope newEnv;

        // zoom in
        if (direction > 0) {

            double zoomStepW = currentWorlEnvelope.getWidth() / 10;
            double zoomStepH = currentWorlEnvelope.getHeight() * zoomStepW / currentWorlEnvelope.getWidth();

            minx = currentWorlEnvelope.getMinX() + zoomStepW;
            maxx = currentWorlEnvelope.getMaxX() - zoomStepW;

            miny = currentWorlEnvelope.getMinY() + zoomStepH;
            maxy = currentWorlEnvelope.getMaxY() - zoomStepH;

            newEnv = new ReferencedEnvelope(minx, maxx, miny, maxy, currentWorlEnvelope.getCoordinateReferenceSystem());
        }

        // zoom out
        else if (direction < 0) {

            // when zooming out, we need to have a 'zoom out step' greater than a 'zoom in step',
            // in order to restore previous envelope before zoom in, and reuse views in cache
            double zoomStepW = currentWorlEnvelope.getWidth() / 8;
            double zoomStepH = currentWorlEnvelope.getHeight() * zoomStepW / currentWorlEnvelope.getWidth();

            minx = currentWorlEnvelope.getMinX() - zoomStepW;
            maxx = currentWorlEnvelope.getMaxX() + zoomStepW;

            miny = currentWorlEnvelope.getMinY() - zoomStepH;
            maxy = currentWorlEnvelope.getMaxY() + zoomStepH;

            newEnv = new ReferencedEnvelope(minx, maxx, miny, maxy, currentWorlEnvelope.getCoordinateReferenceSystem());

        }

        // invalid argument
        else {
            throw new IllegalArgumentException("Invalid zoom direction: " + direction);
        }

        if (newEnv.getWidth() < projectWorldWidth * maxZoomFactor) {
            currentWorlEnvelope = newEnv;
        }

        // check if envelope is proportional
        checkWorldEnvelope();

    }

    /**
     * Zoom in one increment
     */
    public void zoomIn() {
        zoomEnvelope(1);
    }

    /**
     * Zoom out one increment
     */
    public void zoomOut() {
        zoomEnvelope(-1);
    }

    /**
     * Reset display to show whole width of map, from upper left corner corner
     */
    public void resetDisplay() {

        // TODO center map at a different scale ?
        // this could avoid "blank screen" when layers are large but empty

        // get world x and width
        ReferencedEnvelope projectBounds = project.getMaximumBounds();

        double worldWidthWu = projectBounds.getMaxX() - projectBounds.getMinX();
        double heightWu = getHeight() * worldWidthWu / getWidth();

        double minx = projectBounds.getMinX();
        double maxx = projectBounds.getMaxX();
        double miny = projectBounds.getMaxY() - heightWu;
        double maxy = projectBounds.getMaxY();

        currentWorlEnvelope = new ReferencedEnvelope(minx, maxx, miny, maxy, project.getCrs());

        // first time width: 3000px ?
        //System.out.println("Reset display: " + getSize() + " / " + scale + " / " + worldEnvelope + "");
    }

    /**
     * Enable navigation bar with zoom in/out and center button
     *
     * @param val
     */
    public void setNavigationBarEnabled(boolean val) {

        // add navigation bar
        if (val) {
            navigationBar = new MapNavigationBar(this);
            add(navigationBar, "alignx right, aligny bottom");
        }

        // remove bar if present
        else {
            if (navigationBar != null) {
                remove(navigationBar);
                navigationBar = null;
            }
        }
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        renderingEngine.setDebugMode(debugMode);
    }

    /**
     * Return a utility to transform coordinates
     *
     * @return
     */
    public AffineTransform getWorldToScreenTransform() {
        return renderingEngine.getWorldToScreenTransform();
    }

    /**
     * Return a utility to transform coordinates
     *
     * @return
     */
    public AffineTransform getScreenToWorldTransform() {
        return renderingEngine.getScreenToWorldTransform();
    }

    /**
     * Remove a tool from listener list
     *
     * @param tool
     */
    public void removeToolFromListeners(MapTool tool) {
        removeMouseListener(tool);
        removeMouseMotionListener(tool);
        removeMouseWheelListener(tool);
    }

    /**
     * Remove a tool from listener list
     *
     * @param tool
     */
    public void addToolToListeners(MapTool tool) {
        addMouseListener(tool);
        addMouseMotionListener(tool);
        addMouseWheelListener(tool);
    }

    /**
     * Observe this component and refresh map when needed
     */
    private class RefreshMapComponentListener implements ComponentListener {

        @Override
        public void componentResized(ComponentEvent e) {
            // refresh map if needed
            refreshMap();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            // refresh map if needed
            refreshMap();
        }

        @Override
        public void componentShown(ComponentEvent e) {
            // refresh map if needed
            refreshMap();
        }

        @Override
        public void componentHidden(ComponentEvent e) {

        }
    }

    /**
     * Enable or disable mouse control of map, in order to allow user to move or zoom map
     */
    public void setMouseManagementEnabled(boolean enabled) {

        // enable management
        if (enabled == true) {

            if (this.mouseControl != null) {
                return;
            }

            this.mouseControl = new CachedMapPaneMouseController(this);

            this.addMouseListener(mouseControl);
            this.addMouseMotionListener(mouseControl);
            this.addMouseWheelListener(mouseControl);

        }

        // disable management
        else {

            if (this.mouseControl == null) {
                return;
            }

            this.removeMouseListener(mouseControl);
            this.removeMouseMotionListener(mouseControl);
            this.removeMouseWheelListener(mouseControl);

            mouseControl = null;
        }
    }

    /**
     * Get project associated with rendering engine
     *
     * @return
     */
    public Project getProject() {
        return project;
    }

    /**
     * Set envelope shown by component
     *
     * @param worldEnvelope
     */
    public void setWorldEnvelope(ReferencedEnvelope worldEnvelope) {

        if (worldEnvelope.getCoordinateReferenceSystem().equals(project.getCrs()) == false) {
            throw new IllegalStateException("Coordinate Reference Systems are different: " + worldEnvelope.getCoordinateReferenceSystem() + " / " + project.getCrs());
        }

        this.currentWorlEnvelope = worldEnvelope;

        // check if envelope is proportional
        checkWorldEnvelope();
    }

    /**
     * If set to true, panel will ask to current tool to paint if needed
     */
    public void setAcceptPaintFromTool(boolean acceptPaintFromTool) {
        this.acceptPaintFromTool = acceptPaintFromTool;
    }

    /**
     * If set to true, panel will ask to current tool to paint if needed
     */
    public boolean isAcceptPaintFromTool() {
        return acceptPaintFromTool;
    }

    /**
     * Get specified world envelope to show
     *
     * @return
     */
    public ReferencedEnvelope getWorldEnvelope() {
        return new ReferencedEnvelope(currentWorlEnvelope);
    }

    @Override
    public EventNotificationManager getNotificationManager() {
        return notifm;
    }

}
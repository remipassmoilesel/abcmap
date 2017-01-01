package org.abcmap.core.project;

import org.abcmap.core.log.CustomLogger;
import org.abcmap.core.managers.LogManager;
import org.abcmap.core.project.layers.*;
import org.abcmap.core.project.layouts.LayoutSheet;
import org.abcmap.core.rendering.partials.RenderedPartialStore;
import org.abcmap.core.styles.StyleContainer;
import org.abcmap.core.styles.StyleLibrary;
import org.abcmap.core.tiles.TileStorage;
import org.abcmap.core.utils.GeoUtils;
import org.abcmap.core.utils.SQLProcessor;
import org.abcmap.core.utils.SQLUtils;
import org.abcmap.core.utils.Utils;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.MapContent;
import org.geotools.swing.JMapFrame;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/**
 * Represent a serializable project. Projects are stored in Geopakages, some kind of sqlite
 * database. All mainLayersList are read directly on the database.
 * <p>
 * Project are strongly dependant of database because mainLayersList read data directly in database
 */
public class Project {

    private static final CustomLogger logger = LogManager.getLogger(Project.class);

    /**
     * Temp directory, where is located database
     */
    private final Path tempDirectory;

    /**
     * Build and store styles
     */
    private final StyleLibrary styleLibrary;

    /**
     * Tile storage of the project, where are stored all tile coverages
     */
    private final TileStorage tileStorage;

    /**
     * Where are stored map rendered map partials, for display purposes
     */
    private final RenderedPartialStore partialStore;

    /**
     * List of layouts, sheets which can be printed
     */
    private ArrayList<LayoutSheet> layouts;

    /**
     * Only one layer is alterable at a time, the active layer
     */
    private AbstractLayer activeLayer;

    /**
     * Final path of the project, where the user want to save it.
     * <p>
     * This location is used only to save project.
     */
    private Path finalPath;

    /**
     * The path of the database
     */
    private Path databasePath;

    /**
     * List of mainLayersList. Layers wrap Geotools mainLayersList.
     */
    private ArrayList<AbstractLayer> mainLayersList;

    /**
     * Metadata about project
     */
    private ProjectMetadata metadataContainer;

    /**
     * CRS of the whole project
     * <p>
     * TODO: to remove ?
     */
    private CoordinateReferenceSystem crs;

    /**
     * Create a new project associated with the database at specified location.
     * <p>
     * Project file must be a temporary project file.
     *
     * @param databasePath
     * @throws IOException
     */
    public Project(Path databasePath) throws IOException {

        // default system
        this.crs = GeoUtils.WGS_84;

        this.databasePath = databasePath;
        this.tempDirectory = databasePath.getParent();
        this.metadataContainer = new ProjectMetadata();
        this.mainLayersList = new ArrayList<>();
        this.layouts = new ArrayList<>();
        this.finalPath = null;

        this.styleLibrary = new StyleLibrary();

        // create a new tile storage
        this.tileStorage = new TileStorage(databasePath);
        tileStorage.initialize();

        try {
            partialStore = new RenderedPartialStore(databasePath, crs);
        } catch (SQLException e) {
            throw new IOException("Unable to initialize database: " + e.getMessage(), e);
        }

    }

    /**
     * Get the path of the temporary database
     *
     * @return
     */
    public Path getDatabasePath() {
        return databasePath;
    }


    /**
     * Set the path of the temporary database
     *
     * @param databasePath
     */
    protected void setDatabasePath(Path databasePath) {
        this.databasePath = databasePath;
    }

    /**
     * Get metadataContainer associated with project: title, comments, ...
     *
     * @return
     */
    public ProjectMetadata getMetadataContainer() {
        return metadataContainer;
    }

    /**
     * Set metadataContainer associated with project: title, comments, ...
     *
     * @return
     */
    public void setMetadataContainer(ProjectMetadata metadata) {
        this.metadataContainer = metadata;
    }

    /**
     * Get a shallow copy of mainLayersList list
     * <p>
     * List is sorted by zindex, from lowest to highest
     *
     * @return
     */
    public ArrayList<AbstractLayer> getLayersList() {
        ArrayList<AbstractLayer> list = new ArrayList<>(mainLayersList);
        Collections.sort(list);
        return list;
    }

    /**
     * Return maximum bounds of all layers
     * // TODO Exclude WMS layers ?
     * // TODO: Check CRS compatibility between layers ?
     *
     * @return
     */
    public ReferencedEnvelope getMaximumBounds() {

        double minx = 0.0d;
        double miny = 0.0d;
        double maxx = 0.0d;
        double maxy = 0.0d;
        boolean firstLoop = true;

        for (AbstractLayer lay : getLayersList()) {
            ReferencedEnvelope bounds = lay.getInternalLayer().getBounds();
            // TODO: do not use empty feature layers ?

            if (bounds.getCoordinateReferenceSystem() != getCrs()) {
                logger.warning("Bounds computing: CRS are different");
            }

            if (firstLoop) {

                minx = bounds.getMinX();
                miny = bounds.getMinY();
                maxx = bounds.getMaxX();
                maxy = bounds.getMaxY();

                firstLoop = false;
                continue;
            }

            if (bounds.getMinX() < minx) {
                minx = bounds.getMinX();
            }
            if (bounds.getMinY() < miny) {
                miny = bounds.getMinY();
            }
            if (bounds.getMaxX() > maxx) {
                maxx = bounds.getMaxX();
            }
            if (bounds.getMaxY() > maxy) {
                maxy = bounds.getMaxY();
            }

        }

        return new ReferencedEnvelope(minx, maxx, miny, maxy, getCrs());
    }

    /**
     * Add a new feature layer, where can be stored draw
     *
     * @param name
     * @param visible
     * @param zindex
     */
    public AbstractLayer addNewFeatureLayer(String name, boolean visible, int zindex) throws IOException {

        // create a layer wrapper and store it
        AbstractLayer layer = null;
        try {
            layer = new FeatureLayer(null, name, visible, zindex, this, true);
        } catch (Exception e) {
            throw new IOException("Error while adding tile layer: ", e);
        }

        return addLayer(layer);
    }

    /**
     * Add a new tile layer, where can be stored tiles
     *
     * @param name
     * @param visible
     * @param zindex
     * @return
     */
    public AbstractLayer addNewTileLayer(String name, boolean visible, int zindex) throws IOException {
        TileLayer layer = null;
        try {
            layer = new TileLayer(null, name, visible, zindex, this, true);
        } catch (Exception e) {
            throw new IOException("Error while adding tile layer: ", e);
        }
        return addLayer(layer);
    }

    /**
     * Add specified layer to project and write the layer index. Return the layer or null if an error occur.
     *
     * @param layer
     * @return
     */
    protected AbstractLayer addLayer(AbstractLayer layer) {
        mainLayersList.add(layer);
        return layer;
    }

    /**
     * Set the project coordinate reference system
     * // TODO: generalize to mainLayersList ?
     *
     * @param crs
     */
    public void setCrs(CoordinateReferenceSystem crs) {
        this.crs = crs;
    }

    /**
     * Return the coordinate reference system of project
     *
     * @return
     */
    public CoordinateReferenceSystem getCrs() {
        if (crs == null) {
            logger.warning("CRS of project is null");
        }
        return crs;
    }

    /**
     * Return the background color of this project
     *
     * @return
     */
    public Color getBackgroundColor() {
        return Utils.stringToColor(metadataContainer.getValue(PMConstants.BG_COLOR));
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    /**
     * Close the database associated with this project.
     * <p>
     * Temporary files are not deleted
     */
    public void close() {

        try {
            SQLUtils.shutdownH2Database(databasePath);
        } catch (SQLException e) {
            logger.error("Error while shutting down database");
            logger.error(e);
        }

    }

    /**
     * Final path of the project, where the user want to save it.
     * <p>
     * This location can be null, and is used only to save project.
     */
    public Path getFinalPath() {
        return finalPath;
    }

    /**
     * Final path of the project, where the user want to save it.
     * <p>
     * This location is used only to save project.
     */
    public void setFinalPath(Path finalPath) {
        this.finalPath = finalPath;
    }

    /**
     * Path of temporary directory with databases and misc files
     *
     * @return
     */
    public Path getTempDirectory() {
        return tempDirectory;
    }

    /**
     * Set the active layer, the only layer alterable
     * <p>
     * Index correspond to index in sorted list of layers
     *
     * @param index
     */
    public void setActiveLayer(int index) {
        this.activeLayer = getLayersList().get(index);
    }

    /**
     * Set the active layer, the only layer alterable
     *
     * @param layer
     */
    public void setActiveLayer(AbstractLayer layer) {

        if (getLayersList().indexOf(layer) < 0) {
            throw new IllegalArgumentException("Invalid layer: " + layer);
        }

        this.activeLayer = layer;
    }

    /**
     * Get the active layer, the only layer alterable
     * <p>
     * There is always an active layer, because if all layers are removed, a new feature layer will be added
     *
     * @return
     */
    public AbstractLayer getActiveLayer() {
        return activeLayer;
    }

    /**
     * Execute an operation with database connection
     * <p>
     * "function" is a transaction block, if an exception is thrown nothing will be committed.
     * <p>
     * Be careful when you process long operations, SQLite do not support high concurrency
     * <p>
     * Execute an operation here avoid to have too many connections outside, maybe unclosed
     * <p>
     * <p>
     * /!\ No excpetions are thrown, please return a result that can indicate a potential fail. Eg: Boolean: null, true, false
     *
     * @return
     */
    public Object executeWithDatabaseConnection(SQLProcessor processor) {
        try {
            // sqlutils will process a transaction, not in auto commit mode
            // connection will be closed by utils function
            return SQLUtils.processTransaction(getDatabaseConnection(), processor);
        } catch (Exception e) {
            logger.error(e);
            return null;
        }
    }

    /**
     * Return layer index entries list
     *
     * @return
     */
    protected ArrayList<LayerIndexEntry> getLayerIndexEntries() {
        ArrayList<LayerIndexEntry> indexes = new ArrayList<>();
        for (AbstractLayer layer : getLayersList()) {
            indexes.add(layer.getIndexEntry());
        }
        return indexes;
    }

    /**
     * Informations used: styleLibrary finalPath mainLayersList metadataContainer crs layouts
     *
     * @param o
     * @return
     */


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(styleLibrary, project.styleLibrary) &&
                Objects.equals(layouts, project.layouts) &&
                Objects.equals(finalPath, project.finalPath) &&
                Objects.equals(mainLayersList, project.mainLayersList) &&
                Objects.equals(metadataContainer, project.metadataContainer) &&
                Objects.equals(crs, project.crs);
    }

    /**
     * Informations used: styleLibrary finalPath mainLayersList metadataContainer crs layouts
     *
     * @return
     */
    @Override
    public int hashCode() {
        return Objects.hash(styleLibrary, layouts, finalPath, mainLayersList, metadataContainer, crs);
    }

    public StyleContainer getStyle(Color activeForeground, Color activeBackground, int activeThick) {
        return getStyleLibrary().getStyle(activeForeground, activeBackground, activeThick);
    }

    /**
     * Return the style library associated with the project
     *
     * @return
     */
    public StyleLibrary getStyleLibrary() {
        return styleLibrary;
    }

    public TileStorage getTileStorage() {
        return tileStorage;
    }

    /**
     * Return the database connection.
     * <p>
     * Prefer use of executeWithDatabaseConnection() instead
     *
     * @return
     * @throws IOException
     */
    public Connection getDatabaseConnection() throws SQLException {
        return SQLUtils.getH2Connection(databasePath);
    }

    /**
     * Build a Geotools map content with available layers
     * <p>
     * This kind of map content is used only for debug purpose for now
     *
     * @return
     */
    public MapContent buildGlobalMapContent(boolean includeTileOutlines) {

        MapContent content = new MapContent();

        ArrayList<AbstractLayer> layers = getLayersList();

        // add mainLayersList
        for (AbstractLayer layer : layers) {

            // optionally add tile outlines
            content.addLayer(layer.getInternalLayer());
            if (includeTileOutlines && layer instanceof TileLayer) {
                content.addLayer(((TileLayer) layer).getOutlineLayer());
            }
        }

        return content;
    }

    /**
     * Show a project for debug purposes
     */
    public void showForDebug(boolean includeTileOutlines) {

        SwingUtilities.invokeLater(() -> {

            // Create a JMapFrame with a menu to choose the display style for the
            JMapFrame frame = new JMapFrame(buildGlobalMapContent(includeTileOutlines));
            frame.setSize(800, 600);
            frame.enableStatusBar(true);
            frame.enableTool(JMapFrame.Tool.ZOOM, JMapFrame.Tool.PAN, JMapFrame.Tool.RESET);
            frame.enableToolBar(true);
            frame.enableLayerTable(true);
            frame.setVisible(true);

        });

    }

    /**
     * Return database store where are stored rendered partials
     *
     * @return
     */
    public RenderedPartialStore getRenderedPartialStore() {
        return partialStore;
    }

    /**
     * Return layer corresponding to specified id or null
     *
     * @param layerId
     */
    public AbstractLayer getLayerById(String layerId) {

        for (AbstractLayer lay : getLayersList()) {
            if (Utils.safeEquals(lay.getId(), layerId)) {
                return lay;
            }
        }

        return null;
    }

    /**
     * Remove a layer from project
     * <p>
     * Layer is found by its index in a list sorted by zindex (getLayersList())
     * <p>
     * If no layer remains, a feature layer is added as if the project is new
     *
     * @param index
     */
    public void removeLayer(int index) {

        ArrayList<AbstractLayer> list = getLayersList();
        if (index < 0 || index > list.size()) {
            throw new IllegalArgumentException("Illegal index: " + index);
        }

        removeLayer(list.get(index));
    }

    /**
     * Remove a layer from project
     * <p>
     * If no layer remains, a feature layer is added as if the project is new
     *
     * @param lay
     */
    public void removeLayer(AbstractLayer lay) {

        mainLayersList.remove(lay);

        if (mainLayersList.size() < 1) {
            try {
                addFirstLayer();
            } catch (IOException e) {
                logger.error(e);
            }
        }

    }

    /**
     * Move specified layer to specified index in sorted list by zindex(getLayersList())
     * <p>
     * Return the new layers list
     *
     * @param layToMove
     * @param newIndex
     */
    public ArrayList<AbstractLayer> moveLayerToIndex(AbstractLayer layToMove, int newIndex) {

        ArrayList<AbstractLayer> list = getLayersList();

        // check arguments
        if (list.indexOf(layToMove) < 0) {
            throw new IllegalArgumentException("Invalid layer: " + layToMove);
        }

        if (newIndex < 0 || newIndex > list.size()) {
            throw new IllegalArgumentException("Invalid index: " + newIndex + " / min 0 / max " + list.size());
        }

        // remove layer
        list.remove(layToMove);

        // replace it on desired index
        list.add(newIndex, layToMove);

        // reset zindex
        for (int i = 0; i < list.size(); i++) {
            AbstractLayer lay = list.get(i);
            lay.setZindex(i);
        }

        return getLayersList();
    }

    /**
     * Invalidate cache for specified layer, on this thread
     * <p>
     * Evelope can be null, in this case whole layer will be deleted
     *
     * @param layId
     */
    public void deleteCacheForLayer(String layId, ReferencedEnvelope env) {

        if (getLayerById(layId) == null) {
            throw new IllegalArgumentException("Unable to find layer: " + layId);
        }

        partialStore.deletePartialsForLayer(layId, env);
    }

    /**
     * Add the first layer in project
     */
    public void addFirstLayer() throws IOException {
        addNewFeatureLayer("First layer", true, 0);
    }


    /**
     * Add a layout in project
     *
     * @param lay
     */
    public void addLayout(LayoutSheet lay) {
        layouts.add(lay);
    }

    /**
     * Return a shallow copy of layout list
     *
     * @return
     */
    public ArrayList<LayoutSheet> getLayouts() {
        return new ArrayList<>(layouts);
    }

    /**
     * Remove all sheets from layout list
     */
    public void removeAllLayouts() {
        layouts.clear();
    }

    /**
     * Remove specified layer
     *
     * @param lay
     */
    public void removeLayout(LayoutSheet lay) {
        layouts.remove(lay);
    }

    /**
     * Replace project layouts list by specified list
     *
     * @param layouts
     */
    public void setLayouts(ArrayList<LayoutSheet> layouts) {
        this.layouts = layouts;
    }

    /**
     * Get higher zindex available in layer
     *
     * @return
     */
    public int getHigherZindex() {
        ArrayList<AbstractLayer> list = getLayersList();
        return list.get(list.size() - 1).getZindex();
    }

    public void setAllElementsSelected(boolean allElementsSelected) {
        //TODO
    }


    public AbstractLayer addNewShapeFileLayer(Path p) throws IOException {

        // create a layer wrapper and store it
        AbstractLayer layer = null;
        try {
            layer = new ShapeFileLayer(null, p.toString(), true, getHigherZindex(), this, p);
        } catch (Exception e) {
            throw new IOException("Error while adding shapefile layer: ", e);
        }

        return addLayer(layer);
    }
}

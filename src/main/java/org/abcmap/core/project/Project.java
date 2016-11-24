package org.abcmap.core.project;

import org.abcmap.core.log.CustomLogger;
import org.abcmap.core.managers.LogManager;
import org.abcmap.core.project.layer.AbstractLayer;
import org.abcmap.core.project.layer.FeatureLayer;
import org.abcmap.core.project.layer.LayerIndexEntry;
import org.abcmap.core.project.layer.TileLayer;
import org.abcmap.core.tiles.TileStorage;
import org.abcmap.core.styles.StyleContainer;
import org.abcmap.core.styles.StyleLibrary;
import org.abcmap.core.utils.GeoUtils;
import org.abcmap.core.utils.SQLProcessor;
import org.abcmap.core.utils.SQLUtils;
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
 * database. All layers are read directly on the database.
 * <p>
 * Project are strongly dependant of database because layers read data directly in database
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
     * List of layers. Layers wrap Geotools layers.
     */
    private ArrayList<AbstractLayer> layers;

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

        this.databasePath = databasePath;

        this.tempDirectory = databasePath.getParent();
        this.metadataContainer = new ProjectMetadata();
        this.layers = new ArrayList();
        this.crs = GeoUtils.GENERIC_2D;
        this.finalPath = null;

        this.styleLibrary = new StyleLibrary();

        this.tileStorage = new TileStorage(databasePath);
        tileStorage.initialize();

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
     * Get all layers
     *
     * @return
     */
    public ArrayList<AbstractLayer> getLayers() {
        return layers;
    }

    /**
     * Add a new feature layer, where can be stored shapes
     *
     * @param name
     * @param visible
     * @param zindex
     */
    public AbstractLayer addNewFeatureLayer(String name, boolean visible, int zindex) throws IOException {

        // create a layer wrapper and store it
        AbstractLayer layer = null;
        try {
            layer = new FeatureLayer(null, name, visible, zindex, databasePath, true);
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
            layer = new TileLayer(null, name, visible, zindex, databasePath, true);
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
        layers.add(layer);
        return layer;
    }

    /**
     * Set the project coordinate reference system
     * // TODO: generalize to layers ?
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
        return crs;
    }

    /**
     * Return the background color of this project
     *
     * @return
     */
    public String getBackgroundColor() {
        return metadataContainer.getValue(PMConstants.BG_COLOR);
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
     * This location is used only to save project.
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
     *
     * @param index
     */
    public void setActiveLayer(int index) {
        this.activeLayer = layers.get(index);
    }

    /**
     * Set the active layer, the only layer alterable
     *
     * @param activeLayer
     */
    public void setActiveLayer(AbstractLayer activeLayer) {
        this.activeLayer = activeLayer;
    }

    /**
     * Get the active layer, the only layer alterable
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
        for (AbstractLayer layer : getLayers()) {
            indexes.add(layer.getIndexEntry());
        }
        return indexes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(styleLibrary, project.styleLibrary) &&
                Objects.equals(finalPath, project.finalPath) &&
                Objects.equals(layers, project.layers) &&
                Objects.equals(metadataContainer, project.metadataContainer) &&
                Objects.equals(crs, project.crs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(styleLibrary, finalPath, layers, metadataContainer, crs);
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
        return SQLUtils.createH2Connection(databasePath);
    }

    /**
     * Build a Geotools map content with available layers
     *
     * @return
     */
    public MapContent buildMapContent(boolean includeTileOutlines) {

        MapContent content = new MapContent();

        // sort layers by zindex
        Collections.sort(layers);

        // add layers
        for (AbstractLayer layer : layers) {

            // optionally add tile outlines
            content.addLayer(layer.getInternalLayer());
            if(includeTileOutlines && layer instanceof TileLayer){
                content.addLayer(((TileLayer) layer).getOutlineLayer());
            }
        }

        return content;
    }

    /**
     * Show a project for debug purposes
     */
    public void showForDebug(boolean includeTileOutlines)  {

        SwingUtilities.invokeLater(() -> {

            // Create a JMapFrame with a menu to choose the display style for the
            JMapFrame frame = new JMapFrame(buildMapContent(includeTileOutlines));
            frame.setSize(800, 600);
            frame.enableStatusBar(true);
            frame.enableTool(JMapFrame.Tool.ZOOM, JMapFrame.Tool.PAN, JMapFrame.Tool.RESET);
            frame.enableToolBar(true);
            frame.enableLayerTable(true);
            frame.setVisible(true);

        });

    }
}

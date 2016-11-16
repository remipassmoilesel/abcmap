package org.abcmap.core.project;

import org.abcmap.core.log.CustomLogger;
import org.abcmap.core.managers.LogManager;
import org.abcmap.core.project.layer.AbstractLayer;
import org.abcmap.core.project.layer.FeatureLayer;
import org.abcmap.core.project.layer.LayerIndexEntry;
import org.abcmap.core.project.layer.LayerType;
import org.abcmap.core.styles.StyleContainer;
import org.abcmap.core.styles.StyleLibrary;
import org.abcmap.core.utils.CRSUtils;
import org.abcmap.core.shapes.feature.DefaultFeatureBuilder;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.Transaction;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.map.MapContent;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

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
     * Database associated with project
     */
    private GeoPackage geopkg;

    /**
     * The path of the database
     */
    private Path databasePath;

    /**
     * List of layers. Layers wrap Geotools layers.
     */
    private ArrayList<AbstractLayer> layers;

    /**
     * Geotools map content used to render and display map
     */
    private MapContent mainMapContent;

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
     * Database object where are stored all data
     */
    private JDBCDataStore datastore;

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
        this.mainMapContent = new MapContent();
        this.crs = CRSUtils.GENERIC_2D;
        this.finalPath = null;

        this.styleLibrary = new StyleLibrary();

    }

    /**
     * Initialize geopackage object when database file is ready
     *
     * @throws IOException
     */
    protected void initializeGeopackage() throws IOException {

        this.geopkg = new GeoPackage(databasePath.toFile());

        // get feature source from geopackage
        Map<String, String> params = new HashMap();
        params.put("dbtype", "geopkg");
        params.put("database", databasePath.toString());

        this.datastore = (JDBCDataStore) DataStoreFinder.getDataStore(params);
    }

    public MapContent getMainMapContent() {
        return mainMapContent;
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
     * Method create and add a layer. This is the only way to create a layer.
     *
     * @param name
     * @param visible
     * @param zindex
     */
    public AbstractLayer addNewFeatureLayer(String name, boolean visible, int zindex) {

        // create a new feature source
        String layerid = LayerIndexEntry.generateId();
        FeatureSource source = null;
        try {
            source = createNewFeatureSoure(layerid);
        } catch (IOException e) {
            logger.error(e);
            return null;
        }

        // create a layer wrapper and store it
        AbstractLayer layer = new FeatureLayer(layerid, name, visible, zindex, source);
        return addLayer(layer);
    }

    /**
     * Create a new table in associated geopackage, and return a feature source
     *
     * @param layerid
     * @return
     * @throws IOException
     */
    private ContentFeatureSource createNewFeatureSoure(String layerid) throws IOException {

        // create a simple feature type
        // /!\ If DefaultEngineeringCRS.GENERIC2D is used, a lot of time is waste.
        // Prefer CRS.decode("EPSG:40400");
        SimpleFeatureType type = DefaultFeatureBuilder.getDefaultFeatureType(layerid, this.crs);
        FeatureEntry fe = new FeatureEntry();
        fe.setBounds(new ReferencedEnvelope());
        fe.setSrid(null);

        // create a geopackage entry
        geopkg.create(fe, type);

        return datastore.getFeatureSource(layerid);

    }

    /**
     * Add specified layer to project and write the layer index. Return the layer or null if an error occur.
     *
     * @param layer
     * @return
     */
    protected AbstractLayer addLayer(AbstractLayer layer) {
        layers.add(layer);
        mainMapContent.addLayer(layer.getInternalLayer());
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

        if (datastore != null) {
            logger.warning("Closing datastore: " + datastore);
            datastore.dispose();
        }

        if (geopkg != null) {
            logger.warning("Closing geopackage: " + geopkg.getFile().toString());
            geopkg.close();
        }

    }

    /**
     * Close the database associated with this project.
     * <p>
     * Temporary files are not deleted
     */
    public void close() {
        datastore.dispose();
        datastore = null;

        geopkg.close();
        geopkg = null;
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
     * Execute an operation here avoid to have too many connections outside, maybe unclosed
     *
     * @return
     */
    public Object executeWithDatabaseConnection(Function<Connection, Object> function) {

        try (Connection connection = datastore.getConnection(Transaction.AUTO_COMMIT)) {
            return function.apply(connection);
        } catch (SQLException | IOException e) {
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
}

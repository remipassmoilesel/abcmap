package org.abcmap.tests.core.rendering;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import junit.framework.TestCase;
import org.abcmap.TestUtils;
import org.abcmap.core.managers.Main;
import org.abcmap.core.project.Project;
import org.abcmap.core.rendering.partials.PartialOutlineFeatureBuilder;
import org.abcmap.core.rendering.partials.RenderedPartial;
import org.abcmap.core.rendering.partials.RenderedPartialStore;
import org.abcmap.core.rendering.partials.SerializableRenderedPartial;
import org.abcmap.core.utils.FeatureUtils;
import org.abcmap.core.utils.GeoUtils;
import org.abcmap.gui.utils.GuiUtils;
import org.geotools.data.FeatureStore;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertTrue;

public class PartialStoreTest {

    private boolean showWindows = false;

    protected final static StyleFactory sf = FeatureUtils.getStyleFactory();
    protected final static FilterFactory2 ff = FeatureUtils.getFilterFactory();

    @BeforeClass
    public static void beforeTests() throws IOException, InterruptedException {
        TestUtils.createNewProject();
    }

    @Test
    public void tests() throws IOException, SQLException, InterruptedException {

        // get partial store
        Project project = Main.getProjectManager().getProject();
        RenderedPartialStore store = project.getRenderedPartialStore();

        // side of partial in world unit
        double partialSide = 30;
        int nbrOfPartialsByLayer = 4;

        // we will create 3 layers, each one will have 4 partials with the same image
        String[] layerIds = new String[]{
                "layer0",
                "layer1",
                "layer2"
        };

        // all partials will have same image
        String imgPath = "/tiles/osm_1.png";
        BufferedImage img = ImageIO.read(PartialStoreTest.class.getResourceAsStream(imgPath));
        TestCase.assertTrue("Image reading: " + imgPath, img != null);

        // keep list of partials
        ArrayList<RenderedPartial> partials = new ArrayList<>();

        // create and add partials
        double miny = -partialSide;
        for (String layerId : layerIds) {

            double minx = -partialSide;
            double maxx = minx + partialSide;
            double maxy = miny + partialSide;

            for (int i = 0; i < nbrOfPartialsByLayer; i++) {

                ReferencedEnvelope ev = new ReferencedEnvelope(minx, maxx, miny, maxy, project.getCrs());
                RenderedPartial part = new RenderedPartial(img, ev, (int) ev.getWidth(), (int) ev.getHeight(), layerId);

                //System.out.println(ev);

                partials.add(part);
                store.addPartial(part);

                minx += partialSide;
                maxx = minx + partialSide;
            }

            miny += partialSide;
        }

        int expectedTotal = layerIds.length * nbrOfPartialsByLayer;

        // check if partials are in loaded list
        int loaded = store.getLoadedPartials().size();
        assertTrue("Loaded partials test: " + expectedTotal + " / " + loaded, loaded == expectedTotal);

        // check if partials have been added in database
        final int[] rowCount = {0, 0};
        project.executeWithDatabaseConnection((conn) -> {

            PreparedStatement stat = conn.prepareStatement("SELECT count(*) FROM " + SerializableRenderedPartial.TABLE_NAME);
            ResultSet rslt = stat.executeQuery();
            rslt.next();

            rowCount[0] = rslt.getInt(1);

            stat.close();
            rslt.close();

            // check outlines
            stat = conn.prepareStatement("SELECT count(*) FROM " + PartialOutlineFeatureBuilder.FEATURE_NAME);
            rslt = stat.executeQuery();
            rslt.next();

            rowCount[1] = rslt.getInt(1);

            stat.close();
            rslt.close();

            return null;
        });

        assertTrue("Partials insertion test: " + expectedTotal + " / " + rowCount[0], rowCount[0] == expectedTotal);
        assertTrue("Partial outlines insertion test: " + expectedTotal + " / " + rowCount[1], rowCount[1] == expectedTotal);

        // test envelopes
        rowCount[0] = 0;
        HashMap<Integer, Geometry[]> unequalGeometries = new HashMap<>();
        project.executeWithDatabaseConnection((conn) -> {

            PreparedStatement stat = conn.prepareStatement("SELECT * FROM " + PartialOutlineFeatureBuilder.FEATURE_NAME + ", " + SerializableRenderedPartial.TABLE_NAME
                    + " WHERE " + PartialOutlineFeatureBuilder.PARTIAL_ID_ATTRIBUTE_NAME + " = " + SerializableRenderedPartial.PARTIAL_ID_FIELD_NAME);

            ResultSet rslt = stat.executeQuery();

            HashMap<Long, ReferencedEnvelope> envelopeToCheck = new HashMap<>();
            while (rslt.next()) {

                int columnX1 = rslt.findColumn("X1");
                int columnX2 = rslt.findColumn("X2");
                int columnY1 = rslt.findColumn("Y1");
                int columnY2 = rslt.findColumn("Y2");
                int columnCRS = rslt.findColumn("CRS");
                int columnPARTIAL_ID = rslt.findColumn(PartialOutlineFeatureBuilder.PARTIAL_ID_ATTRIBUTE_NAME);

                double x1 = rslt.getDouble(columnX1);
                double x2 = rslt.getDouble(columnX2);
                double y1 = rslt.getDouble(columnY1);
                double y2 = rslt.getDouble(columnY2);
                CoordinateReferenceSystem crs = GeoUtils.stringToCrs(rslt.getString(columnCRS));
                long partialId = rslt.getLong(columnPARTIAL_ID);

                envelopeToCheck.put(partialId, new ReferencedEnvelope(x1, x2, y1, y2, crs));
            }

            rowCount[0] = envelopeToCheck.size();

            FeatureIterator featureIter = store.getOutlineFeatureStore().getFeatures().features();
            while (featureIter.hasNext()) {

                Feature feat = featureIter.next();
                Long partialId = (Long) feat.getProperty(PartialOutlineFeatureBuilder.PARTIAL_ID_ATTRIBUTE_NAME).getValue();

                Polygon geom1 = (Polygon) feat.getDefaultGeometryProperty().getValue();
                Polygon geom2 = JTS.toGeometry(envelopeToCheck.get(partialId));

                if (geom1.equals(geom2) == false) {
                    unequalGeometries.put(unequalGeometries.size(), new Geometry[]{geom1, geom2});
                }
            }

            featureIter.close();

            return null;
        });

        assertTrue("Partials / outlines Join test: " + rowCount[0], rowCount[0] == expectedTotal);
        assertTrue("Envelope equality test: " + unequalGeometries, unequalGeometries.size() == 0);

        // This envelope will be used to delete partials LATER.
        // It is created here in order to add it to debug windows if needed
        double minx = partialSide + 5;
        double maxx = partialSide * 2 + 5;
        miny = partialSide - 5;
        double maxy = partialSide + 10;
        ReferencedEnvelope boundsToDelete = new ReferencedEnvelope(minx, maxx, miny, maxy, project.getCrs());

        if (showWindows == true) {
            // this window should show three row of 4 partials, and delete area
            SimpleFeature feat = store.getOutlineFeatureBuilder().build(JTS.toGeometry(boundsToDelete), 0l, "nolayer");
            store.getOutlineFeatureStore().addFeatures(FeatureUtils.asList(feat));
            drawAndShowStore("All partials", store.getOutlineFeatureStore());
        }

        // delete all partials from layer 1 (bottom one)
        store.deletePartialsForLayer(layerIds[0]);

        rowCount[0] = 0;
        rowCount[1] = 0;
        project.executeWithDatabaseConnection((conn) -> {

            // check partials
            PreparedStatement stat = conn.prepareStatement("SELECT count(*) FROM " + SerializableRenderedPartial.TABLE_NAME
                    + " WHERE " + SerializableRenderedPartial.PARTIAL_LAYERID_FIELD_NAME + "=?");
            stat.setString(1, layerIds[0]);
            ResultSet rslt = stat.executeQuery();
            rslt.next();

            rowCount[0] = rslt.getInt(1);

            // check outlines
            stat = conn.prepareStatement("SELECT count(*) FROM " + PartialOutlineFeatureBuilder.FEATURE_NAME
                    + " WHERE " + PartialOutlineFeatureBuilder.LAYER_ID_ATTRIBUTE_NAME + "=?");
            stat.setString(1, layerIds[0]);
            rslt = stat.executeQuery();
            rslt.next();

            rowCount[1] = rslt.getInt(1);

            stat.close();
            rslt.close();
            return null;
        });

        if (showWindows == true) {
            // this window is supposed to show 2 row of 4 partials, and delete area
            drawAndShowStore("Without layer one", store.getOutlineFeatureStore());
        }

        assertTrue("Partials deletion in database test: " + rowCount[0], rowCount[0] == 0);
        assertTrue("Partial outlines deletion test: " + rowCount[1], rowCount[1] == 0);

        // delete partials according to an area
        store.deletePartialsForLayer(layerIds[1], boundsToDelete);

        // check in memory partials after deletion
        for (RenderedPartial partial : store.getLoadedPartials()) {
            if (partial.getLayerId().equals(layerIds[1])
                    && partial.getEnvelope().intersects((com.vividsolutions.jts.geom.Envelope) boundsToDelete)) {
                assertTrue("Deletion fail: " + partial, false);
            }
        }

        // check database after deletion
        rowCount[0] = 0;
        rowCount[1] = 0;
        project.executeWithDatabaseConnection((conn) -> {

            // check how many partials rest after deletion
            PreparedStatement stat = conn.prepareStatement("SELECT count(*) FROM " + SerializableRenderedPartial.TABLE_NAME
                    + " WHERE " + SerializableRenderedPartial.PARTIAL_LAYERID_FIELD_NAME + "=?");
            stat.setString(1, layerIds[1]);
            ResultSet rslt = stat.executeQuery();
            rslt.next();

            rowCount[0] = rslt.getInt(1);

            // check how many outlines rest after deletion
            stat = conn.prepareStatement("SELECT count(*) FROM " + PartialOutlineFeatureBuilder.FEATURE_NAME
                    + " WHERE " + PartialOutlineFeatureBuilder.LAYER_ID_ATTRIBUTE_NAME + "=?");
            stat.setString(1, layerIds[1]);
            rslt = stat.executeQuery();
            rslt.next();

            rowCount[1] = rslt.getInt(1);

            stat.close();
            rslt.close();

            return null;
        });

        assertTrue("Partials deletion on storage: " + rowCount[0], rowCount[0] == 2);
        assertTrue("Partial outlines deletion test: " + rowCount[1], rowCount[1] == 2);

        if (showWindows == true) {
            // this window is supposed to show one row with 2 partials, one row with 4 partials, and delete area
            SimpleFeature feat = store.getOutlineFeatureBuilder().build(JTS.toGeometry(boundsToDelete), 0l, "nolayer");
            store.getOutlineFeatureStore().addFeatures(FeatureUtils.asList(feat));
            drawAndShowStore("Without boundsToDelete", store.getOutlineFeatureStore());

            Thread.sleep(50000);
        }


    }

    /**
     * Show a snapshot of feature store for debug purposes
     *
     * @param title
     * @param store
     */
    private static void drawAndShowStore(String title, FeatureStore store) {

        Style style = sf.createStyle();
        style.featureTypeStyles().add(getOutlineFeatureTypeStyle());
        FeatureLayer layer = new FeatureLayer(store, style);

        int width = 500;
        int height = 500;
        BufferedImage renderedImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = (Graphics2D) renderedImg.getGraphics();

        StreamingRenderer renderer = GeoUtils.buildRenderer();
        MapContent mapContent = new MapContent();
        mapContent.addLayer(layer);
        renderer.setMapContent(mapContent);

        System.out.println("content");
        System.out.println(mapContent);

        renderer.paint(g2d, new Rectangle(width, height), layer.getBounds());

        mapContent.dispose();

        GuiUtils.showImage(title, renderedImg);
    }

    /**
     * Return default feature type style for outline layer
     *
     * @return
     */
    private static FeatureTypeStyle getOutlineFeatureTypeStyle() {

        Color foreground = Color.green;
        Color background = Color.yellow;

        // create point symbolizer
        org.geotools.styling.Stroke stroke = sf.stroke(ff.literal(foreground), null, null, null, null, null, null);
        Fill fill = sf.fill(null, ff.literal(background), ff.literal(0.2));

        // create line symbolizer
        LineSymbolizer lineSym = sf.createLineSymbolizer(stroke, null);

        // create polygon symbolizer
        PolygonSymbolizer polygonSym = sf.createPolygonSymbolizer(stroke, fill, null);

        // create rule
        Rule r = sf.createRule();
        r.symbolizers().add(lineSym);
        r.symbolizers().add(polygonSym);
        r.setIsElseFilter(true);

        return sf.createFeatureTypeStyle(new Rule[]{r});
    }


}

package org.abcmap.core.shapes;

import com.vividsolutions.jts.geom.Coordinate;
import org.abcmap.core.project.Project;
import org.abcmap.core.project.layer.FeatureLayer;
import org.opengis.feature.simple.SimpleFeature;

public class PointBuilder extends AbstractShapeBuilder {

    public PointBuilder(FeatureLayer layer) {
        super(layer);
    }


    /**
     * Create a new point.
     * <p>
     * Positions are in world coordinates.
     *
     * @param coord
     */
    public SimpleFeature addPoint(Coordinate coord) {
        currentFeature = getActiveLayer().addShape(geometryFactory.createPoint(coord));
        applyStyle();
        return currentFeature;
    }

    @Override
    public void cancelDrawing() {
        // Nothing to do here
    }

}
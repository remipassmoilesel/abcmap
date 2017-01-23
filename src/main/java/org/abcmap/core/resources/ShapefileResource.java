package org.abcmap.core.resources;

import org.abcmap.core.configuration.ConfigurationConstants;
import org.abcmap.core.log.CustomLogger;
import org.abcmap.core.managers.LogManager;
import org.abcmap.core.project.Project;
import org.abcmap.core.project.layers.AbmShapefileLayer;
import org.abcmap.core.utils.ZipUtils;
import org.abcmap.gui.utils.GuiUtils;
import org.apache.commons.io.FileUtils;
import org.geotools.referencing.operation.projection.ProjectionException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 *
 */
public class ShapefileResource extends DistantResource {

    private static final CustomLogger logger = LogManager.getLogger(ShapefileResource.class);

    /**
     * Base url of repo
     *
     * @return
     */
    protected String baseUrl;

    /**
     * Resource path relative to base url
     */
    protected String resourcePath;

    /**
     * Size of archive to download in mo
     */
    protected Double size;

    public ShapefileResource(String name, String baseUrl, String resourcePath) {
        super(name, "");
        this.name = name;
        this.baseUrl = baseUrl;
        this.resourcePath = resourcePath;
    }

    public void importIn(Project p, Consumer<Object[]> update) throws IOException {
        p.addLayer(getDistantLayer(p, update, 700));
        p.fireLayerListChanged();
    }

    public AbmShapefileLayer getDistantLayer(Project p, Consumer<Object[]> updates, int updatePeriodMs) throws IOException {

        GuiUtils.throwIfOnEDT();

        // resolve url of distant shape file
        URI url = null;
        try {
            url = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid url: " + baseUrl);
        }
        url = url.resolve(resourcePath);
        String fileName = Paths.get(resourcePath).getFileName().toString();

        // create directories
        Path destinationDirectory = ConfigurationConstants.DATA_DIR_PATH.resolve(fileName);
        if (Files.isDirectory(destinationDirectory)) {
            destinationDirectory = Paths.get(destinationDirectory.toAbsolutePath() + "_" + System.currentTimeMillis());
        }

        Files.createDirectories(destinationDirectory);

        // create file, and add a timer to watch download
        final Path destinationFile = destinationDirectory.resolve(fileName);

        Timer updateTimer = null;
        if (updatePeriodMs > -1) {
            updateTimer = new Timer("", true);
            Path finalDestinationDirectory = destinationDirectory;
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {

                    double dirSize = -1;
                    try {
                        dirSize = (double) Files.size(destinationFile) / 1024 / 1024;
                    } catch (IOException e) {
                        logger.error(e);
                    }

                    updates.accept(new Object[]{
                            finalDestinationDirectory,
                            dirSize,
                            getSize()
                    });


                }
            }, 0, updatePeriodMs);
        }
        try {

            // download file
            FileUtils.copyURLToFile(url.toURL(), destinationFile.toFile(), 2000, 2000);

            // unzip file
            List<Path> uncompressed = ZipUtils.uncompress(destinationFile, destinationDirectory);

            if (uncompressed.size() < 1) {
                throw new IOException("Downloaded archive is empty: " + destinationDirectory);
            }

            for (Path file : uncompressed) {
                if (file.getFileName().toString().endsWith(".shp")) {

                    // create layer and return it
                    try {
                        return new AbmShapefileLayer(null, file.getFileName().toString(), true, p.getHigherZindex(), file, p);
                    } catch (ProjectionException e) {
                        throw new IOException("Error creating shape file layer", e);
                    }

                }
            }

            throw new IOException("No shapefiles found in: " + destinationDirectory);

        } finally {
            // cancel timer
            if (updatePeriodMs > -1) {
                updateTimer.cancel();
            }

        }


    }

    public Double getSize() {
        return size;
    }

    public void setSize(Double size) {
        this.size = size;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getResourceUrl() {

        String url = baseUrl;
        if (baseUrl.substring(baseUrl.length() - 2).equals("/") == false) {
            url += "/";
        }
        url += resourcePath;

        return url;
    }

    @Override
    public String toString() {
        return "ShapefileResource{" +
                "baseUrl='" + baseUrl + '\'' +
                ", resourcePath='" + resourcePath + '\'' +
                ", size='" + size + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ShapefileResource that = (ShapefileResource) o;
        return Objects.equals(baseUrl, that.baseUrl) &&
                Objects.equals(resourcePath, that.resourcePath) &&
                Objects.equals(size, that.size);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), baseUrl, resourcePath, size);
    }
}

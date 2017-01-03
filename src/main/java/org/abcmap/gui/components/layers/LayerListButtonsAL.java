package org.abcmap.gui.components.layers;

import org.abcmap.core.log.CustomLogger;
import org.abcmap.core.managers.*;
import org.abcmap.core.project.Project;
import org.abcmap.core.project.layers.AbstractLayer;
import org.abcmap.core.threads.ThreadManager;
import org.abcmap.gui.dialogs.RenameLayerDialog;
import org.abcmap.gui.utils.GuiUtils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Listent user actions on layers
 */
public class LayerListButtonsAL implements ActionListener {

    private static final CustomLogger logger = LogManager.getLogger(LayerListButtonsAL.class);

    public static final String NEW = "NEW";
    public static final String REMOVE = "REMOVE";

    public static final String RENAME = "RENAME";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String CHANGE_VISIBILITY = "CHANGE_VISIBILITY";

    public static final String MOVE_UP = "MOVE_UP";
    public static final String MOVE_DOWN = "MOVE_DOWN";

    private String mode;
    private ProjectManager projectm;
    private CancelManager cancelm;
    private GuiManager guim;

    public LayerListButtonsAL(String mode) {
        this.mode = mode;
        this.projectm = Main.getProjectManager();
        this.cancelm = Main.getCancelManager();
        this.guim = Main.getGuiManager();
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        GuiUtils.throwIfNotOnEDT();

        if (projectm.isInitialized() == false) {
            return;
        }

        Project project = projectm.getProject();
        AbstractLayer lay = project.getActiveLayer();

        if (CHANGE_VISIBILITY.equals(mode)) {
            changeLayerVisibility(lay);
        }
        //
        else if (NEW.equals(mode)) {
            createNewFeatureLayer();
        }
        //
        else if (MOVE_UP.equals(mode)) {
            moveLayer(lay, +1);
        }
        //
        else if (MOVE_DOWN.equals(mode)) {
            moveLayer(lay, -1);
        }
        //
        else if (REMOVE.equals(mode)) {
            removeLayer(lay);
        }
        //
        else if (DUPLICATE.equals(mode)) {
            duplicateLayer(lay);
        }
        //
        else if (RENAME.equals(mode)) {
            renameLayer(lay);
        }

        // TODO: fire event ?
    }

    private void changeLayerVisibility(AbstractLayer lay) {
        //TODO: save operation in cancelm

        lay.setVisible(!lay.isVisible());

        // fire event
        projectm.fireLayerChanged(lay);
    }

    private void moveLayer(AbstractLayer lay, int step) {

        //TODO: save operation in cancelm

        Project project = projectm.getProject();
        ArrayList<AbstractLayer> layers = project.getLayersList();
        int newIndex = layers.indexOf(lay) + step;

        // move layer only if new index is in bounds
        if (newIndex >= 0 && newIndex < layers.size()) {
            project.moveLayerToIndex(lay, newIndex);
        }

        // fire event
        projectm.fireLayerListChanged();
    }

    private void removeLayer(AbstractLayer lay) {

        //TODO: save operation in cancelm

        // remove active layer
        Project project = projectm.getProject();
        project.removeLayer(lay);

        // change active layer
        int index = project.getLayersList().size() - 1;
        project.setActiveLayer(index);

        // fire event
        projectm.fireLayerListChanged();
    }

    private void createNewFeatureLayer() {

        //TODO: save operation in cancelm

        Project project = projectm.getProject();
        AbstractLayer lay;
        try {

            // create a new layer
            lay = project.addNewFeatureLayer("Nouvelle couche", true, project.getHigherZindex() + 1);

            // set new layer active
            project.setActiveLayer(lay);

        } catch (IOException e) {
            logger.error(e);
        }

        // fire event
        projectm.fireLayerListChanged();

    }

    private void renameLayer(AbstractLayer lay) {

        //TODO: save operation in cancelm

        String name = lay.getName();

        // show dialog with original name
        RenameLayerDialog dial = new RenameLayerDialog(guim.getMainWindow(), name);
        dial.setVisible(true);

        // change name if validated
        if (RenameLayerDialog.ACTION_VALIDATED.equals(dial.getAction())) {
            lay.setName(dial.getInput());
        }

        // fire event
        projectm.fireLayerListChanged();
    }


    private void duplicateLayer(final AbstractLayer lay) {

        // TODO: to complete
        ThreadManager.runLater(new Runnable() {
            @Override
            public void run() {
                throw new IllegalStateException("Unimplemented method !");
            }
        });
    }


}

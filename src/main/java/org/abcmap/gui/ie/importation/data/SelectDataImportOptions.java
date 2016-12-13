package org.abcmap.gui.ie.importation.data;

import org.abcmap.gui.components.data.DataImportOptionsPanel;
import org.abcmap.gui.ie.InteractionElement;

import java.awt.*;

public class SelectDataImportOptions extends InteractionElement {

    public SelectDataImportOptions() {

        this.label = "Options d'import de donnnées";
        this.help = "Sélectionnez ici les options d'import dedonnées.";

    }

    @Override
    protected Component createPrimaryGUI() {
        DataImportOptionsPanel panel = new DataImportOptionsPanel();
        return panel;
    }

}
package org.abcmap.gui.ie.importation.directory;

import org.abcmap.gui.ie.InteractionElementGroup;
import org.abcmap.gui.ie.importation.SelectCropAreaForDirectory;
import org.abcmap.gui.ie.importation.SelectPictureAnalyseMode;

public class MenuImportFromDirectory extends InteractionElementGroup {

    public MenuImportFromDirectory() {

        this.label = "Importer et assembler un dossier d'images...";
        this.help = "Cliquez ici pour importer et assembler automatiquement un dossier d'images. Le dossier d'image sera inspecté, et toutes"
                + " les images présentes seront assemblées automatiquement pour créer un fond de carte.";

        // selection du dossier
        addInteractionElement(new SelectDirectoryToImport());

        // parametres d'analyse
        addInteractionElement(new SelectPictureAnalyseMode());

        // parametres de recadrage
        addInteractionElement(new SelectCropAreaForDirectory());

        // bouton de lancement
        addInteractionElement(new LaunchDirectoryImport());

    }
}
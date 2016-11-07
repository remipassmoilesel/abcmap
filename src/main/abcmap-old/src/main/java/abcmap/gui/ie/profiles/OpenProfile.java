package abcmap.gui.ie.profiles;

import java.awt.Window;
import java.io.File;
import java.io.IOException;

import abcmap.gui.dialogs.ClosingConfirmationDialog;
import abcmap.gui.dialogs.QuestionResult;
import abcmap.gui.dialogs.simple.BrowseDialogResult;
import abcmap.gui.dialogs.simple.SimpleBrowseDialog;
import abcmap.gui.ie.InteractionElement;
import abcmap.managers.Log;
import abcmap.managers.stub.MainManager;
import abcmap.utils.gui.GuiUtils;

public class OpenProfile extends InteractionElement {

	public OpenProfile() {
		label = "Ouvrir un profil de configuration...";
		help = "Cliquez ici pour ouvrir un nouveau profil de configuration.";
	}

	@Override
	public void run() {

		// pas de lancement dans l'EDT
		GuiUtils.throwIfOnEDT();

		// eviter les appels intempestifs
		if (threadAccess.askAccess() == false) {
			return;
		}

		// threadAccess.releaseAccess();

		// boite parcourir de selection de fichier
		Window parent = guim.getMainWindow();
		BrowseDialogResult result = SimpleBrowseDialog
				.browseProfileToOpenAndWait(parent);

		// l'action a ete annulée
		if (result.isActionCanceled()) {
			threadAccess.releaseAccess();
			return;
		}

		// verifier si l'utilisateur souhaite enregistrer.
		if (MainManager.isDebugMode() == false) {
			QuestionResult cf = ClosingConfirmationDialog
					.showProfileConfirmationAndWait(guim.getMainWindow());
			if (cf.isAnswerYes() == false) {
				threadAccess.releaseAccess();
				return;
			}
		}

		// ouverture du profil
		openProfile(result.getFile());

		threadAccess.releaseAccess();
	}

	public void openProfile(File file) {

		try {
			configm.loadProfile(file);

			// conservation dans les profils recents
			recentsm.addProfile(file);
			recentsm.saveHistory();

			guim.showErrorInBox("Le profil est chargé.");

		} catch (IOException e) {
			guim.showErrorInBox("Erreur lors de l'ouverture du profil de configuration.");
			Log.error(e);
		}

	}

}

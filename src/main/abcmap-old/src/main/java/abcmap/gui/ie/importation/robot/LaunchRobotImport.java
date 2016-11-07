package abcmap.gui.ie.importation.robot;

import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import abcmap.exceptions.MapImportException;
import abcmap.gui.dock.comps.blockitems.SimpleBlockItem;
import abcmap.gui.ie.InteractionElement;
import abcmap.managers.Log;
import abcmap.utils.Utils;
import net.miginfocom.swing.MigLayout;

public class LaunchRobotImport extends InteractionElement {

	private static final String START = "START";
	private static final String STOP = "STOP";
	private JButton btnLaunch;
	private JButton btnStop;

	public LaunchRobotImport() {

		label = "Lancer l'import automatique";
		help = "Cliquez ici pour lancer l'import automatique d'écran.";

		displaySimplyInSearch = false;

	}

	@Override
	protected Component createPrimaryGUI() {

		// panneau support
		JPanel panel = new JPanel(new MigLayout("insets 5"));

		// bouton de lancement
		btnLaunch = new JButton("Lancer l'import");
		btnLaunch.addActionListener(this);
		btnLaunch.setActionCommand(START);
		panel.add(btnLaunch);

		// bouton d'arret
		btnStop = new JButton("Arrêter l'import");
		btnStop.addActionListener(this);
		btnStop.setActionCommand(STOP);
		panel.add(btnStop);

		return panel;
	}

	@Override
	public void run() {

		if (getLastActionCommand() == null) {
			return;
		}

		// lancer l'import
		if (Utils.safeEquals(START, getLastActionCommand())) {

			try {
				importm.startRobotImport();
				guim.showMessageInBox("Début de l'import automatique.");
			}

			// erreur lors du lancement
			catch (MapImportException e) {

				Log.error(e);

				if (Utils.safeEquals(
						MapImportException.ROBOT_INSTATIATION_EXCEPTION,
						e.getMessage())) {
					// TODO: message à compléter
					guim.showErrorInDialog(
							guim.getMainWindow(),
							"Impossible de lancer l'import automatique: autorisation réfusée ou erreur interne"
							+ " de machine virtuelle Java.",
							false);
				}

				else if (Utils.safeEquals(MapImportException.ALREADY_IMPORTING,
						e.getMessage())) {
					guim.showErrorInBox("Un autre import est déjà en cours.");
				}

				else {
					guim.showErrorInBox("Erreur lors de l'importation automatique.");
				}
			}
		}

		// arreter l'import
		else {
			importm.stopDirectoryImportLater();
			guim.showMessageInBox("L'import sera bientôt stoppé.");
		}

	}

	/**
	 * Ne pas désactiver le bouton d'aret d'import. En cas d'erreur,
	 * l'utilisateur pourra recreer un objet d'import.
	 */
	@Deprecated
	private void checkBoutonsEnabled() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				Boolean val = importm.isDirectoryImporting();
				btnLaunch.setEnabled(!val);
				btnStop.setEnabled(val);
			}
		});

	}

}

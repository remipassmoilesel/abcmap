package abcmap.gui.dialogs;

import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JDialog;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import abcmap.gui.GuiStyle;
import abcmap.gui.comps.buttons.HtmlButton;
import abcmap.gui.comps.share.DonateButtonsPanel;
import abcmap.gui.comps.share.ShareButtonsPanel;
import abcmap.utils.gui.GuiUtils;

public class AboutProjectDialog extends JDialog {

	public AboutProjectDialog(Window parent) {
		super(parent);

		setModal(true);
		setUndecorated(false);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		setSize(new Dimension(300, 500));
		setLocationRelativeTo(null);

		JPanel cp = new JPanel(new MigLayout());

		// titre
		GuiUtils.addLabel("A propos d'Abc-Map", cp, "gaptop 10px, wrap 10px,",
				GuiStyle.DIALOG_TITLE_1);

		// texte
		GuiUtils.addLabel(
				"Ce programme à été conçu grâce aux technologies suivantes: "
						+ "<ul><li>ImageJ du NIH,</li>" + "<li>Geotools,</li>"
						+ "<li>Java,</li>" + "</ul>", cp,
				"gaptop 10px, wrap 10px,", GuiStyle.DIALOG_TEXT);

		// réseaux sociaux
		GuiUtils.addLabel("Faites connaitre le projet: ", cp,
				"align center, gaptop 15px, wrap", GuiStyle.DIALOG_TITLE_2);

		ShareButtonsPanel sharePanel = new ShareButtonsPanel();
		cp.add(sharePanel, "align center, wrap");

		// dons
		DonateButtonsPanel dbp = new DonateButtonsPanel();
		cp.add(dbp, "align center, wrap");

		// fermer le dialogue
		HtmlButton closeBtn = new HtmlButton("Fermer");
		closeBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		});
		cp.add(closeBtn, "align center, wrap");

		setContentPane(cp);

		pack();

	}

}

import javax.swing.*;

/**
 * Created by eric on 5/8/15.
 */
public class GUI extends JFrame {
    private JComboBox boatComboBox;
    private JCheckBox centerCheckBox;
    private JCheckBox prayerCheckBox;
    private JButton startButton;
    private JPanel mainPanel;

    boolean formComplete;
    int boat;
    boolean quickPrayer;
    boolean fightCenter;

    public GUI() {
        super("NVSPestControl");
        setContentPane(mainPanel);
        pack();
        startButton.addActionListener(e -> {
            boat = boatComboBox.getSelectedIndex();
            quickPrayer = prayerCheckBox.isSelected();
            fightCenter = centerCheckBox.isSelected();
            formComplete = true;
            setVisible(false);
        });

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setVisible(true);
    }

}

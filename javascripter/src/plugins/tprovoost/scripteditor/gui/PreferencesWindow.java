package plugins.tprovoost.scripteditor.gui;

import icy.gui.component.IcyTextField;
import icy.gui.frame.IcyFrame;
import icy.preferences.PluginPreferences;
import icy.preferences.XMLPreferences;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class PreferencesWindow extends IcyFrame
{
    private static PreferencesWindow singleton = new PreferencesWindow();
    // private JTable table;
    private JPanel panel;
    private XMLPreferences prefs = PluginPreferences.getPreferences().node("scripteditor");
    // private PreferencesTableModel tableModel;
    private final String PREF_VAR_INTERPRET = "varinterp";
    private final String PREF_OVERRIDE = "override";
    private final String PREF_VERIF = "autoverif";
    private final String PREF_STRICT = "strictmode";
    private final String PREF_INDENT_SPACES = "indent";
    private final String PREF_INDENT_SPACES_VALUE = "nbSpaces";
    private final String PREF_FULL_AUTOCOMPLETE = "fullautocomplete";
    private IcyTextField tfSpacesTab;
    private JCheckBox cboxVarInterp;
    private JCheckBox cboxOverride;
    private JCheckBox cboxAutoVerif;
    private JCheckBox cboxStrict;
    private JCheckBox cboxSoft;
    private boolean release = false;
    private JCheckBox cboxAdvanced;
    private JCheckBox cboxFullAutocomplete;

    private PreferencesWindow()
    {
        super("Script Editor Preferences", false, true, false, true);
        setContentPane(createGUI());
    }

    public static PreferencesWindow getPreferencesWindow()
    {
        return singleton;
    }

    private JPanel createGUI()
    {
        JPanel toReturn = new JPanel();
        toReturn.setBorder(new EmptyBorder(4, 4, 4, 4));
        toReturn.setLayout(new BorderLayout(0, 0));

        JPanel panelButtons = new JPanel();
        panelButtons.setBorder(new EmptyBorder(4, 0, 4, 0));
        toReturn.add(panelButtons, BorderLayout.SOUTH);
        panelButtons.setLayout(new BoxLayout(panelButtons, BoxLayout.X_AXIS));

        panelButtons.add(Box.createHorizontalGlue());

        // JButton btnApply = new JButton("Apply");
        // panelButtons.add(btnApply);

        // panelButtons.add(Box.createHorizontalStrut(20));

        JButton btnOk = new JButton("OK");
        btnOk.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                savePrefs();
                close();
            }

        });
        panelButtons.add(btnOk);

        panelButtons.add(Box.createHorizontalStrut(20));

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                close();
            }

        });
        panelButtons.add(btnClose);

        JLabel lblPreferences = new JLabel("<html><h2>Preferences</h2></html>");
        lblPreferences.setHorizontalAlignment(SwingConstants.CENTER);
        toReturn.add(lblPreferences, BorderLayout.NORTH);

        JPanel panelCenter = new JPanel();
        toReturn.add(panelCenter, BorderLayout.CENTER);

        // table = new JTable();
        // table.setShowGrid(false);
        // table.setOpaque(false);
        // table.setModel(tableModel);
        // table.getColumnModel().getColumn(0).setPreferredWidth(190);
        panelCenter.setLayout(new BorderLayout(0, 0));
        panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panelCenter.add(panel);

        // -------------------
        // Full autocomplete
        // -------------------
        JPanel panelFullAutoComplete = new JPanel();
        panel.add(panelFullAutoComplete);
        panelFullAutoComplete.setLayout(new BoxLayout(panelFullAutoComplete, BoxLayout.X_AXIS));
        JLabel lblFullAutocomplete = new JLabel("Enable Auto Complete after any character");
        panelFullAutoComplete.add(lblFullAutocomplete);
        cboxFullAutocomplete = new JCheckBox("");

        panelFullAutoComplete.add(cboxFullAutocomplete);
        panelFullAutoComplete.add(Box.createHorizontalGlue());

        // -------------------
        // Var interpretation
        // -------------------
        JPanel panelVarInterp = new JPanel();
        if (!release)
            panel.add(panelVarInterp);
        panelVarInterp.setLayout(new BoxLayout(panelVarInterp, BoxLayout.X_AXIS));
        JLabel lblVarInterp = new JLabel("Enable variable interpretation (beta)*");
        panelVarInterp.add(lblVarInterp);
        cboxVarInterp = new JCheckBox("");

        panelVarInterp.add(cboxVarInterp);
        panelVarInterp.add(Box.createHorizontalGlue());

        // -----------------------------------
        // Override verification (javascript)
        // -----------------------------------
        JPanel panelOverride = new JPanel();
        panelOverride.setLayout(new BoxLayout(panelOverride, BoxLayout.X_AXIS));
        if (!release)
            panel.add(panelOverride);

        JLabel lblOverride = new JLabel("Override verification (javascript)");
        panelOverride.add(lblOverride);

        cboxOverride = new JCheckBox("");
        panelOverride.add(cboxOverride);

        panelOverride.add(Box.createHorizontalGlue());
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // ------------------
        // Auto verification
        // ------------------
        JPanel panelAutoVerif = new JPanel();
        if (!release)
            panel.add(panelAutoVerif);
        panelAutoVerif.setLayout(new BoxLayout(panelAutoVerif, BoxLayout.X_AXIS));

        JLabel lblAutoVerif = new JLabel("Enable auto verification (javascript/beta)*");
        panelAutoVerif.add(lblAutoVerif);

        cboxAutoVerif = new JCheckBox("");
        panelAutoVerif.add(cboxAutoVerif);
        panelAutoVerif.add(Box.createHorizontalGlue());

        // ------------
        // Strict mode
        // ------------
        JPanel panelAdvancedMode = new JPanel();
        if (!release)
            panel.add(panelAdvancedMode);
        panelAdvancedMode.setLayout(new BoxLayout(panelAdvancedMode, BoxLayout.X_AXIS));

        JLabel lblAdvancedMode = new JLabel("Enable methods (javascript/beta)*");
        panelAdvancedMode.add(lblAdvancedMode);

        cboxAdvanced = new JCheckBox("");
        panelAdvancedMode.add(cboxAdvanced);
        panelAdvancedMode.add(Box.createHorizontalGlue());

        // ------------
        // Strict mode
        // ------------
        JPanel panelStrict = new JPanel();
        panelStrict.setLayout(new BoxLayout(panelStrict, BoxLayout.X_AXIS));
        if (!release)
            panel.add(panelStrict);

        JLabel lblStrict = new JLabel("Enable Strict Mode (javascript)");
        panelStrict.add(lblStrict);

        cboxStrict = new JCheckBox("");
        panelStrict.add(cboxStrict);

        panelStrict.add(Box.createHorizontalGlue());

        // ----------
        // Soft Tabs
        // ----------
        JPanel panelSoft = new JPanel();
        panelSoft.setLayout(new BoxLayout(panelSoft, BoxLayout.X_AXIS));
        panel.add(panelSoft);

        JLabel lblSoft = new JLabel("Soft tabs");
        panelSoft.add(lblSoft);

        cboxSoft = new JCheckBox("");
        panelSoft.add(cboxSoft);

        panelSoft.add(Box.createHorizontalGlue());

        // ---------------------
        // Spaces for Soft Tabs
        // ---------------------
        JPanel panelSpacesTab = new JPanel();
        panel.add(panelSpacesTab);
        panelSpacesTab.setLayout(new BoxLayout(panelSpacesTab, BoxLayout.X_AXIS));

        JLabel lblSpacesTab = new JLabel("Spaces count for soft tabs");
        panelSpacesTab.add(lblSpacesTab);

        tfSpacesTab = new IcyTextField();
        tfSpacesTab.setColumns(10);
        panelSpacesTab.add(tfSpacesTab);
        tfSpacesTab.setMaximumSize(new Dimension(60, 25));

        panelSpacesTab.add(Box.createHorizontalGlue());

        panel.add(Box.createVerticalGlue());

        JLabel lblNeedsRestarting = new JLabel("* needs restarting Script Editor");
        if (!release)
            panelCenter.add(lblNeedsRestarting, BorderLayout.SOUTH);

        loadPrefs();

        cboxSoft.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                tfSpacesTab.setEnabled(cboxSoft.isSelected());
            }
        });
        return toReturn;
    }

    public boolean isFullAutoCompleteEnabled()
    {
        return cboxFullAutocomplete.isSelected();
    }

    public boolean isVarInterpretationEnabled()
    {
        if (release)
            return false;
        return cboxVarInterp.isSelected();
    }

    public boolean isOverrideEnabled()
    {
        if (release)
            return true;
        return cboxOverride.isSelected();
    }

    public boolean isAutoBuildEnabled()
    {
        if (release)
            return false;
        return cboxAutoVerif.isSelected();
    }

    public boolean isStrictModeEnabled()
    {
        if (release)
            return false;
        return cboxStrict.isSelected();
    }

    public boolean isIndentSpacesEnabled()
    {
        return cboxSoft.isSelected();
    }

    public int indentSpacesCount()
    {
        try
        {
            return Integer.valueOf(tfSpacesTab.getText());
        }
        catch (NumberFormatException e)
        {
            // do nothing
        }
        return 8;
    }

    // public class PreferencesTableModel extends DefaultTableModel
    // {
    //
    // /**
    // *
    // */
    // private static final long serialVersionUID = 1L;
    // Class<?>[] columnTypes = new Class[] {String.class, Boolean.class};
    //
    // public PreferencesTableModel()
    // {
    // super(new Object[][] {
    // {"Enable variable interpretation (beta)*", prefs.getBoolean(PREF_VAR_INTERPRET,
    // Boolean.FALSE)},
    // {"Override verification (javascript)", prefs.getBoolean(PREF_OVERRIDE, Boolean.TRUE)},
    // {"Enable auto verification (javascript/beta)*", prefs.getBoolean(PREF_VERIF, Boolean.FALSE)},
    // {"Enable Strict Mode (javascript)", prefs.getBoolean(PREF_STRICT, Boolean.FALSE)},
    // {"Soft tabs", prefs.getBoolean(PREF_INDENT_SPACES, Boolean.TRUE)},
    // {"Spaces count for soft tabs", prefs.getInt(PREF_INDENT_SPACES_VALUE, 8)},}, new String[] {
    // "Property", "Value"});
    // }
    //
    // public PreferencesTableModel(boolean release)
    // {
    // super(new Object[][] {{"Indentation by spaces", prefs.getBoolean(PREF_INDENT_SPACES,
    // Boolean.FALSE)}},
    // new String[] {"Property", "Value"});
    // }
    //
    // @Override
    // public Class<?> getColumnClass(int columnIndex)
    // {
    // return columnTypes[columnIndex];
    // }
    //
    // @Override
    // public void setValueAt(Object aValue, int row, int column)
    // {
    // super.setValueAt(aValue, row, column);
    // savePrefs();
    // }
    // }

    public void savePrefs()
    {
        prefs.putBoolean(PREF_VAR_INTERPRET, isVarInterpretationEnabled());
        prefs.putBoolean(PREF_OVERRIDE, isOverrideEnabled());
        prefs.putBoolean(PREF_VERIF, isAutoBuildEnabled());
        prefs.putBoolean(PREF_STRICT, isStrictModeEnabled());
        prefs.putBoolean(PREF_INDENT_SPACES, isIndentSpacesEnabled());
        prefs.putBoolean(PREF_FULL_AUTOCOMPLETE, isFullAutoCompleteEnabled());
        prefs.putInt(PREF_INDENT_SPACES_VALUE, indentSpacesCount());
    }

    public void loadPrefs()
    {
        cboxVarInterp.setSelected(prefs.getBoolean(PREF_VAR_INTERPRET, Boolean.TRUE));
        cboxOverride.setSelected(prefs.getBoolean(PREF_OVERRIDE, Boolean.TRUE));
        cboxAutoVerif.setSelected(prefs.getBoolean(PREF_VERIF, Boolean.TRUE));
        cboxStrict.setSelected(prefs.getBoolean(PREF_STRICT, Boolean.FALSE));
        cboxFullAutocomplete.setSelected(prefs.getBoolean(PREF_FULL_AUTOCOMPLETE, Boolean.TRUE));

        boolean active = prefs.getBoolean(PREF_INDENT_SPACES, Boolean.FALSE);
        cboxSoft.setSelected(active);
        if (active)
            tfSpacesTab.setEnabled(true);
        tfSpacesTab.setValue("" + prefs.getInt(PREF_INDENT_SPACES_VALUE, 8));
    }

}

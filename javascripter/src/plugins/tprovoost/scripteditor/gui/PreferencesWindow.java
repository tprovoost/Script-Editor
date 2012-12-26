package plugins.tprovoost.scripteditor.gui;

import icy.gui.frame.IcyFrame;
import icy.preferences.IcyPreferences;
import icy.preferences.XMLPreferences;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class PreferencesWindow extends IcyFrame
{
    private static PreferencesWindow singleton = new PreferencesWindow();
    private JTable table;
    private XMLPreferences prefs = IcyPreferences.pluginsRoot().node("scripteditor");
    private PreferencesTableModel tableModel = new PreferencesTableModel();
    private final String PREF_VAR_INTERPRET = "varinterp";
    private final String PREF_OVERRIDE = "override";
    private final String PREF_VERIF = "autoverif";
    private final String PREF_STRICT = "strictmode";
    private final String PREF_NEW_ENGINE = "newengine";

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

	Component horizontalGlue = Box.createHorizontalGlue();
	panelButtons.add(horizontalGlue);

	JButton btnApply = new JButton("Apply");
	panelButtons.add(btnApply);

	Component horizontalStrut = Box.createHorizontalStrut(20);
	panelButtons.add(horizontalStrut);

	JButton btnOk = new JButton("OK");
	btnOk.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		close();
	    }
	});
	panelButtons.add(btnOk);

	Component horizontalStrut_1 = Box.createHorizontalStrut(20);
	panelButtons.add(horizontalStrut_1);

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

	table = new JTable();
	table.setShowGrid(false);
	table.setOpaque(false);
	table.setModel(tableModel);
	table.getColumnModel().getColumn(0).setPreferredWidth(190);
	panelCenter.setLayout(new BorderLayout(0, 0));
	panelCenter.add(table);
	
	JLabel lblNeedsRestarting = new JLabel("* needs restarting Script Editor");
	panelCenter.add(lblNeedsRestarting, BorderLayout.SOUTH);
	return toReturn;
    }

    public boolean isVarInterpretationEnabled()
    {
	return (Boolean) tableModel.getValueAt(0, 1);
    }

    public boolean isOverrideEnabled()
    {
	return (Boolean) tableModel.getValueAt(1, 1);
    }

    public boolean isAutoBuildEnabled()
    {
	return (Boolean) tableModel.getValueAt(2, 1);
    }

    public boolean isStrictModeEnabled()
    {
	return (Boolean) tableModel.getValueAt(3, 1);
    }

    public boolean isRunNewEngineEnabled()
    {
	return (Boolean) tableModel.getValueAt(4, 1);
    }

    public class PreferencesTableModel extends DefaultTableModel
    {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Class<?>[] columnTypes = new Class[]
	{ String.class, Boolean.class };

	public PreferencesTableModel()
	{
	    super(new Object[][]
	    {
	    { "Enable variable interpretation (beta)*", prefs.getBoolean(PREF_VAR_INTERPRET, Boolean.FALSE) },
	    { "Override verification (javascript)", prefs.getBoolean(PREF_OVERRIDE, Boolean.TRUE) },
	    { "Enable auto verification (javascript/beta)*", prefs.getBoolean(PREF_VERIF, Boolean.FALSE) },
	    { "Enable Strict Mode (javascript)", prefs.getBoolean(PREF_STRICT, Boolean.FALSE) },
	    { "Always run in a new Engine", prefs.getBoolean(PREF_NEW_ENGINE, Boolean.TRUE) }, }, new String[]
	    { "Property", "Value" });
	}

	public Class<?> getColumnClass(int columnIndex)
	{
	    return columnTypes[columnIndex];
	}

	@Override
	public void setValueAt(Object aValue, int row, int column)
	{
	    super.setValueAt(aValue, row, column);
	    savePrefs();
	}
    }

    public void savePrefs()
    {
	prefs.putBoolean(PREF_VAR_INTERPRET, isVarInterpretationEnabled());
	prefs.putBoolean(PREF_OVERRIDE, isOverrideEnabled());
	prefs.putBoolean(PREF_VERIF, isAutoBuildEnabled());
	prefs.putBoolean(PREF_STRICT, isStrictModeEnabled());
	prefs.putBoolean(PREF_NEW_ENGINE, isRunNewEngineEnabled());
    }
    
    public void loadPrefs() {
	
    }
}

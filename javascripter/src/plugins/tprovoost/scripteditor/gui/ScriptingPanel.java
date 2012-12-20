package plugins.tprovoost.scripteditor.gui;

import icy.file.FileUtil;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.plugin.PluginRepositoryLoader;
import icy.system.thread.ThreadUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.border.BevelBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

import plugins.tprovoost.scripteditor.completion.IcyAutoCompletion;
import plugins.tprovoost.scripteditor.completion.IcyCompletionCellRenderer;
import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scripteditor.completion.JSAutoCompletion;
import plugins.tprovoost.scripteditor.completion.PythonAutoCompletion;
import plugins.tprovoost.scripteditor.main.ScriptListener;
import plugins.tprovoost.scripteditor.main.scriptinghandlers.JSScriptingHandler6;
import plugins.tprovoost.scripteditor.main.scriptinghandlers.PythonScriptingHandler;
import plugins.tprovoost.scripteditor.main.scriptinghandlers.ScriptingHandler;
import plugins.tprovoost.scripteditor.scriptingconsole.BindingsScriptFrame;
import plugins.tprovoost.scripteditor.scriptingconsole.PythonScriptingconsole;
import plugins.tprovoost.scripteditor.scriptingconsole.Scriptingconsole;

public class ScriptingPanel extends JPanel implements CaretListener, ScriptListener
{
    /** */
    private static final long serialVersionUID = 1L;
    private ScriptingHandler scriptHandler;
    private RSyntaxTextArea textArea;
    private RTextScrollPane pane;
    private PanelOptions options;
    private String name;
    private String saveFileString = "";

    /** Default file used to save the content into */
    private File saveFile = null;

    /** Boolean used to know if the file was modified since the last save. */
    private JTabbedPane tabbedPane;

    /** Provider used for autocompletion. */
    private IcyCompletionProvider provider;
    private JScrollPane scrollpane;
    private JTextArea output;
    private Scriptingconsole console;

    /** Autocompletion system. Uses provider item. */
    private IcyAutoCompletion ac;
    public JButton btnRun;
    public JButton btnStop;

    /**
     * Creates a panel for scripting, using an {@link RSyntaxTextArea} for the
     * text and {@link Gutter} to display line numbers and errors. Error is
     * shown in the output window as a {@link JTextArea} in a
     * {@link JScrollPane}.
     * 
     * @param name
     */
    public ScriptingPanel(String name)
    {
	this.name = name;
	setLayout(new BorderLayout());

	output = new JTextArea(5, 40);
	output.setEditable(false);

	output.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
	scrollpane = new JScrollPane(output);
	scrollpane.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
	scrollpane.setAutoscrolls(true);
	scrollpane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener()
	{

	    @Override
	    public void adjustmentValueChanged(AdjustmentEvent e)
	    {
		if (!output.getText().isEmpty())
		    output.setCaretPosition(output.getText().length() - 1);
	    }
	});

	// creates the text area and set it up
	textArea = new RSyntaxTextArea(20, 60);
	textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
	textArea.setCodeFoldingEnabled(true);
	textArea.setAntiAliasingEnabled(true);
	textArea.setAutoIndentEnabled(true);
	textArea.setCloseCurlyBraces(true);
	textArea.setMarkOccurrences(true);
	textArea.addCaretListener(this);
	textArea.setCodeFoldingEnabled(true);
	textArea.setPaintMarkOccurrencesBorder(true);
	textArea.setPaintMatchedBracketPair(true);
	textArea.setPaintTabLines(true);

	pane = new RTextScrollPane(textArea);
	pane.setIconRowHeaderEnabled(true);

	// creates the options panel
	String ext = FileUtil.getFileExtension(name, false);
	if (ext.contentEquals("py"))
	    options = new PanelOptions("python");
	else
	    options = new PanelOptions();
	installLanguage(options.combo.getSelectedItem().toString());
	rebuildGUI();

	// set the default theme: eclipse.
	setTheme("eclipse");
	textArea.requestFocus();
    }

    public RSyntaxTextArea getTextArea()
    {
	return textArea;
    }

    public RTextScrollPane getPane()
    {
	return pane;
    }

    public String getPanelName()
    {
	return name;
    }

    public void setSyntax(String syntaxType)
    {
	textArea.setSyntaxEditingStyle(syntaxType);
    }

    /**
     * Install the wanted theme.
     * 
     * @param s
     */
    public void setTheme(String s)
    {
	try
	{
	    Theme t = Theme.load(getClass().getClassLoader().getResourceAsStream("plugins/tprovoost/scripteditor/themes/" + s + ".xml"));
	    t.apply(textArea);
	}
	catch (IOException e)
	{
	    System.out.println("Couldn't load theme");
	}
    }

    /**
     * Save the data into the file f.
     * 
     * @param f
     * @return
     */
    public boolean saveFileAs(File f)
    {
	if (f != null)
	{
	    BufferedWriter writer = null;
	    try
	    {
		String s = textArea.getText();
		writer = new BufferedWriter(new FileWriter(f));
		writer.write(s);
		writer.close();
		saveFile = f;
		saveFileString = s;
		name = f.getName();
		scriptHandler.setFileName(saveFileString);
		updateTitle();
		return true;
	    }
	    catch (IOException e)
	    {
		new FailedAnnounceFrame(e.getLocalizedMessage());
		return false;
	    }
	}
	return false;
    }

    /**
     * Save the file is dirty. Returns true if success or not dirty.
     * 
     * @return
     */
    public boolean saveFile()
    {
	if (!isDirty())
	    return true;
	if (saveFile != null)
	{
	    BufferedWriter writer = null;
	    try
	    {
		String s = textArea.getText();
		writer = new BufferedWriter(new FileWriter(saveFile));
		writer.write(s);
		writer.close();
		saveFileString = s;
		updateTitle();
		return true;
	    }
	    catch (IOException e)
	    {
		new FailedAnnounceFrame(e.getLocalizedMessage());
		return false;
	    }
	}
	return false;
    }

    private void updateTitle()
    {
	// if this panel is in a tabbed pane: updates its title.
	if (tabbedPane != null)
	{
	    int idx = tabbedPane.indexOfComponent(this);
	    if (idx != -1)
	    {
		if (isDirty())
		    tabbedPane.setTitleAt(idx, name + "*");
		else
		    tabbedPane.setTitleAt(idx, name);
		Component c = tabbedPane.getTabComponentAt(idx);
		if (c instanceof JComponent)
		    ((JComponent) c).revalidate();
		else
		    c.repaint();
	    }
	}
    }

    /**
     * Install the wanted language in the text area: creates the provider and
     * its default auto-complete words.
     * 
     * @param language
     *            : javascript / ruby / python / etc.
     */
    public synchronized void installLanguage(final String language)
    {
	output.setText("");

	// Autocompletion is done with the following item
	if (scriptHandler != null)
	{
	    scriptHandler.removeScriptListener(this);
	    textArea.removeKeyListener(scriptHandler);
	    PluginRepositoryLoader.removeListener(scriptHandler);
	}

	// the provider provides the results when hitting Ctrl + Space.
	if (provider == null)
	{
	    provider = new IcyCompletionProvider();
	    provider.setAutoActivationRules(false, ".");
	    ThreadUtil.invokeLater(new Runnable()
	    {

		@Override
		public void run()
		{
		    provider.setListCellRenderer(new IcyCompletionCellRenderer());
		}
	    });
	}
	provider.clear();

	if (ac != null)
	{
	    ac.uninstall();
	}

	// set the syntax
	if (language.contentEquals("javascript"))
	{
	    setSyntax(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
	    ac = new JSAutoCompletion(provider);
	}
	else if (language.contentEquals("python"))
	{
	    setSyntax(SyntaxConstants.SYNTAX_STYLE_PYTHON);
	    ac = new PythonAutoCompletion(provider);
	}
	else
	{
	    setSyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
	    new AnnounceFrame("This language is not yet supported.");
	    return;
	}

	// install the default completion words: eg. "for", "while", etc.
	provider.installDefaultCompletions(language);

	// install the text area with the completion system.
	ac.install(textArea);
	ac.setParameterAssistanceEnabled(true);
	ac.setAutoActivationEnabled(true);
	ac.setAutoActivationDelay(500);
	ac.setShowDescWindow(true);
	ThreadUtil.invokeLater(new Runnable()
	{
	    @Override
	    public void run()
	    {
		// the ScriptHandler in the Console is independant, so it needs
		// to have
		// if (language.contentEquals("python")) {
		// console = new PythonScriptingconsole();
		// } else {
		console = new Scriptingconsole();
		// }

		// set the language for the console too.
		console.setLanguage(language);

		// a reference to the output.
		console.setOutput(output);

		// add the scripting handler, which handles the compilation
		// and the parsing of the code for advanced features.
		if (language.contentEquals("javascript"))
		{
		    scriptHandler = new JSScriptingHandler6(provider, textArea, pane.getGutter(), true);
		    scriptHandler.setOutput(output);
		}
		else if (language.contentEquals("python"))
		{
		    scriptHandler = new PythonScriptingHandler(provider, textArea, pane.getGutter(), true);
		    scriptHandler.setOutput(output);
		}
		else
		{
		    scriptHandler = null;
		}
		if (scriptHandler != null)
		{
		    scriptHandler.addScriptListener(ScriptingPanel.this);
		    provider.setHandler(scriptHandler);
		    textArea.addKeyListener(scriptHandler);
		    PluginRepositoryLoader.addListener(scriptHandler);

		    BindingsScriptFrame frame = BindingsScriptFrame.getInstance();
		    frame.setEngine(scriptHandler.getEngine());
		}
		rebuildGUI();
		textArea.requestFocus();
	    }
	});
    }

    /**
     * Getter of the handler.
     * 
     * @return
     */
    public ScriptingHandler getScriptHandler()
    {
	return scriptHandler;
    }

    /**
     * Get the defaut save location.
     * 
     * @return
     */
    public File getSaveFile()
    {
	return saveFile;
    }

    /**
     * Returns if the file has been modified since its last save.
     * 
     * @return
     */
    public boolean isDirty()
    {
	String currentText = textArea.getText();
	return saveFile == null || !saveFileString.contentEquals(currentText);
    }

    /**
     * Rebuild the whole GUI.
     */
    private void rebuildGUI()
    {
	removeAll();
	JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pane, scrollpane);
	split.setDividerLocation(0.75d);
	split.setResizeWeight(0.75d);
	split.setOneTouchExpandable(true);
	add(split, BorderLayout.CENTER);
	add(options, BorderLayout.NORTH);
	add(console, BorderLayout.SOUTH);
	revalidate();
    }

    /**
     * This panel creates the tools needed to choose the language (for each
     * language) and run the code.
     * 
     * @author Thomas Provoost
     * 
     */
    class PanelOptions extends JPanel
    {

	/** */
	private static final long serialVersionUID = 1L;
	private JComboBox combo;

	public PanelOptions()
	{
	    this("javascript");
	}

	public PanelOptions(String string)
	{
	    final JButton btnBuild = new JButton("Verify");
	    btnRun = new JButton("Run");
	    btnStop = new JButton("Stop");
	    btnStop.setEnabled(false);

	    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
	    setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

	    add(new JLabel("Lang: "));
	    ArrayList<String> values = new ArrayList<String>();
	    ScriptEngineManager manager = new ScriptEngineManager();
	    for (ScriptEngineFactory factory : manager.getEngineFactories())
	    {
		values.add(getLanguageName(factory));
	    }
	    combo = new JComboBox(values.toArray());
	    combo.setSelectedItem(string);
	    combo.addItemListener(new ItemListener()
	    {

		@Override
		public void itemStateChanged(final ItemEvent e)
		{
		    ThreadUtil.bgRun(new Runnable()
		    {

			@Override
			public void run()
			{
			    if (e.getStateChange() == ItemEvent.SELECTED)
			    {
				String language = combo.getSelectedItem().toString();
				installLanguage(language);
			    }
			}
		    });

		}
	    });
	    add(combo);
	    add(Box.createHorizontalStrut(4));

	    btnBuild.addActionListener(new ActionListener()
	    {

		@Override
		public void actionPerformed(ActionEvent e)
		{
		    if (scriptHandler != null)
		    {
			scriptHandler.interpret(false);
		    }
		    else
			System.out.println("Script Handler null.");
		}
	    });
	    // add(btnBuild);

	    btnRun.addActionListener(new ActionListener()
	    {

		@Override
		public void actionPerformed(ActionEvent e)
		{
		    if (scriptHandler == null)
			return;
		    PreferencesWindow prefs = PreferencesWindow.getPreferencesWindow();
		    btnRun.setEnabled(false);
		    btnStop.setEnabled(true);
		    scriptHandler.setNewEngine(prefs.isRunNewEngineEnabled());
		    scriptHandler.setForceRun(prefs.isOverrideEnabled());
		    scriptHandler.setStrict(prefs.isStrictModeEnabled());
		    scriptHandler.setForceRun(prefs.isVarInterpretationEnabled());
		    scriptHandler.interpret(true);
		}
	    });
	    add(btnRun);

	    btnStop.addActionListener(new ActionListener()
	    {

		@Override
		public void actionPerformed(ActionEvent e)
		{
		    if (scriptHandler == null)
			return;
		    if (scriptHandler.isRunning())
		    {
			scriptHandler.killScript();
		    }
		}
	    });
	    add(Box.createHorizontalStrut(5));
	    add(btnStop);
	    add(Box.createHorizontalGlue());
	}

    }

    /**
     * Sets the text in the textArea.
     * 
     * @param text
     */
    public void setText(String text)
    {
	textArea.setText(text);
    }

    /**
     * Get the String language corresponding to the engine factory.<br/>
     * Ex: ECMAScript factory returns JavaScript.
     * 
     * @param factory
     * @return
     */
    public String getLanguageName(ScriptEngineFactory factory)
    {
	String languageName = factory.getLanguageName();
	if (languageName.contentEquals("ECMAScript"))
	    return "javascript";
	if (languageName.contentEquals("python"))
	    return "python";
	return languageName;
    }

    @Override
    public void caretUpdate(CaretEvent e)
    {
	updateTitle();
    }

    public void setTabbedPane(JTabbedPane tabbedPane)
    {
	this.tabbedPane = tabbedPane;
    }

    /**
     * Get the current selected language in the combobox.
     * 
     * @return
     */
    public String getLanguage()
    {
	return (String) options.combo.getSelectedItem();
    }

    /**
     * Load the content of the file into the textArea. Also updates the
     * {@link #saveFile()} and {@link #saveFileAs(File)} variables, used to know
     * if the text is dirty.
     * 
     * @param f
     *            : the file to load the code from.
     * @throws IOException
     *             : If the file could not be opened or read, an exception is
     *             raised.
     * @see {@link #isDirty()}
     */
    public void openFile(File f) throws IOException
    {
	BufferedReader reader = new BufferedReader(new FileReader(f));
	String all = "";
	String line;
	while ((line = reader.readLine()) != null)
	{
	    all += (line + "\n");
	}
	saveFile = f;
	saveFileString = all;
	textArea.setText(all);
	reader.close();
    }

    public void openStream(InputStream stream) throws IOException
    {
	byte[] data = new byte[stream.available()];
	stream.read(data);
	String s = "";
	for (byte b : data)
	    s += (char) b;
	textArea.setText(s);
	stream.close();
	ThreadUtil.bgRun(new Runnable()
	{

	    @Override
	    public void run()
	    {
		while (scriptHandler == null)
		    ThreadUtil.sleep(1000);
		scriptHandler.autoDownloadPlugins();
	    }
	});
    }

    @Override
    public void evaluationStarted()
    {
    }

    @Override
    public void evaluationOver()
    {
	btnRun.setEnabled(true);
	btnStop.setEnabled(false);
    }
}

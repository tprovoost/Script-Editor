package plugins.tprovoost.scripteditor.gui;

import icy.file.FileUtil;
import icy.file.Loader;
import icy.gui.component.button.IcyButton;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.image.ImageUtil;
import icy.main.Icy;
import icy.network.NetworkUtil;
import icy.plugin.PluginLoader;
import icy.plugin.PluginRepositoryLoader;
import icy.resource.icon.IcyIcon;
import icy.system.FileDrop;
import icy.system.thread.ThreadUtil;
import japa.parser.ast.body.ConstructorDeclaration;
import japa.parser.ast.body.MethodDeclaration;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.HyperlinkListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DescWindowCallback;
import org.fife.ui.autocomplete.ExternalURLHandler;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

import plugins.tprovoost.scripteditor.completion.IcyAutoCompletion;
import plugins.tprovoost.scripteditor.completion.IcyCompletionCellRenderer;
import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scripteditor.completion.IcyCompletionProviderPython;
import plugins.tprovoost.scripteditor.completion.JSAutoCompletion;
import plugins.tprovoost.scripteditor.completion.PythonAutoCompletion;
import plugins.tprovoost.scripteditor.completion.types.BasicJavaClassCompletion;
import plugins.tprovoost.scripteditor.completion.types.NewInstanceCompletion;
import plugins.tprovoost.scripteditor.completion.types.ScriptFunctionCompletion;
import plugins.tprovoost.scripteditor.gui.action.SplitButtonActionListener;
import plugins.tprovoost.scripteditor.javasource.ClassSource;
import plugins.tprovoost.scripteditor.javasource.JarAccess;
import plugins.tprovoost.scripteditor.main.ScriptListener;
import plugins.tprovoost.scripteditor.scriptblock.Javascript;
import plugins.tprovoost.scripteditor.scriptingconsole.BindingsScriptFrame;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngineHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptingHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.js.JSScriptingHandlerRhino;
import plugins.tprovoost.scripteditor.scriptinghandlers.py.PythonScriptingHandler;

// import plugins.tprovoost.scripteditor.main.scriptinghandlers.JSScriptingHandler7;

public class ScriptingPanel extends JPanel implements CaretListener, ScriptListener, HyperlinkListener
{
	/** */
	private static final long serialVersionUID = 1L;

	public static final BufferedImage imgPlayback2 = ImageUtil.load(PluginLoader
			.getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/playback_erase_play_alpha.png"));

	public static final int STRUT_SIZE = 4;

	private ScriptingHandler scriptHandler;
	private RSyntaxTextArea textArea;
	private RTextScrollPane pane;
	private PanelOptions options;
	private String panelName;
	private String saveFileString = "";

	/** Default file used to save the content into */
	private File saveFile = null;

	/** Provider used for auto-completion. */
	private IcyCompletionProvider provider;
	/** Auto-completion system. Uses provider item. */
	private IcyAutoCompletion ac;

	public JMenuItem btnRun;
	private JSplitButton btnSplitRun;
	public JButton btnStop;
	private ScriptingEditor editor;
	private boolean integrated;
	
	/**
	 * This listener is called when there are changes applied in the preferences window.
	 * It applies the preferences to the opened ScriptingPanels.
	 */
	private PreferencesListener applyPrefsListener = new PreferencesListener() {
		
		@Override
		public void preferencesChanged() {
			Preferences preferences = Preferences.getPreferences();

			textArea.setTabsEmulated(preferences.isSoftTabsEnabled());
			textArea.setTabSize(preferences.indentSpacesCount());
		}
	};

	public ScriptingPanel(ScriptingEditor editor, String name, String language)
	{
		this(editor, name, language, false);
	}

	/**
	 * Creates a panel for scripting, using an {@link RSyntaxTextArea} for the
	 * text and {@link Gutter} to display line numbers and errors. Error is
	 * shown in the output window as a {@link JTextArea} in a
	 * {@link JScrollPane}.
	 * 
	 * @param name
	 */
	public ScriptingPanel(ScriptingEditor editor, String name, String language, boolean integrated)
	{
		this.panelName = name;
		this.editor = editor;
		this.integrated = integrated;
		setLayout(new BorderLayout());

		// creates the text area and set it up
		textArea = new RSyntaxTextArea(20, 60);
		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
		// textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
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
		textArea.setTabsEmulated(false);
		((RSyntaxTextArea) textArea).addHyperlinkListener(this);
		new FileDrop(textArea, new FileDrop.FileDropListener()
		{

			@Override
			public void filesDropped(File[] files)
			{
				for (File f : files)
				{
					if (f.getName().endsWith(".js") || f.getName().endsWith(".py"))
					{
						try
						{
							ScriptingPanel.this.editor.openFile(f);
						} catch (IOException e)
						{
						}
					} else
					{
						Loader.load(f, true);
					}
				}
			}
		});
		textArea.addHyperlinkListener(new HyperlinkListener()
		{

			@Override
			public void hyperlinkUpdate(HyperlinkEvent e)
			{
				NetworkUtil.openBrowser(e.getURL());
			}

		});

		pane = new RTextScrollPane(textArea);
		pane.setIconRowHeaderEnabled(true);

		// creates the options panel
		options = new PanelOptions(language);
		installLanguage(options.comboLanguages.getSelectedItem().toString());

		// set the default theme: eclipse.
		setTheme("eclipse");
		textArea.requestFocus();
		
		// load the preferences
		applyPrefsListener.preferencesChanged();
		
		// frame listener on preferences
		PreferencesWindow.getPreferencesWindow().addPreferencesListener(applyPrefsListener);
	}

	/**
	 * Getter for the provider
	 * 
	 * @return
	 */
	public IcyCompletionProvider getProvider()
	{
		return provider;
	}

	/**
	 * Setter for the provider.
	 * 
	 * @param provider
	 */
	public void setProvider(IcyCompletionProvider provider)
	{
		this.provider = provider;
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
		return panelName;
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
			Theme t = Theme.load(PluginLoader.getLoader().getResourceAsStream("plugins/tprovoost/scripteditor/resources/themes/" + s + ".xml"));
			t.apply(textArea);
		} catch (IOException e)
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
				panelName = f.getName();
				scriptHandler.setFileName(f.getAbsolutePath());
				updateTitle();
				if (editor != null)
					editor.addRecentFile(f);
				return true;
			} catch (IOException e)
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
			} catch (IOException e)
			{
				new FailedAnnounceFrame(e.getLocalizedMessage());
				return false;
			}
		}
		return false;
	}

	/**
	 * Displays a JFileChoose and let the user choose its file, then open it.
	 * 
	 * @see ScriptingEditor#openFile(File)
	 */
	public boolean showSaveFileDialog(String currentDirectoryPath)
	{
		return showSaveFileDialog(currentDirectoryPath, panelName);
	}

	/**
	 * Displays a JFileChoose and let the user choose its file, then open it.
	 * 
	 * @see ScriptingEditor#openFile(File)
	 */
	public boolean showSaveFileDialog(String currentDirectoryPath, String defaultName)
	{
		final JFileChooser fc;
		if (currentDirectoryPath == "")
			fc = new JFileChooser();
		else
			fc = new JFileChooser(currentDirectoryPath);
		if (getLanguage().contentEquals("JavaScript"))
		{
			fc.setFileFilter(new FileNameExtensionFilter("Javascript files", "js"));
		} else if (getLanguage().contentEquals("Python"))
		{
			fc.setFileFilter(new FileNameExtensionFilter("Python files", "py"));
		}
		fc.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				fc.accept(fc.getSelectedFile());
				fc.removeKeyListener(this);
			}
		});
		if (!defaultName.isEmpty())
			fc.setSelectedFile(new File(defaultName));
		if (fc.showSaveDialog(Icy.getMainInterface().getMainFrame()) == JFileChooser.APPROVE_OPTION)
		{
			File file = fc.getSelectedFile();
			if (FileUtil.getFileExtension(file.getAbsolutePath(), false).isEmpty())
			{
				file = addExtension(file);
			}
			return saveFileAs(file);
		}
		return false;
	}

	private File addExtension(File file)
	{
		if (getLanguage().contentEquals("JavaScript"))
		{
			return new File(file.getAbsolutePath() + ".js");
		} else if (getLanguage().contentEquals("Python"))
		{
			return new File(file.getAbsolutePath() + ".py");
		}
		return file;
	}

	private void updateTitle()
	{
		// if this panel is in a tabbed pane: updates its title.
		if (editor != null && editor.getTabbedPane() != null)
		{
			int idx = editor.getTabbedPane().indexOfComponent(this);
			if (idx != -1)
			{
				if (isDirty())
					editor.getTabbedPane().setTitleAt(idx, panelName + "*");
				else
					editor.getTabbedPane().setTitleAt(idx, panelName);
				Component c = editor.getTabbedPane().getTabComponentAt(idx);
				if (c instanceof JComponent)
					((JComponent) c).revalidate();
				else
					c.repaint();
			}
		}
	}
	
	/**
	 * Ask the user whether to save the files.
	 * 
	 * @return false is the close operation is cancelled
	 */
	boolean promptSave()
	{
        int n = JOptionPane.showOptionDialog(Icy.getMainInterface().getMainFrame(),
                "Some work has not been saved, are you sure you want to close?", getPanelName(),
                JOptionPane.WARNING_MESSAGE, JOptionPane.QUESTION_MESSAGE, null, new Object[] {"Save",
                        "Discard Changes", "Cancel"}, "Cancel" + "");
        if (n == 2)
        	return false;
        if (n == 0)
        {
            if (getSaveFile() != null)
            	return saveFile();
            else
            	return showSaveFileDialog(editor.getCurrentDirectory());
        }
        
        return true;
	}
	
	/**
	 * Try to close this ScriptingPanel. The user will be asked whether to save the files
	 * if they are modified. If the operation is not cancelled, the listeners are removed.
	 * 
	 * @return false is the close operation is cancelled
	 */
	boolean close()
	{
		boolean canClose = true;
        if (isDirty())
        {
        	canClose = promptSave();
        }

        if (canClose)
        {
        	cleanup();
    		PreferencesWindow.getPreferencesWindow().removePreferencesListener(applyPrefsListener);
        	return true;
        }
        else
        {
        	return false;
        }
	}
	
	/**
	 * Removes the script listeners, release completion objects...
	 */
	void cleanup()
	{
		// if (editor != null && editor.getConsoleOutput() != null)
		// editor.getConsoleOutput().setText("");
		
		// Autocompletion is done with the following item
		if (scriptHandler != null)
		{
			scriptHandler.stopThreads();
			scriptHandler.removeScriptListener(this);
			textArea.removeKeyListener(scriptHandler);
			PluginRepositoryLoader.removeListener(scriptHandler);
		}
		
		if (ac != null)
		{
			ac.uninstall();
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
		final Preferences preferences = Preferences.getPreferences();

		cleanup();

		// the provider provides the results when hitting Ctrl + Space.
		if (provider == null)
		{
			if (language.contentEquals("Python"))
			{
				provider = new IcyCompletionProviderPython();
			} else
			{
				provider = new IcyCompletionProvider();
			}
			boolean autoActivate = preferences.isFullAutoCompleteEnabled();
			provider.setAutoActivationRules(autoActivate, ".");
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

		// set the syntax
		if (language.contentEquals("JavaScript"))
		{
			// setSyntax(SyntaxConstants.SYNTAX_STYLE_JAVA);
			setSyntax(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
			ac = new JSAutoCompletion(provider);
		} else if (language.contentEquals("Python"))
		{
			setSyntax(SyntaxConstants.SYNTAX_STYLE_PYTHON);
			ac = new PythonAutoCompletion(provider);
		} else
		{
			setSyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
			new AnnounceFrame("This language is not yet supported.");
			ThreadUtil.invokeLater(new Runnable()
			{

				@Override
				public void run()
				{
					btnRun.setEnabled(false);
					btnSplitRun.setEnabled(false);
				}
			});
			return;
		}

		// install the default completion words: eg. "for", "while", etc.
		provider.installDefaultCompletions(language);

		// install the text area with the completion system.
		ac.install(textArea);
		ac.setAutoCompleteSingleChoices(false);
		ac.setParameterAssistanceEnabled(true);
		ac.setAutoActivationEnabled(true);
		ac.setAutoActivationDelay(500);
		ac.setShowDescWindow(true);
		ac.setExternalURLHandler(new ExternalURLHandler()
		{
			@Override
			public void urlClicked(HyperlinkEvent e, Completion c, DescWindowCallback callback)
			{
				if (e.getDescription().contentEquals("SourceCodeLink"))
				{
					Class<?> clazz = null;
					if (c instanceof BasicJavaClassCompletion)
					{
						clazz = ((BasicJavaClassCompletion) c).getJavaClass();
						openSource(clazz);
					} else if (c instanceof ScriptFunctionCompletion)
					{
						ScriptFunctionCompletion sfc = (ScriptFunctionCompletion) c;
						Method m = sfc.getMethod();
						clazz = m.getDeclaringClass();
						final ClassSource cs = ClassSource.getClassSource(clazz);
						MethodDeclaration md = cs.getMethods().get(m.toGenericString());
						openSource(clazz, md.getBeginLine() - 1, md.getEndLine() - 1);
					} else if (c instanceof NewInstanceCompletion)
					{
						Constructor<?> cons = ((NewInstanceCompletion) c).getConstructor();
						clazz = cons.getDeclaringClass();
						final ClassSource cs = ClassSource.getClassSource(clazz);
						ConstructorDeclaration cd = cs.getConstructors().get(cons.toGenericString());
						if (cd != null)
							openSource(clazz, cd.getBeginLine() - 1, cd.getEndLine() - 1);
						else
							System.out.println(clazz);
					}
				} else
				{
					System.out.println("Click:" + e.getDescription());
					// callback.showSummaryFor(new BasicJavaCl, "");
				}
			}
		});
		ThreadUtil.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				if (editor != null)
					editor.changeConsoleLanguage(language);

				// add the scripting handler, which handles the compilation
				// and the parsing of the code for advanced features.
				if (language.contentEquals("JavaScript"))
				{
					// if
					// (System.getProperty("java.version").startsWith("1.6.")) {
					scriptHandler = new JSScriptingHandlerRhino(provider, textArea, pane.getGutter(), true);
					// } else {
					// scriptHandler = new JSScriptingHandlerSimple(provider,
					// textArea,
					// pane.getGutter(), true);
					// }
					if (!integrated && editor != null)
						scriptHandler.setOutput(editor.getConsoleOutput());

				} else if (language.contentEquals("Python"))
				{
					scriptHandler = new PythonScriptingHandler(provider, textArea, pane.getGutter(), true);
					if (!integrated)
						scriptHandler.setOutput(editor.getConsoleOutput());
				} else
				{
					scriptHandler = null;
				}
				if (scriptHandler != null)
				{
					scriptHandler.addScriptListener(ScriptingPanel.this);
					scriptHandler.setVarInterpretation(preferences.isVarInterpretationEnabled());
					scriptHandler.setStrict(preferences.isStrictModeEnabled());
					scriptHandler.setForceRun(preferences.isOverrideEnabled());
					// scriptHandler.interpret(false);
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

	public void openSource(Class<?> clazz)
	{
		openSource(clazz, 0, 0);
	}

	public void openSource(Class<?> clazz, int lineBegin, int lineEnd)
	{
		InputStream jar = JarAccess.getJavaSourceInputStream(clazz);
		if (jar != null)
		{
			String res = null;
			try
			{
				byte b[] = new byte[jar.available()];
				jar.read(b);
				res = new String(b);
			} catch (IOException e1)
			{
			}
			if (res == null)
				return;

			// creates the dialog
			IcyFrame frame = new IcyFrame("Source code of: [" + clazz.getName() + "]", true, true, true, true);
			JPanel panel = new JPanel(new BorderLayout());

			// Generation of the RSyntaxTextArea
			final RSyntaxTextArea sourceTextArea = new RSyntaxTextArea(200, 200);
			sourceTextArea.setText(res);
			// sourceTextArea.setEditable(false);
			sourceTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
			sourceTextArea.setCodeFoldingEnabled(true);
			sourceTextArea.setAntiAliasingEnabled(true);
			sourceTextArea.setAutoIndentEnabled(true);
			sourceTextArea.setCloseCurlyBraces(true);
			sourceTextArea.setMarkOccurrences(true);
			sourceTextArea.setCodeFoldingEnabled(true);
			sourceTextArea.setPaintMarkOccurrencesBorder(true);
			sourceTextArea.setPaintMatchedBracketPair(true);
			sourceTextArea.setPaintTabLines(true);
			sourceTextArea.setTabsEmulated(false);

			// Change the theme to Eclipse
			try
			{
				Theme t = Theme.load(PluginLoader.getLoader().getResourceAsStream("plugins/tprovoost/scripteditor/resources/themes/eclipse.xml"));
				t.apply(sourceTextArea);
			} catch (IOException e2)
			{
			}

			// Add the RSyntaxTextArea to a scroll pane
			RTextScrollPane paneSource = new RTextScrollPane(sourceTextArea);
			panel.add(paneSource);
			frame.setContentPane(panel);
			frame.setSize(720, 640);
			frame.addToMainDesktopPane();
			frame.setVisible(true);

			// Put the cursor at the right place
			int posCaretBegin = 0;
			int posCaretEnd = 0;
			try
			{
				posCaretBegin = sourceTextArea.getLineStartOffset(lineBegin);
				posCaretEnd = sourceTextArea.getLineStartOffset(lineEnd);
			} catch (BadLocationException e)
			{
			}
			sourceTextArea.getCaret().setDot(posCaretEnd);
			final int begin = posCaretBegin;
			ThreadUtil.bgRun(new Runnable()
			{

				@Override
				public void run()
				{
					ThreadUtil.sleep(100);
					sourceTextArea.getCaret().setDot(begin);
				}
			});
		}
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
	 * Setter for the handler. Should only be used for reference to the same
	 * script handler in different panels. For instance with the
	 * {@link Javascript} block.
	 * 
	 * @param scriptHandler
	 */
	public void setScriptHandler(ScriptingHandler scriptHandler)
	{
		this.scriptHandler = scriptHandler;
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
		return (saveFile == null && !currentText.isEmpty()) || !saveFileString.contentEquals(currentText);
	}

	/**
	 * Rebuild the whole GUI.
	 */
	private void rebuildGUI()
	{
		removeAll();
		add(pane);

		if (editor != null)
			add(options, BorderLayout.NORTH);
		revalidate();
	}

	/**
	 * This panel creates the tools needed to choose the language (for each
	 * language) and run the code.
	 * 
	 * @author Thomas Provoost
	 */
	class PanelOptions extends JPanel
	{

		/** */
		private static final long serialVersionUID = 1L;
		private JComboBox comboLanguages;

		private ActionListener runInNewListener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (!lastIsNew)
				{
					lastIsNew = true;
					btnSplitRun.setIcon(new IcyIcon("playback_play", 16));
					btnSplitRun.setToolTipText("Creates a new context and run the script. The previous context will be lost.");
					btnSplitRun.repaint();
				}
				runInNew();
			}
		};

		private ActionListener runInSameListener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (lastIsNew)
				{
					lastIsNew = false;
					btnSplitRun.setIcon(new IcyIcon(imgPlayback2, 16));
					btnSplitRun.setToolTipText("All variables in the bindings are re-usable.");
					btnSplitRun.repaint();
				}
				runInSame();
			}
		};
		protected boolean lastIsNew = true;

		public PanelOptions()
		{
			this("JavaScript");
		}

		public PanelOptions(String language)
		{
			// final JButton btnBuild = new JButton("Verify");
			btnRun = new JMenuItem("Run in Current Context", new IcyIcon(imgPlayback2, 16));
			btnRun.setToolTipText("All variables in the bindings are re-usable.");

			btnSplitRun = new JSplitButton("  ", new IcyIcon("playback_play", 16));
			btnSplitRun.setPreferredSize(new Dimension(45, 20));
			btnSplitRun.setToolTipText("Creates a new context and run the script. The previous context will be lost.");

			JMenuItem btnRunNew2 = new JMenuItem("Run in New Context", new IcyIcon("playback_play", 16));
			btnRunNew2.setToolTipText("Creates a new context and run the script. The previous context and its bindings will be lost.");

			JPopupMenu popupRun = new JPopupMenu();
			popupRun.add(btnRunNew2);
			popupRun.add(btnRun);
			btnSplitRun.setPopupMenu(popupRun);

			btnStop = new IcyButton(new IcyIcon("square_shape", 16));
			btnStop.setToolTipText("Stops the current script.");
			btnStop.setEnabled(false);

			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

			add(new JLabel("Lang: "));
			ArrayList<String> values = new ArrayList<String>();
			ScriptEngineManager manager = new ScriptEngineManager(PluginLoader.getLoader());
			for (ScriptEngineFactory factory : manager.getEngineFactories())
			{
				values.add(ScriptEngineHandler.getLanguageName(factory));
			}
			comboLanguages = new JComboBox(values.toArray());
			comboLanguages.setSelectedItem(language);
			comboLanguages.addItemListener(new ItemListener()
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
								String language = comboLanguages.getSelectedItem().toString();
								installLanguage(language);
							}
						}
					});

				}
			});
			add(comboLanguages);

			if (integrated)
				return;

			btnRun.addActionListener(runInSameListener);

			btnSplitRun.addSplitButtonActionListener(new SplitButtonActionListener()
			{

				@Override
				public void splitButtonClicked(ActionEvent e)
				{
				}

				@Override
				public void buttonClicked(ActionEvent e)
				{
					if (lastIsNew)
						runInNew();
					else
						runInSame();
				}
			});
			btnRunNew2.addActionListener(runInNewListener);

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

			add(Box.createHorizontalStrut(STRUT_SIZE * 3));
			add(btnSplitRun);
			add(Box.createHorizontalStrut(STRUT_SIZE));
			add(btnStop);
			add(Box.createHorizontalGlue());
		}

		protected void runInNew()
		{
			if (scriptHandler == null)
				return;
			if (!integrated)
			{
				if (isDirty())
				{
					if (saveFile != null)
					{
						saveFile();
					}
				}
			}

			Preferences preferences = Preferences.getPreferences();
			
			ThreadUtil.invokeLater(new Runnable()
			{

				@Override
				public void run()
				{
					btnRun.setEnabled(false);
					btnSplitRun.setEnabled(false);
					btnStop.setEnabled(true);
				}
			});
			// consoleOutput.setText("");
			scriptHandler.setNewEngine(true);
			scriptHandler.setForceRun(preferences.isOverrideEnabled());
			scriptHandler.setStrict(preferences.isStrictModeEnabled());
			scriptHandler.setVarInterpretation(preferences.isVarInterpretationEnabled());
			scriptHandler.interpret(true);
		}

		protected void runInSame()
		{
			if (scriptHandler == null)
				return;
			if (!integrated)
			{
				if (isDirty())
				{
					if (saveFile != null)
					{
						saveFile();
					}
				}
			}
			
			Preferences preferences = Preferences.getPreferences();
			
			ThreadUtil.invokeLater(new Runnable()
			{

				@Override
				public void run()
				{
					btnRun.setEnabled(false);
					btnSplitRun.setEnabled(false);
					btnStop.setEnabled(true);
				}
			});
			if (!integrated)
			{
				scriptHandler.setNewEngine(false);
				scriptHandler.setForceRun(preferences.isOverrideEnabled());
				scriptHandler.setStrict(preferences.isStrictModeEnabled());
				scriptHandler.setVarInterpretation(preferences.isVarInterpretationEnabled());
				scriptHandler.interpret(true);
			}
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

	@Override
	public void caretUpdate(CaretEvent e)
	{
		updateTitle();
	}

	/**
	 * Get the current selected language in the combobox.
	 * 
	 * @return
	 */
	public String getLanguage()
	{
		return (String) options.comboLanguages.getSelectedItem();
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
		scriptHandler.setFileName(f.getAbsolutePath());
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
		ThreadUtil.invokeLater(new Runnable()
		{

			@Override
			public void run()
			{
				btnRun.setEnabled(true);
				btnSplitRun.setEnabled(true);
				btnStop.setEnabled(false);
			}
		});
	}

	/**
	 * Displays a modal dialog to go to a specific line.
	 */
	public void displayGotoLine()
	{
		int min = 1;
		int max = textArea.getLineCount();
		String res = JOptionPane.showInputDialog(Icy.getMainInterface().getMainFrame(), "Enter line number (" + min + "," + max + ")", "Go to Line",
				JOptionPane.QUESTION_MESSAGE);
		try
		{
			int line = Integer.parseInt(res);
			textArea.setCaretPosition(textArea.getLineStartOffset(line - 1));
		} catch (NumberFormatException e)
		{
		} catch (BadLocationException e)
		{
		}
	}

	@Override
	public void hyperlinkUpdate(HyperlinkEvent e)
	{
		if (e.getEventType() == EventType.ACTIVATED)
		{
			URL url = e.getURL();
			String res = url == null ? e.getDescription() : url.getFile();
			try
			{
				editor.openFile(new File(res));
			} catch (IOException e1)
			{
			}
		}
	}

	/**
	 * Format the text in this panel.
	 * Do a space/tab conversion according to the preferences, and
	 * perform a language-specific beautification.
	 * 
	 */
	public void format() {
		// getScriptHandler().organizeImports();
		
		// do the tab-to-space (or space-to-tab) conversion
		Preferences preferences = Preferences.getPreferences();
		RSyntaxTextArea textArea = getTextArea();
		if (preferences.isSoftTabsEnabled())
		{
			textArea.convertTabsToSpaces();
		} else
		{
			textArea.convertSpacesToTabs();
		}
		
		// do a language-specific formatting
		getScriptHandler().format();
	}

}

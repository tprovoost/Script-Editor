package plugins.tprovoost.scripteditor.gui;

import icy.file.FileUtil;
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
import icy.util.EventUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
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
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.border.BevelBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

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
import plugins.tprovoost.scripteditor.completion.JSAutoCompletion;
import plugins.tprovoost.scripteditor.completion.PythonAutoCompletion;
import plugins.tprovoost.scripteditor.completion.types.BasicJavaClassCompletion;
import plugins.tprovoost.scripteditor.javasource.JarAccess;
import plugins.tprovoost.scripteditor.main.ScriptListener;
import plugins.tprovoost.scripteditor.scriptingconsole.BindingsScriptFrame;
import plugins.tprovoost.scripteditor.scriptingconsole.PythonScriptingconsole;
import plugins.tprovoost.scripteditor.scriptingconsole.Scriptingconsole;
import plugins.tprovoost.scripteditor.scriptinghandlers.JSScriptingHandlerRhino;
import plugins.tprovoost.scripteditor.scriptinghandlers.PythonScriptingHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngineHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptingHandler;

// import plugins.tprovoost.scripteditor.main.scriptinghandlers.JSScriptingHandler7;

public class ScriptingPanel extends JPanel implements CaretListener, ScriptListener, ActionListener
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

    /** Boolean used to know if the file was modified since the last save. */
    private JTabbedPane tabbedPane;

    /** Provider used for auto-completion. */
    private IcyCompletionProvider provider;
    /** Auto-completion system. Uses provider item. */
    private IcyAutoCompletion ac;

    private JScrollPane scrollpane;
    private JTextPane consoleOutput;
    private Scriptingconsole console;
    private JButton btnClearConsole;

    public JButton btnRun;
    private JButton btnRunNew;
    public JButton btnStop;
    private ScriptingEditor editor;
    private boolean integrated;

    protected boolean scrollLocked;

    public ScriptingPanel(ScriptingEditor editor, String name, String language)
    {
        this(editor, name, language, false);
    }

    /**
     * Creates a panel for scripting, using an {@link RSyntaxTextArea} for the
     * text and {@link Gutter} to display line numbers and errors. Error is
     * shown in the output window as a {@link JTextArea} in a {@link JScrollPane}.
     * 
     * @param name
     */
    public ScriptingPanel(ScriptingEditor editor, String name, String language, boolean integrated)
    {
        this.panelName = name;
        this.editor = editor;
        this.integrated = integrated;
        setLayout(new BorderLayout());

        consoleOutput = new JTextPane();
        consoleOutput.setPreferredSize(new Dimension(400, 200));
        consoleOutput.setEditable(false);
        consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleOutput.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

        // HANDLE RIGHT CLICK POPUP MENU
        consoleOutput.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (EventUtil.isRightMouseButton(e))
                {
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem itemCopy = new JMenuItem("Copy");
                    itemCopy.addActionListener(new ActionListener()
                    {

                        @Override
                        public void actionPerformed(ActionEvent e)
                        {
                            consoleOutput.copy();
                        }
                    });
                    popup.add(itemCopy);
                    popup.show(consoleOutput, e.getX(), e.getY());
                    e.consume();
                }
            }
        });

        // Create the scrollpane around the output
        scrollpane = new JScrollPane(consoleOutput);
        scrollpane.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        scrollpane.setAutoscrolls(true);
        final JScrollBar scrollbar = scrollpane.getVerticalScrollBar();

        // LISTENER ON THE SCROLLBAR FOR SCROLL LOCK
        scrollbar.addAdjustmentListener(new AdjustmentListener()
        {

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e)
            {
                if (scrollbar.getValueIsAdjusting())
                {
                    if (scrollbar.getValue() + scrollbar.getVisibleAmount() == scrollbar.getMaximum())
                        setScrollLocked(false);
                    else
                        setScrollLocked(true);
                }
                if (!isScrollLocked() && !consoleOutput.getText().isEmpty())
                {
                    Document doc = consoleOutput.getDocument();
                    consoleOutput.setCaretPosition(doc.getLength() - 1);
                }
            }

        });
        scrollpane.addMouseWheelListener(new MouseWheelListener()
        {

            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                if (scrollbar.getValue() + scrollbar.getVisibleAmount() == scrollbar.getMaximum())
                    setScrollLocked(false);
                else
                    setScrollLocked(true);
            }
        });
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
        new FileDrop(textArea, new FileDrop.FileDropListener()
        {

            @Override
            public void filesDropped(File[] files)
            {
                for (File f : files)
                    try
                    {
                        ScriptingPanel.this.editor.openFile(f);
                    }
                    catch (IOException e)
                    {
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

    private synchronized boolean isScrollLocked()
    {
        return scrollLocked;
    }

    private synchronized void setScrollLocked(boolean scrollLocked)
    {
        this.scrollLocked = scrollLocked;
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
            Theme t = Theme.load(PluginLoader.getLoader().getResourceAsStream(
                    "plugins/tprovoost/scripteditor/resources/themes/" + s + ".xml"));
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
                panelName = f.getName();
                scriptHandler.setFileName(saveFileString);
                updateTitle();
                if (editor != null)
                    editor.addRecentFile(f);
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

    /**
     * Displays a JFileChoose and let the user choose its file, then open it.
     * 
     * @see ScriptingEditor#openFile(File)
     */
    public boolean showSaveFileDialog(String currentDirectoryPath)
    {
        final JFileChooser fc;
        if (currentDirectoryPath == "")
            fc = new JFileChooser();
        else
            fc = new JFileChooser(currentDirectoryPath);
        if (getLanguage().contentEquals("JavaScript"))
        {
            fc.setFileFilter(new FileNameExtensionFilter("Javascript files", "js"));
        }
        else if (getLanguage().contentEquals("Python"))
        {
            fc.setFileFilter(new FileNameExtensionFilter("Python files", "py"));
        }
        fc.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                fc.accept(fc.getSelectedFile());
            }
        });
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
        }
        else if (getLanguage().contentEquals("Python"))
        {
            return new File(file.getAbsolutePath() + ".py");
        }
        return file;
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
                    tabbedPane.setTitleAt(idx, panelName + "*");
                else
                    tabbedPane.setTitleAt(idx, panelName);
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
     *        : javascript / ruby / python / etc.
     */
    public synchronized void installLanguage(final String language)
    {
        consoleOutput.setText("");

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
        if (language.contentEquals("JavaScript"))
        {
            // setSyntax(SyntaxConstants.SYNTAX_STYLE_JAVA);
            setSyntax(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
            ac = new JSAutoCompletion(provider);
        }
        else if (language.contentEquals("Python"))
        {
            setSyntax(SyntaxConstants.SYNTAX_STYLE_PYTHON);
            ac = new PythonAutoCompletion(provider);
        }
        else
        {
            setSyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
            new AnnounceFrame("This language is not yet supported.");
            ThreadUtil.invokeLater(new Runnable()
            {

                @Override
                public void run()
                {
                    btnRun.setEnabled(false);
                    btnRunNew.setEnabled(false);
                }
            });
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
                    }

                    if (clazz != null)
                    {
                        openSource(clazz);
                    }
                }
                else
                {
                    // callback.showSummaryFor(new BasicJavaCl, "");
                }
            }
        });
        ThreadUtil.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                // the ScriptHandler in the Console is independant, so it needs
                // to have
                if (language.contentEquals("Python"))
                {
                    console = new PythonScriptingconsole();
                }
                else
                {
                    console = new Scriptingconsole();
                }

                // set the language for the console too.
                console.setLanguage(language);

                // a reference to the output.
                console.setOutput(consoleOutput);

                console.setFont(consoleOutput.getFont());

                // add the scripting handler, which handles the compilation
                // and the parsing of the code for advanced features.
                if (language.contentEquals("JavaScript"))
                {
                    // if
                    // (System.getProperty("java.version").startsWith("1.6.")) {
                    scriptHandler = new JSScriptingHandlerRhino(provider, textArea, pane.getGutter(), true);
                    // } else {
                    // scriptHandler = new JSScriptingHandlerSimple(provider, textArea,
                    // pane.getGutter(), true);
                    // }
                    if (!integrated)
                        scriptHandler.setOutput(consoleOutput);

                }
                else if (language.contentEquals("Python"))
                {
                    scriptHandler = new PythonScriptingHandler(provider, textArea, pane.getGutter(), true);
                    if (!integrated)
                        scriptHandler.setOutput(consoleOutput);
                }
                else
                {
                    scriptHandler = null;
                }
                if (scriptHandler != null)
                {
                    scriptHandler.addScriptListener(ScriptingPanel.this);
                    PreferencesWindow prefWin = PreferencesWindow.getPreferencesWindow();
                    scriptHandler.setVarInterpretation(prefWin.isVarInterpretationEnabled());
                    scriptHandler.setStrict(prefWin.isStrictModeEnabled());
                    scriptHandler.setForceRun(prefWin.isOverrideEnabled());
                    provider.setHandler(scriptHandler);
                    textArea.addKeyListener(scriptHandler);
                    PluginRepositoryLoader.addListener(scriptHandler);

                    BindingsScriptFrame frame = BindingsScriptFrame.getInstance();
                    frame.setEngine(scriptHandler.getEngine());
                }
                if (btnClearConsole != null)
                    btnClearConsole.removeActionListener(ScriptingPanel.this);
                btnClearConsole = new JButton("Clear");
                btnClearConsole.addActionListener(ScriptingPanel.this);
                rebuildGUI();
                textArea.requestFocus();
            }
        });
    }

    public void openSource(Class<?> clazz)
    {
        openSource(clazz, "");
    }

    public void openSource(Class<?> clazz, String entryPoint)
    {
        InputStream jar = JarAccess.getJavaSourceInputStream(clazz);
        if (jar != null)
        {
            try
            {
                byte b[] = new byte[jar.available()];
                jar.read(b);
                String res = new String(b);
                IcyFrame frame = new IcyFrame("Source code of: [" + clazz.getName() + "]", true, true, true, true);
                JPanel panel = new JPanel(new BorderLayout());
                RSyntaxTextArea sourceTextArea = new RSyntaxTextArea(200, 200);
                sourceTextArea.setText(res);
                sourceTextArea.setEditable(false);
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
                sourceTextArea.setCaretPosition(0);
                try
                {
                    Theme t = Theme.load(PluginLoader.getLoader().getResourceAsStream(
                            "plugins/tprovoost/scripteditor/resources/themes/eclipse.xml"));
                    t.apply(sourceTextArea);
                }
                catch (IOException e2)
                {
                }

                RTextScrollPane paneSource = new RTextScrollPane(sourceTextArea);
                panel.add(paneSource);
                frame.setContentPane(panel);
                frame.setSize(720, 640);
                frame.addToMainDesktopPane();
                frame.setVisible(true);
            }
            catch (IOException e1)
            {
            }

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
        if (!integrated)
        {
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(scrollpane, BorderLayout.CENTER);

            JPanel panelSouth = new JPanel(new BorderLayout());
            panelSouth.add(console, BorderLayout.CENTER);
            panelSouth.add(btnClearConsole, BorderLayout.EAST);
            bottomPanel.add(panelSouth, BorderLayout.SOUTH);

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pane, bottomPanel);
            split.setDividerLocation(0.75d);
            split.setResizeWeight(0.75d);
            split.setOneTouchExpandable(true);
            add(split, BorderLayout.CENTER);
        }
        else
            add(pane);

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

        public PanelOptions()
        {
            this("JavaScript");
        }

        public PanelOptions(String language)
        {
            // final JButton btnBuild = new JButton("Verify");
            btnRun = new IcyButton(new IcyIcon("playback_play", 16));
            btnRun.setToolTipText("Run the script in the current context.");

            // btnRunNew = new IcyButton(new IcyIcon(imgPlayback2, 16));
            btnRunNew = new IcyButton(new IcyIcon("playback_play", 16));
            btnRunNew.setToolTipText("Creates a new context and run the script. The previous context will be lost.");

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

            // btnBuild.addActionListener(new ActionListener()
            // {
            //
            // @Override
            // public void actionPerformed(ActionEvent e)
            // {
            // if (scriptHandler != null)
            // {
            // scriptHandler.interpret(false);
            // }
            // else
            // System.out.println("Script Handler null.");
            // }
            // });
            // add(btnBuild);

            btnRun.addActionListener(new ActionListener()
            {

                @Override
                public void actionPerformed(ActionEvent e)
                {
                    if (scriptHandler == null)
                        return;
                    PreferencesWindow prefs = PreferencesWindow.getPreferencesWindow();
                    ThreadUtil.invokeLater(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            btnRun.setEnabled(false);
                            btnRunNew.setEnabled(false);
                            btnStop.setEnabled(true);
                        }
                    });
                    if (!integrated)
                    {
                        scriptHandler.setNewEngine(false);
                        scriptHandler.setForceRun(prefs.isOverrideEnabled());
                        scriptHandler.setStrict(prefs.isStrictModeEnabled());
                        scriptHandler.setVarInterpretation(prefs.isVarInterpretationEnabled());
                        scriptHandler.interpret(true);
                    }
                }
            });

            btnRunNew.addActionListener(new ActionListener()
            {

                @Override
                public void actionPerformed(ActionEvent e)
                {
                    if (scriptHandler == null)
                        return;
                    PreferencesWindow prefs = PreferencesWindow.getPreferencesWindow();
                    ThreadUtil.invokeLater(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            btnRun.setEnabled(false);
                            btnRunNew.setEnabled(false);
                            btnStop.setEnabled(true);
                        }
                    });
                    // consoleOutput.setText("");
                    scriptHandler.setNewEngine(true);
                    scriptHandler.setForceRun(prefs.isOverrideEnabled());
                    scriptHandler.setStrict(prefs.isStrictModeEnabled());
                    scriptHandler.setVarInterpretation(prefs.isVarInterpretationEnabled());
                    scriptHandler.interpret(true);
                }
            });

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
            add(btnRunNew);
            // add(Box.createHorizontalStrut(STRUT_SIZE));
            // add(btnRun);
            add(Box.createHorizontalStrut(STRUT_SIZE));
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
        return (String) options.comboLanguages.getSelectedItem();
    }

    /**
     * Load the content of the file into the textArea. Also updates the {@link #saveFile()} and
     * {@link #saveFileAs(File)} variables, used to know
     * if the text is dirty.
     * 
     * @param f
     *        : the file to load the code from.
     * @throws IOException
     *         : If the file could not be opened or read, an exception is
     *         raised.
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
        ThreadUtil.invokeLater(new Runnable()
        {

            @Override
            public void run()
            {
                btnRun.setEnabled(true);
                btnRunNew.setEnabled(true);
                btnStop.setEnabled(false);
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == btnClearConsole)
        {
            if (console != null)
                console.clear();
        }
    }

    /**
     * Displays a modal dialog to go to a specific line.
     */
    public void displayGotoLine()
    {
        int min = 1;
        int max = textArea.getLineCount();
        String res = JOptionPane.showInputDialog(Icy.getMainInterface().getMainFrame(), "Enter line number (" + min
                + "," + max + ")", "Go to Line", JOptionPane.QUESTION_MESSAGE);
        try
        {
            int line = Integer.parseInt(res);
            textArea.setCaretPosition(textArea.getLineStartOffset(line - 1));
        }
        catch (NumberFormatException e)
        {
        }
        catch (BadLocationException e)
        {
        }
    }
}

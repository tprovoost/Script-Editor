package plugins.tprovoost.scripteditor.gui;

import icy.common.listener.AcceptListener;
import icy.file.FileUtil;
import icy.gui.component.button.IcyButton;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.IcyFrameAdapter;
import icy.gui.frame.IcyFrameEvent;
import icy.gui.frame.IcyFrameListener;
import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.main.Icy;
import icy.network.NetworkUtil;
import icy.preferences.IcyPreferences;
import icy.preferences.XMLPreferences;
import icy.resource.icon.IcyIcon;
import icy.system.SystemUtil;
import icy.system.thread.ThreadUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import plugins.tprovoost.scripteditor.scriptingconsole.BindingsScriptFrame;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngineHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptingHandler;

/**
 * Main GUI of the class
 * 
 * @author tprovoost
 */
public class ScriptingEditor extends IcyFrame implements IcyFrameListener
{
    private JTabbedPane tabbedPane;
    private JButton addPaneButton;
    private String currentDirectoryPath = "";
    private ArrayList<String> previousFiles = new ArrayList<String>();
    private static final int ctrlMask = SystemUtil.getMenuCtrlMask();
    private static final int MAX_RECENT_FILES = 20;
    private static final String STRING_LAST_DIRECTORY = "lastDirectory";
    private XMLPreferences prefs = IcyPreferences.pluginsRoot().node("scripteditor");
    private JMenu menuOpenRecent;
    private static final boolean IS_PYTHON_INSTALLED = ScriptEngineHandler.factory.getEngineByExtension("py") != null;
    private IcyFrameListener frameListener = new IcyFrameAdapter()
    {
        @Override
        public void icyFrameClosing(IcyFrameEvent e)
        {
            closeAll();
            PreferencesWindow.getPreferencesWindow().removeFrameListener(ScriptingEditor.this);
        }
    };
    private AcceptListener acceptlistener = new AcceptListener()
    {
        @Override
        public boolean accept(Object source)
        {
            return closeAll();
        }
    };

    public ScriptingEditor()
    {
        super("Script Editor", true, true, true, true);

        currentDirectoryPath = prefs.get(STRING_LAST_DIRECTORY, "");

        // load preferences
        XMLPreferences openedFiles = prefs.node("openedFiles");
        final ArrayList<String> toOpen = new ArrayList<String>();
        for (XMLPreferences key : openedFiles.getChildren())
        {
            String fileName = key.get("name", "");
            previousFiles.add(fileName);
            boolean opened = key.getBoolean("opened", false);
            key.putBoolean("opened", false);
            if (opened)
                toOpen.add(fileName);
        }

        setJMenuBar(createJMenuBar());

        JPanel mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new JTabbedPane()
        {
            /** */
            private static final long serialVersionUID = 1L;

            @Override
            public void setSelectedIndex(int index)
            {
                if (index != tabbedPane.getTabCount() - 1)
                    super.setSelectedIndex(index);
            }
        };
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.addChangeListener(new ChangeListener()
        {

            @Override
            public void stateChanged(ChangeEvent arg0)
            {
                Component comp = tabbedPane.getSelectedComponent();
                if (!(comp instanceof ScriptingPanel))
                    return;
                ScriptingPanel panel = (ScriptingPanel) comp;
                final ScriptingHandler handler = panel.getScriptHandler();
                ThreadUtil.bgRun(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        int max = 20;
                        int i = 0;
                        while (handler == null && i < max)
                        {
                            ThreadUtil.sleep(500);
                            ++i;
                        }
                    }
                });

            }
        });
        addPaneButton = new IcyButton(new IcyIcon("plus"));
        addPaneButton.setBorderPainted(false);
        addPaneButton.setPreferredSize(new Dimension(20, 20));
        addPaneButton.setMinimumSize(new Dimension(20, 20));
        addPaneButton.setMaximumSize(new Dimension(20, 20));
        addPaneButton.setSize(20, 20);
        addPaneButton.setOpaque(false);

        addPaneButton.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                createNewPane();
            }
        });
        createNewPane();

        mainPanel.add(tabbedPane);
        setContentPane(mainPanel);

        // ----------------------
        // setting listeners
        // ---------------------

        // exit listener
        Icy.getMainInterface().addCanExitListener(acceptlistener);

        // frame listener on preferences
        PreferencesWindow.getPreferencesWindow().addFrameListener(this);

        ThreadUtil.invokeLater(new Runnable()
        {

            @Override
            public void run()
            {
                for (String s : toOpen)
                {
                    try
                    {
                        openFile(new File(s));
                    }
                    catch (IOException e1)
                    {
                    }
                }
            }
        });
    }

    /**
     * Getter on tabbedPane.
     * 
     * @return
     */
    public JTabbedPane getTabbedPane()
    {
        return tabbedPane;
    }

    /**
     * Close all tabs by dispatching the closing to each tab.
     * 
     * @return Returns if success in closing. False means the user decided to cancel the closing.
     */
    private boolean closeAll()
    {
        if (getInternalFrame().getDefaultCloseOperation() == WindowConstants.DO_NOTHING_ON_CLOSE)
        {
            // Saving state of the opened files.
            XMLPreferences openedFiles = prefs.node("openedFiles");
            for (int i = 0; i < tabbedPane.getTabCount() - 1; ++i)
            {
                Component c = tabbedPane.getComponentAt(i);
                if (c instanceof ScriptingPanel)
                {
                    File f = ((ScriptingPanel) c).getSaveFile();
                    if (f != null)
                    {
                        String path = f.getAbsolutePath();
                        XMLPreferences key = openedFiles.node(path);
                        key.putBoolean("opened", true);
                    }
                }
            }
            while (tabbedPane.getTabCount() > 1)
            {
                if (!closeTab(0))
                    return false;
            }
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            close();
        }
        return true;
    }

    public ScriptingPanel createNewPane()
    {
        return createNewPane("Untitled");
    }

    /**
     * Creates a new Scripting Pane in the JTabbedPane and returns it. <br/>
     * Automatically removes the "+" tab and re-add it after the panel.
     * 
     * @param name
     *        : this is not a file path. Please use {@link #openFile(String)} instead to open a file
     *        with a file
     *        path.
     * @return
     * @see {@link #createNewPane()}, {@link #openFile(File)}, {@link #openFile(String)}
     */
    public ScriptingPanel createNewPane(String name)
    {
        ScriptingPanel panelCreated;
        String ext = FileUtil.getFileExtension(name, false);
        if (ext.contentEquals("py"))
            panelCreated = new ScriptingPanel(this, name, "python");
        else
            panelCreated = new ScriptingPanel(this, name, "javascript");
        panelCreated.setTabbedPane(tabbedPane);
        int idx = tabbedPane.getTabCount() - 1;
        if (idx != -1)
            tabbedPane.removeTabAt(idx);
        else
            idx = 0;
        tabbedPane.addTab(name, panelCreated);
        tabbedPane.setTitleAt(idx, name);
        tabbedPane.repaint();
        tabbedPane.setTabComponentAt(idx, new ButtonTabComponent(this, tabbedPane));
        tabbedPane.addTab("+", new JLabel());
        tabbedPane.setTabComponentAt(idx + 1, addPaneButton);
        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 2);
        Insets i = addPaneButton.getInsets();
        i.bottom = 0;
        i.left = 0;
        i.right = 0;
        i.top = 0;
        return panelCreated;
    }

    /**
     * Open the file f into the editor as a new tab.
     * 
     * @param f
     * @throws IOException
     */
    public void openFile(File f) throws IOException
    {
        String filename = f.getName();
        boolean exists = false;
        for (int i = 0; i < tabbedPane.getTabCount(); ++i)
            if (tabbedPane.getTitleAt(i).contentEquals(filename))
            {
                tabbedPane.setSelectedIndex(i);
                exists = true;
            }
        if (exists)
        {
            return;
        }
        // only one tab opened
        if (tabbedPane.getTabCount() == 2)
        {
            if (tabbedPane.getTitleAt(0).contentEquals("Untitled"))
                tabbedPane.remove(0);
        }
        ScriptingPanel panel = createNewPane(filename);
        panel.openFile(f);
        addRecentFile(f);
    }

    private void updateRecentFiles()
    {
        menuOpenRecent.removeAll();
        ArrayList<String> copy = new ArrayList<String>(previousFiles);
        for (int i = 0; i < copy.size(); ++i)
        {
            String path = copy.get(i);
            JMenuItem item = createRecentFileItem(path);
            if (item == null)
            {
                previousFiles.remove(path);
                prefs.node("openedFiles").remove(path);
            }
            else
            {
                menuOpenRecent.add(item);
            }
        }
    }

    private JMenuItem createRecentFileItem(String filename)
    {
        final File f = new File(filename);
        JMenuItem toReturn = null;
        if (f.exists())
        {
            toReturn = new JMenuItem(f.getPath());
            toReturn.addActionListener(new ActionListener()
            {

                @Override
                public void actionPerformed(ActionEvent e)
                {
                    try
                    {
                        openFile(f);
                    }
                    catch (IOException e1)
                    {
                        e1.printStackTrace();
                    }
                }
            });
        }
        return toReturn;
    }

    /**
     * Open the file f into the editor as a new tab.
     * 
     * @param f
     * @throws IOException
     */
    public void openStream(String name, InputStream stream) throws IOException
    {
        if (tabbedPane.getTabCount() == 2)
        {
            ScriptingPanel panel = (ScriptingPanel) tabbedPane.getComponentAt(0);
            if (!panel.isDirty() && panel.getPanelName().contentEquals("Untitled"))
                tabbedPane.removeTabAt(0);
        }
        ScriptingPanel panel = createNewPane(name);
        panel.openStream(stream);
    }

    /**
     * Displays a JFileChoose and let the user choose its file, then open it.
     * 
     * @see ScriptingEditor#openFile(File)
     */
    public void showOpenFileDialog()
    {
        JFileChooser fc;
        if (currentDirectoryPath == "")
            fc = new JFileChooser();
        else
            fc = new JFileChooser(currentDirectoryPath);
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(getFrame()) == JFileChooser.APPROVE_OPTION)
        {
            File[] files = fc.getSelectedFiles();
            for (File f : files)
            {
                String path = FileUtil.getDirectory(f.getPath());
                currentDirectoryPath = path;
                prefs.put(STRING_LAST_DIRECTORY, path);
                try
                {
                    openFile(f);
                }
                catch (IOException e)
                {
                    new FailedAnnounceFrame(f.getName() + " is not a valid file");
                }
            }
        }
    }

    /**
     * Creates the JMenuBar of the {@link ScriptingEditor}.
     * 
     * @return
     */
    private JMenuBar createJMenuBar()
    {
        JMenuBar toReturn = new JMenuBar();

        // MENU FILE
        JMenu menuFile = new JMenu("File");
        JMenuItem menuNew = new JMenuItem("New");
        menuNew.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                createNewPane("Untitled");
            }
        });
        menuFile.add(menuNew);
        menuNew.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ctrlMask));

        JMenuItem menuOpen = new JMenuItem("Open...");
        menuOpen.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                showOpenFileDialog();
            }
        });
        menuOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ctrlMask));
        menuOpen.setDisplayedMnemonicIndex(0);
        menuFile.add(menuOpen);

        menuOpenRecent = new JMenu("Open Recent");
        menuFile.add(menuOpenRecent);

        JMenuItem menuSave = new JMenuItem("Save");
        menuSave.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                Component comp = tabbedPane.getSelectedComponent();
                if (comp instanceof ScriptingPanel)
                {
                    ScriptingPanel panel = ((ScriptingPanel) comp);
                    if (panel.getSaveFile() == null)
                    {
                        panel.showSaveFileDialog(currentDirectoryPath);
                    }
                    else if (panel.isDirty())
                    {
                        panel.saveFile();
                    }
                }
            }
        });
        menuSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ctrlMask));
        menuFile.add(menuSave);

        JMenuItem menuSaveAs = new JMenuItem("Save As...");
        menuSaveAs.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                Component comp = tabbedPane.getSelectedComponent();
                if (comp instanceof ScriptingPanel)
                {
                    ((ScriptingPanel) comp).showSaveFileDialog(currentDirectoryPath);
                }
            }
        });
        menuSaveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ctrlMask | InputEvent.SHIFT_DOWN_MASK));
        menuFile.add(menuSaveAs);

        menuFile.add(new JSeparator());

        JMenuItem menuClose = new JMenuItem("Close");
        menuClose.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                int i = tabbedPane.getSelectedIndex();
                if (i >= 0 && i < tabbedPane.getTabCount() - 1)
                {
                    closeTab(i);
                }
            }
        });
        menuClose.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, ctrlMask));
        menuFile.add(menuClose);

        // MENU EDIT
        JMenu menuEdit = new JMenu("Edit");
        JMenuItem menuUndo = new JMenuItem("Undo");
        menuUndo.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                Component comp = tabbedPane.getTabComponentAt(0);
                if (!(comp instanceof ScriptingPanel))
                    return;
                ScriptingPanel panel = (ScriptingPanel) comp;
                panel.getTextArea().getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrlMask))
                        .actionPerformed(e);
            }
        });
        menuUndo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrlMask));
        menuEdit.add(menuUndo);

        JMenuItem menuRedo = new JMenuItem("Redo");
        menuRedo.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                Component comp = tabbedPane.getTabComponentAt(0);
                if (!(comp instanceof ScriptingPanel))
                    return;
                ScriptingPanel panel = (ScriptingPanel) comp;
                panel.getTextArea().getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ctrlMask))
                        .actionPerformed(e);
            }
        });
        menuRedo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ctrlMask));
        menuEdit.add(menuRedo);

        menuEdit.add(new JSeparator());
        JMenuItem menuFormat = new JMenuItem("Format");
        menuFormat.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                Component c = tabbedPane.getSelectedComponent();
                if (c instanceof ScriptingPanel)
                {
                    ScriptingPanel panel = ((ScriptingPanel) c);
                    // panel.getScriptHandler().organizeImports();
                    panel.getScriptHandler().format();
                }
            }
        });
        menuFormat.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrlMask | InputEvent.SHIFT_DOWN_MASK));
        menuEdit.add(menuFormat);

        menuEdit.add(new JSeparator());

        // FIND FEATURE
        JMenuItem menuFind = new JMenuItem("Find");
        menuFind.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                displayFindReplace();
            }
        });
        menuFind.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrlMask));
        // menuFind.setEnabled(false);
        menuEdit.add(menuFind);

        // REPLACE FEATURE
        JMenuItem menuReplace = new JMenuItem("Replace");
        menuReplace.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                displayFindReplace();
            }
        });
        menuReplace.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, ctrlMask));
        // menuReplace.setEnabled(false);
        menuEdit.add(menuReplace);

        // GOTO LINE FEATURE
        JMenuItem menuGotoLine = new JMenuItem("Go to Line...");
        menuGotoLine.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                Component c = tabbedPane.getSelectedComponent();
                if (c instanceof ScriptingPanel)
                {
                    ((ScriptingPanel) c).displayGotoLine();
                }
            }
        });
        menuGotoLine.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, ctrlMask));
        menuEdit.add(menuGotoLine);

        JMenu menuTools = new JMenu("Tools");
        JMenuItem menuFindClass = new JMenuItem("Find Class...");
        menuTools.add(menuFindClass);

        // MENU TEMPLATES
        JMenu menuTemplate = new JMenu("Templates");
        populateMenuTemplate(menuTemplate);

        JMenu menuOptions = new JMenu("Options");
        JMenuItem menuPreferences = new JMenuItem("Preferences");
        menuPreferences.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                final PreferencesWindow prefs = PreferencesWindow.getPreferencesWindow();
                prefs.addToMainDesktopPane();
                prefs.setVisible(true);
            }
        });
        menuOptions.add(menuPreferences);

        JMenuItem menuBindingsFrame = new JMenuItem("Bindings Frame");
        menuBindingsFrame.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                BindingsScriptFrame frame = BindingsScriptFrame.getInstance();
                frame.update();
                if (frame.isVisible())
                    frame.setVisible(false);
                else
                    frame.setVisible(true);
            }
        });
        menuOptions.add(menuBindingsFrame);

        JMenuItem menuHelp = new JMenuItem("Help (online)");
        menuHelp.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                NetworkUtil.openBrowser("http://icy.bioimageanalysis.org/plugin/Script_Editor#documentation");
            }
        });
        menuOptions.add(menuHelp);

        toReturn.add(menuFile);
        toReturn.add(menuEdit);
        toReturn.add(menuTemplate);
        toReturn.add(menuTools);
        toReturn.add(menuOptions);

        updateRecentFiles();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addFrameListener(frameListener);

        return toReturn;
    }

    public void displayFindReplace()
    {
        FindAndReplaceDialog.showDialog(this);
    }

    protected boolean closeTab(int i)
    {
        Component c = tabbedPane.getTabComponentAt(i);
        if (c instanceof ButtonTabComponent)
        {
            return ((ButtonTabComponent) c).deletePane();
        }
        return true;
    }

    /**
     * Hard coded function populating the templates. May change to an automatic
     * parsing of files.
     * 
     * @param menuTemplate
     */
    private void populateMenuTemplate(JMenu menuTemplate)
    {
        JMenu menuTemplateJS = new JMenu("Javascript");
        JMenuItem itemJSDuplicateSequence = new JMenuItem("Duplicate Sequence");

        itemJSDuplicateSequence.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                openJSTemplate("duplicateSequence.js");
            }
        });
        menuTemplateJS.add(itemJSDuplicateSequence);

        JMenuItem itemThreshold = new JMenuItem("Threshold");
        itemThreshold.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                openJSTemplate("threshold.js");
            }
        });
        menuTemplateJS.add(itemThreshold);

        JMenu menuTemplatePython = new JMenu("Python");
        JMenuItem itemPythonDuplicateSequence = new JMenuItem("Duplicate Sequence");

        itemPythonDuplicateSequence.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                openPythonTemplate("duplicateSequence.py");
            }
        });
        menuTemplatePython.add(itemPythonDuplicateSequence);

        // add JS templates.
        menuTemplate.add(menuTemplateJS);
        if (IS_PYTHON_INSTALLED)
            menuTemplate.add(menuTemplatePython);
    }

    private void openJSTemplate(String templateName)
    {
        openTemplate("js", templateName);
    }

    private void openPythonTemplate(String templateName)
    {
        openTemplate("python", templateName);
    }

    /**
     * Open a template file contained in
     * plugins/tprovoost/scripteditor/templates/" + type + "/" + templateName
     * 
     * @param type
     * @param templateName
     */
    private void openTemplate(String type, String templateName)
    {
        String current = new File(".").getAbsolutePath();
        current = current.substring(0, current.length() - 1);
        try
        {
            InputStream is = getClass().getClassLoader().getResourceAsStream(
                    "plugins/tprovoost/scripteditor/resources/templates/" + type + "/" + templateName);
            openStream(templateName, is);
        }
        catch (IOException e1)
        {
        }
    }

    public String getCurrentDirectory()
    {
        return currentDirectoryPath;
    }

    public void addRecentFile(File f)
    {
        String filename = f.getName();
        String path = f.getPath();
        XMLPreferences openedFiles = prefs.node("openedFiles");
        if (!previousFiles.contains(path))
        {
            previousFiles.add(path);
            XMLPreferences key = openedFiles.node(path);
            key.put("name", path);
            // key.putBoolean("opened", true);
        }
        if (previousFiles.size() > MAX_RECENT_FILES)
        {
            filename = previousFiles.get(0);
            XMLPreferences key = openedFiles.node(filename);
            if (key.exists())
            {
                openedFiles.remove(filename);
            }
            previousFiles.remove(0);
        }
        updateRecentFiles();
    }

    @Override
    public void icyFrameOpened(IcyFrameEvent e)
    {
    }

    @Override
    public void icyFrameClosing(IcyFrameEvent e)
    {
    }

    @Override
    public void icyFrameClosed(IcyFrameEvent e)
    {
        PreferencesWindow prefWin = PreferencesWindow.getPreferencesWindow();

        for (int i = 0; i < tabbedPane.getTabCount(); ++i)
        {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof ScriptingPanel)
            {
                RSyntaxTextArea textArea = ((ScriptingPanel) comp).getTextArea();
                if (!textArea.getText().isEmpty())
                {
                    boolean indentWanted = prefWin.isIndentSpacesEnabled();
                    // if (textArea.getTabsEmulated() != indentWanted)
                    // {
                    // a change occured
                    textArea.setTabsEmulated(indentWanted);
                    if (indentWanted)
                    {
                        textArea.convertSpacesToTabs();
                    }
                    else
                    {
                        textArea.setTabSize(prefWin.indentSpacesCount());
                        textArea.convertTabsToSpaces();
                    }
                    // }
                }
            }
        }
    }

    @Override
    public void icyFrameIconified(IcyFrameEvent e)
    {
    }

    @Override
    public void icyFrameDeiconified(IcyFrameEvent e)
    {
    }

    @Override
    public void icyFrameActivated(IcyFrameEvent e)
    {
    }

    @Override
    public void icyFrameDeactivated(IcyFrameEvent e)
    {
    }

    @Override
    public void icyFrameInternalized(IcyFrameEvent e)
    {
    }

    @Override
    public void icyFrameExternalized(IcyFrameEvent e)
    {
    }
}

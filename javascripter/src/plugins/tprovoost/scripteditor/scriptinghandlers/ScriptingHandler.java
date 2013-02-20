package plugins.tprovoost.scripteditor.scriptinghandlers;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.ImageUtil;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLoader;
import icy.plugin.PluginRepositoryLoader.PluginRepositoryLoaderListener;
import icy.plugin.abstract_.Plugin;
import icy.resource.icon.IcyIcon;
import icy.system.thread.ThreadUtil;
import icy.util.ClassUtil;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.VariableCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextArea;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;

import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scripteditor.completion.types.BasicJavaClassCompletion;
import plugins.tprovoost.scripteditor.gui.PreferencesWindow;
import plugins.tprovoost.scripteditor.gui.ScriptingPanel;
import plugins.tprovoost.scripteditor.main.ScriptListener;
import plugins.tprovoost.scripteditor.scriptingconsole.BindingsScriptFrame;
import plugins.tprovoost.scriptenginehandler.ScriptEngineHandler;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;

/**
 * This class is in charge of the compilation of the script. It mostly depends
 * on the provider.
 * 
 * @author Thomas Provoost
 */
public abstract class ScriptingHandler implements KeyListener, PluginRepositoryLoaderListener
{

    /**
     * {@link HashMap} containing all ignored Lines if they contains errors.
     * This allows the parser to no stop at the first line where the error is.
     */
    protected HashMap<Integer, Exception> ignoredLines = new HashMap<Integer, Exception>();

    /**
     * List of the variable completions found when script was parsed. Functions
     * and classes are considered as Variables too.
     */
    protected ArrayList<Completion> variableCompletions = new ArrayList<Completion>();

    /** Reference to the provider used for the autocompletion. */
    protected DefaultCompletionProvider provider;

    private String engineType;

    /**
     * Is the compilation a success? The script will never be run if the
     * compilation / parsing contains issues.
     */
    private boolean compilationOk = false;

    /** Reference to the current engine. */

    /** Reference to the {@link RSyntaxTextArea} this item works on. */
    protected JTextComponent textArea;

    /** Contains all declared variables in the script. */
    protected HashMap<String, TreeMap<Integer, Class<?>>> localVariables;

    /** Contains all declared variables in the script. */
    protected HashMap<String, Class<?>> localFunctions = new HashMap<String, Class<?>>();

    /** Contains all declared importPackages in the script. */
    protected ArrayList<String> scriptDeclaredImports = new ArrayList<String>();

    /** Contains all declared importClasses in the script. */
    protected ArrayList<String> scriptDeclaredImportClasses = new ArrayList<String>();

    /** A specific offset contains an Function. */
    protected HashMap<Integer, IcyFunctionBlock> blockFunctions = new HashMap<Integer, IcyFunctionBlock>();

    /**
     * This is where the warning / errors are displayed, contained in this
     * scrollpane.
     */
    protected JTextPane errorOutput;

    /** Reference to the textarea scrollpane gutter. */
    private Gutter gutter;

    /** Filename of the script */
    protected String fileName = "Untitled";

    /** for debug purposes: advance will be used to load or not all functions */
    private boolean advanced;

    private boolean forceRun = false;
    private boolean newEngine = true;
    private boolean strict = false;
    private boolean varInterpretation = false;

    /**
     * Thread running the evaluation.
     */
    public EvalThread thread;

    private ArrayList<ScriptListener> listeners = new ArrayList<ScriptListener>();

    /** Turn to true if you need to display more information in the console. */
    protected static final boolean DEBUG = false;

    // Different relevance of items. Simplify code, but integer values can
    // always be used.
    public static final int RELEVANCE_MIN = 1;
    public static final int RELEVANCE_LOW = 2;
    public static final int RELEVANCE_HIGH = 10;

    private static final IcyIcon ICON_ERROR_TOOLTIP = new IcyIcon(ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/quickfix_warning_obj.gif")), 16, false);

    private static final IcyIcon ICON_ERROR = new IcyIcon(ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/error.gif")), 15, false);

    private StringWriter sw = new StringWriter();
    private PrintWriter pw = new PrintWriter(sw, true)
    {
        @Override
        public synchronized void write(final String s)
        {
            if (errorOutput != null)
            {
                EventQueue.invokeLater(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        Document doc = errorOutput.getDocument();
                        try
                        {
                            Style style = errorOutput.getStyle("normal");
                            if (style == null)
                                style = errorOutput.addStyle("normal", null);
                            doc.insertString(doc.getLength(), s, style);
                        }
                        catch (BadLocationException e)
                        {
                        }
                    }
                });
            }
            else
            {
                System.out.print(s);
            }
        }
    };

    public ScriptingHandler(DefaultCompletionProvider provider, String engineType, JTextComponent textArea,
            Gutter gutter, boolean forceRun, ScriptingPanel scriptingPanel)
    {
        this.provider = provider;
        this.textArea = textArea;
        this.gutter = gutter;
        this.forceRun = forceRun;
        setLanguage(engineType);

        textArea.getDocument().addDocumentListener(new AutoVerify());

        localVariables = new HashMap<String, TreeMap<Integer, Class<?>>>();

        ScriptEngine engine = getEngine();
        if (engine == null)
        {
            return;
        }
        engine.getContext().setWriter(pw);
        engine.getContext().setErrorWriter(pw);
    }

    /**
     * @param provider
     *        : reference to the provider used by autocomplete. Cannot be
     *        null.
     * @param engineType
     *        : as of now, only "javascript" or "python".
     * @param textArea2
     *        : reference to the textArea. Cannot be null.
     * @param gutter
     *        : reference to the gutter attached to the scrollpane of the
     *        textArea.
     * @param autocompilation2
     */
    public ScriptingHandler(DefaultCompletionProvider provider, String engineType, JTextComponent textArea,
            Gutter gutter)
    {
        this(provider, engineType, textArea, gutter, false);
    }

    /**
     * @param provider
     *        : reference to the provider used by autocomplete. Cannot be
     *        null.
     * @param engineType
     *        : as of now, only "javascript" or "python".
     * @param textArea2
     *        : reference to the textArea. Cannot be null.
     * @param gutter
     *        : reference to the gutter attached to the scrollpane of the
     *        textArea.
     * @param autocompilation2
     */
    public ScriptingHandler(DefaultCompletionProvider provider, String engineType, JTextComponent textArea,
            Gutter gutter, boolean forceRun)
    {
        this(provider, engineType, textArea, gutter, forceRun, null);
    }

    public void setOutput(JTextPane errorOutput)
    {
        this.errorOutput = errorOutput;
    }

    /**
     * Ex: script.py or script.js
     * 
     * @param fileName
     */
    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    /**
     * Set the language of the Handler.
     * 
     * @param engineType
     */
    private void setLanguage(String engineType)
    {
        this.engineType = engineType;
        try
        {
            installDefaultLanguageCompletions(engineType);

        }
        catch (ScriptException e)
        {
            e.printStackTrace();
        }
    }

    public HashMap<String, Class<?>> getLocalFunctions()
    {
        return localFunctions;
    }

    public HashMap<String, TreeMap<Integer, Class<?>>> getLocalVariables()
    {
        return localVariables;
    }

    public HashMap<Integer, IcyFunctionBlock> getBlockFunctions()
    {
        return blockFunctions;
    }

    /**
     * Get the variable type.
     * 
     * @param name
     * @return
     */
    public Class<?> getVariableDeclaration(String name)
    {
        return getVariableDeclaration(name, textArea.getCaretPosition());
    }

    public abstract void eval(ScriptEngine engine, String s) throws ScriptException;

    /**
     * Get a variable declaration according to a specific offset
     * 
     * @param name
     * @return
     */
    public Class<?> getVariableDeclaration(String name, int offset)
    {
        boolean isArray = name.contains("[");
        String originalName = name;
        if (isArray)
        {
            name = name.substring(0, name.indexOf('['));
        }
        TreeMap<Integer, Class<?>> list = localVariables.get(name);
        if (list == null)
            return null;
        Class<?> type = null;
        for (Integer i : list.keySet())
        {
            if (offset > i)
                type = list.get(i);
        }
        if (type == null)
        {
            ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());
            type = engineHandler.getEngineVariables().get(name);
        }
        if (type != null && isArray)
        {
            int occ = originalName.split("\\[").length - 1;
            for (int i = 0; i < occ; ++i)
            {
                type = type.getComponentType();
            }
        }
        return type;
    }

    public abstract void installDefaultLanguageCompletions(String language) throws ScriptException;

    /**
     * Import all functions annotated with the {@link BindingFunction} annotation.
     * 
     * @throws ScriptException
     */
    public void importFunctions() throws ScriptException
    {
        if (!(provider instanceof IcyCompletionProvider))
            return;
        advanced = false;
        if (advanced)
        {
            ThreadUtil.bgRun(new Runnable()
            {

                @SuppressWarnings("deprecation")
                @Override
                public void run()
                {
                    ProgressFrame frame = new ProgressFrame("Loading functions...");
                    ((IcyCompletionProvider) provider).findAllMethods(getEngine(), frame);
                    frame.setVisible(false);
                }
            });
        }
        else
        {
            // install functions from ScriptEngineHandler
            ScriptEngineHandler handler = ScriptEngineHandler.getEngineHandler(getEngine());
            ArrayList<Method> functions = handler.getFunctions();

            ((IcyCompletionProvider) provider).installMethods(functions);
            installMethods(getEngine(), functions);
        }
    }

    public abstract void installMethods(ScriptEngine engine, ArrayList<Method> functions);

    /**
     * Returns if should execute the code or not.
     * 
     * @return
     */
    public boolean isCompilationOk()
    {
        return compilationOk;
    }

    /**
     * Sets if should execute the code or not.
     * 
     * @param compilationOk
     */
    private void setCompilationOk(boolean compilationOk)
    {
        this.compilationOk = compilationOk;
    }

    public boolean isNewEngine()
    {
        return newEngine;
    }

    public void setNewEngine(boolean newEngine)
    {
        this.newEngine = newEngine;
    }

    public boolean isForceRun()
    {
        return forceRun;
    }

    public void setForceRun(boolean forceRun)
    {
        this.forceRun = forceRun;
    }

    public boolean isStrict()
    {
        return strict;
    }

    public void setStrict(boolean strict)
    {
        this.strict = strict;
    }

    public void setVarInterpretation(boolean varInterpretation)
    {
        this.varInterpretation = varInterpretation;
    }

    /**
     * Interpret the script. If <code>exec</code> is true, will try to run the
     * code if compile is successful. Be careful: a building code is not
     * necessary a functionnal running code.
     * 
     * @param runAfterCompile
     *        : if true, runs the code.
     * @param b
     */
    public void interpret(boolean exec)
    {
        ignoredLines.clear();

        // use either selected text if any or all text
        String s = textArea.getSelectedText();
        if (s == null)
            s = textArea.getText();

        // interpret the code
        if (exec && forceRun)
        {
            run();
        }
        else
        {
            interpret(s);
            if (exec && (isCompilationOk()))
                run();
        }
    }

    protected void updateGutter()
    {
        if (gutter == null || !(textArea instanceof JTextArea))
            return;
        gutter.removeAllTrackingIcons();
        for (Integer a : new ArrayList<Integer>(ignoredLines.keySet()))
        {
            try
            {
                IcyIcon icon;
                Exception e = ignoredLines.get(a);
                if (e instanceof EvaluatorException)
                    icon = ICON_ERROR_TOOLTIP;
                else
                    icon = ICON_ERROR;
                String tooltip = ignoredLines.get(a).getMessage();
                // if (tooltip.length() > 127)
                // {
                // tooltip = tooltip.substring(0, 127) + "...";
                // }
                gutter.addLineTrackingIcon(a, icon, tooltip);
                gutter.repaint();
            }
            catch (BadLocationException e)
            {
            }
        }
    }

    private void updateOutput()
    {
        ThreadUtil.invokeLater(new Runnable()
        {

            @Override
            public void run()
            {
                if (thread != null)
                {
                    ThreadUtil.sleep(200);
                }
                String textResult = "";
                for (Integer a : ignoredLines.keySet())
                {
                    Exception ee = ignoredLines.get(a);
                    String msg = ee.getLocalizedMessage();

                    // System.out.println(msg);
                    textResult += msg + "\n";
                }
                if (errorOutput != null)
                {
                    Document doc = errorOutput.getDocument();
                    try
                    {
                        Style style = errorOutput.getStyle("error");
                        if (style == null)
                        {
                            style = errorOutput.addStyle("error", null);
                            StyleConstants.setForeground(style, Color.red);
                        }
                        doc.insertString(doc.getLength(), textResult, style);
                    }
                    catch (BadLocationException e)
                    {
                    }
                }
                else
                    System.out.print(textResult);
            }
        });
    }

    /**
     * This method interprets the code in one or two steps (depending on the
     * user willing to immediately run the code or not). First, the code is
     * Parsed. If any error occurs, the line containing it will be highlighted
     * in the gutter.
     * 
     * @param forceRun
     * @param s
     *        : the code to interpret.
     * @param exec
     *        : run after compile or not.
     */
    private void interpret(String s)
    {
        RTextArea textArea = new RTextArea();
        textArea.setText(s);
        try
        {
            Context context = Context.enter();
            context.setApplicationClassLoader(PluginLoader.getLoader());
            updateGutter();
            clearScriptVariables();
            registerImports();
            if (provider != null && varInterpretation)
            {
                detectVariables(s, context);
            }
            setCompilationOk(true);
        }
        catch (EvaluatorException ee)
        {
            // get the line and column of error
            int lineError = ee.lineNumber() - 1;
            int columnNumber = ee.columnNumber();
            if (columnNumber == -1)
                columnNumber = 0;
            try
            {
                // verify if exists (> 0 and < lineCount)
                if (lineError >= 0 && lineError <= textArea.getLineOfOffset(s.length() - 1))
                {
                    int lineOffset = textArea.getLineStartOffset(lineError);
                    int lineEndOffset = textArea.getLineEndOffset(lineError);

                    String textToRemove = s.substring(lineOffset, lineEndOffset);

                    textToRemove = "\n";
                    if (ignoredLines.containsKey(lineError))
                    {
                        // System.out.println("An error occured with the parsing.");
                        return;
                    }
                    ignoredLines.put(lineError, ee);
                    s = s.substring(0, lineOffset) + textToRemove + s.substring(lineEndOffset);

                    // interpret again, without the faulty line.
                    interpret(s);
                }
                else
                {
                    // stop interpretation
                    // System.out.println("error at unknown line: " +
                    // lineError);
                    ee.printStackTrace();
                    if (errorOutput != null)
                    {
                        Document doc = errorOutput.getDocument();
                        try
                        {
                            Style style = errorOutput.getStyle("normal");
                            if (style == null)
                                style = errorOutput.addStyle("normal", null);
                            doc.insertString(doc.getLength(), ee.getMessage() + "\n", style);
                        }
                        catch (BadLocationException e)
                        {
                        }
                    }
                    else
                        System.out.println(ee.getMessage());
                }
            }
            catch (BadLocationException e1)
            {
                e1.printStackTrace();
            }
        }
        catch (ScriptException se)
        {
            // get line error
            int lineError = lineNumber(se) - 1;
            Integer columnNumberI = columnNumber(se);
            int columnNumber = columnNumberI != null ? columnNumberI : -1;
            if (columnNumber == -1)
                columnNumber = 0;
            try
            {
                // verify integrity (>0, < lineCount)
                if (lineError >= 0 && lineError <= textArea.getLineOfOffset(s.length() - 1))
                {
                    int lineOffset = textArea.getLineStartOffset(lineError);
                    int lineEndOffset = textArea.getLineEndOffset(lineError);

                    s = s.substring(0, lineOffset) + "\n" + s.substring(lineEndOffset);
                    if (ignoredLines.containsKey(lineError))
                    {
                        // System.out.println("An error occured with the error parsing.");
                        return;
                    }
                    ignoredLines.put(lineError, se);

                    // interpret again, without the faulty line.
                    interpret(s);
                }
                else
                {
                    // stops interpretation
                    System.out.println("error at unknown line: " + lineError);
                    se.printStackTrace();
                    if (errorOutput != null)
                    {
                        Document doc = errorOutput.getDocument();
                        try
                        {
                            Style style = errorOutput.getStyle("normal");
                            if (style == null)
                                style = errorOutput.addStyle("normal", null);
                            doc.insertString(doc.getLength(), se.getMessage() + "\n", style);
                        }
                        catch (BadLocationException e)
                        {
                        }
                    }
                }
            }
            catch (BadLocationException e1)
            {
                e1.printStackTrace();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            Context.exit();
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void clearScriptVariables()
    {
        localVariables.clear();
        localFunctions.clear();
        scriptDeclaredImports.clear();

        for (String s : scriptDeclaredImportClasses)
        {
            try
            {
                BasicJavaClassCompletion c = new BasicJavaClassCompletion(provider, ClassUtil.findClass(s));
                s = c.getName();
                List<Completion> list = provider.getCompletionByInputText(s);
                if (list == null)
                    continue;
                for (Completion c2 : new ArrayList<Completion>(list))
                {
                    if (c2 instanceof VariableCompletion)
                    {
                        if (((VariableCompletion) c2).getName().contentEquals(s))
                            provider.removeCompletion(c2);
                    }
                }
            }
            catch (ClassNotFoundException e)
            {
            }
        }
        scriptDeclaredImportClasses.clear();
    }

    public void run()
    {
        if (isNewEngine())
        {
            if (errorOutput != null)
            {
                // Graphics2D g = (Graphics2D) errorOutput.getGraphics();
                // int chW = g.getFontMetrics().charWidth('_');
                // int w = errorOutput.getWidth();
                // int charCount = w / chW;
                // String str = "";
                // for (int i = 0; i < charCount - 1; ++i)
                // str += '-';
                // errorOutput.append(str + "\n");
                // errorOutput.append("New Engine created" + "\n");
                // errorOutput.append(str + "\n");
                errorOutput.setText("");
                // g.dispose();
            }
            ScriptEngine engine = createNewEngine();
            thread = new EvalThread(engine, textArea.getText());
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
        else
        {
            ScriptEngine engine = getEngine();
            EvalThread thread = new EvalThread(engine, textArea.getText());
            thread.start();
        }
    }

    /**
     * Creates a new engine for the current language.
     * Will delete the previous one.
     * 
     * @return
     */
    public ScriptEngine createNewEngine()
    {
        ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());
        ArrayList<Method> functions = engineHandler.getFunctions();
        String newEngineType = getEngine().getFactory().getLanguageName();
        ScriptEngine engine = ScriptEngineHandler.getEngine(newEngineType, true);
        installMethods(engine, functions);
        engine.getContext().setWriter(pw);
        engine.getContext().setErrorWriter(pw);

        return engine;
    }

    /**
     * Register all imports contained in the script.
     * 
     * @param s
     */
    public abstract void registerImports();

    public abstract void autoDownloadPlugins();

    /**
     * This method will detect the variables and add them to the provider.
     * 
     * @param s
     * @param context
     * @throws Exception
     */
    protected abstract void detectVariables(String s, Context context) throws Exception;

    @Override
    public void keyTyped(KeyEvent e)
    {
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        switch (e.getKeyCode())
        {
            case KeyEvent.VK_ENTER:
                if (e.isControlDown())
                {
                    interpret(false);
                    e.consume();
                    break;
                }
                else if (e.isShiftDown())
                {
                    break;
                }

            case KeyEvent.VK_R:
                if (e.isControlDown())
                    interpret(true);
                break;

            case KeyEvent.VK_M:
                if (e.isControlDown())
                {
                    Bindings bindings = getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
                    for (String s : bindings.keySet())
                    {
                        // try {
                        // Object o = bindings.get(s);
                        // if (o instanceof NativeFunction) {
                        // System.out.print(s + ": ");
                        // engine.eval("print(" + s + ")");
                        // } else {
                        Object o = bindings.get(s);
                        System.out.println(s + " : " + o);
                        // }
                        // } catch (ScriptException e1) {
                        // System.out.println(s + " : " + bindings.get(s));
                        // }
                    }
                    // for (String s : localVariables.keySet()) {
                    // System.out.println(s + ": " + localVariables.get(s));
                    // }
                }
                break;
            default:
                break;
        }
    }

    public void organizeImports()
    {
        organizeImports(textArea);
    }

    public abstract void organizeImports(JTextComponent textArea);

    @Override
    public void keyReleased(KeyEvent e)
    {
    }

    /**
     * Returns the column number where the error occurred. This function should
     * be called instead of {@link ScriptException#getColumnNumber()}.
     * 
     * @param se
     *        : the ScriptException raised.
     * @return
     */
    private Integer columnNumber(ScriptException se)
    {
        Throwable cause = se.getCause();
        int columnNumber = se.getColumnNumber();
        if (cause == null || columnNumber >= 0)
            return columnNumber;
        return callMethod(cause, "columnNumber", Integer.class);
    }

    /**
     * Returns the line number where the error occurred. This function should be
     * called instead {@link ScriptException#getLineNumber()}.
     * 
     * @param se
     *        : the ScriptException raised.
     * @return
     */
    private Integer lineNumber(ScriptException se)
    {
        Throwable cause = se.getCause();
        int lineNumber = se.getLineNumber();
        if (lineNumber >= 0)
            return lineNumber;
        if (cause == null)
            return -1;
        else
            return callMethod(cause, "lineNumber", Integer.class);
    }

    static private Method getMethod(Object object, String methodName)
    {
        try
        {
            if (object != null)
                return object.getClass().getMethod(methodName);
            return null;
        }
        catch (NoSuchMethodException e)
        {
            return null;
            /* gulp */
        }
    }

    static private <T> T callMethod(Object object, String methodName, Class<T> cl)
    {
        try
        {
            Method m = getMethod(object, methodName);
            if (m != null)
            {
                Object result = m.invoke(object);
                return cl.cast(result);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns the class corresponding to the type, depending on the imports:<br/>
     * Javascript example:
     * 
     * <pre>
     * {@code
     * importPackage(Packages.javax.swing)
     * importClass(Packages.java.util.Math)
     * }
     * </pre>
     * 
     * If the type asked is JButton, this will return javax.swing.JButton class.
     * If the type asked is Math, this will return java.util.Math class.
     * 
     * @param type
     * @return
     */
    public Class<?> resolveClassDeclaration(String type)
    {
        // try with declared in the script importClass
        for (String s : scriptDeclaredImportClasses)
        {
            if (ClassUtil.getSimpleClassName(s).contentEquals(type))
                try
                {
                    return ClassUtil.findClass(s);
                }
                catch (ClassNotFoundException e)
                {
                    System.out.println(e.getLocalizedMessage());
                }
                catch (NoClassDefFoundError e2)
                {
                }
        }

        // try with declared in the script importPackage
        for (String s : scriptDeclaredImports)
        {
            try
            {
                return ClassUtil.findClass(s + "." + type);
            }
            catch (ClassNotFoundException e)
            {
            }
            catch (NoClassDefFoundError e2)
            {
            }
        }

        if (strict)
        {
            // declared in engine
            ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());

            // try with declared in the engine importClass
            for (String s : engineHandler.getEngineDeclaredImportClasses())
            {
                if (ClassUtil.getSimpleClassName(s).contentEquals(type))
                    try
                    {
                        return ClassUtil.findClass(s);
                    }
                    catch (ClassNotFoundException e)
                    {
                    }
                    catch (NoClassDefFoundError e2)
                    {
                    }
            }

            // try with declared in the script importPackage
            for (String s : engineHandler.getEngineDeclaredImports())
            {
                try
                {
                    return ClassUtil.findClass(s + "." + type);
                }
                catch (ClassNotFoundException e)
                {
                }
                catch (NoClassDefFoundError e2)
                {
                }
            }
        }
        return null;
    }

    public ScriptEngine getEngine()
    {
        return ScriptEngineHandler.getEngine(engineType);
    }

    /**
     * @author thomasprovoost
     */
    private class AutoVerify extends FocusAdapter implements DocumentListener, ActionListener
    {

        private Timer timer;
        private boolean lastChange;

        public AutoVerify()
        {
            timer = new Timer(2000, this);
            timer.setRepeats(false);
        }

        @Override
        public void insertUpdate(DocumentEvent e)
        {
            try
            {
                Document doc = e.getDocument();
                int offset = e.getOffset();
                int len = e.getLength();
                if (doc.getText(offset, len).contentEquals("."))
                {
                    timer.stop();
                    lastChange = false;
                    String fullTxt = doc.getText(0, doc.getLength());
                    String s = fullTxt.substring(0, offset);
                    s += fullTxt.substring(offset + len, doc.getLength());

                    interpret(s);
                }
            }
            catch (BadLocationException e1)
            {
                e1.printStackTrace();
            }
        }

        @Override
        public void removeUpdate(DocumentEvent e)
        {
        }

        @Override
        public void changedUpdate(DocumentEvent e)
        {
            lastChange = true;
            if (PreferencesWindow.getPreferencesWindow().isAutoBuildEnabled())
                timer.restart();
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            lastChange = false;
            interpret(textArea.getText());
        }

        @Override
        public void focusLost(FocusEvent e)
        {
            timer.stop();
        }

        @Override
        public void focusGained(FocusEvent e)
        {
            if (lastChange)
                timer.restart();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void pluginRepositeryLoaderChanged(PluginDescriptor plugin)
    {
        if (plugin == null)
            return;
        Class<? extends Plugin> clazz;
        try
        {
            clazz = (Class<? extends Plugin>) ClassUtil.findClass(plugin.getClassAsString());
        }
        catch (ClassNotFoundException e)
        {
            return;
        }

        if (!plugin.isInstalled())
        {
            // uninstalled the plugin
            ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());
            HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>> engineTypesMethod = engineHandler
                    .getEngineTypesMethod();
            engineTypesMethod.remove(clazz);
        }
        else
        {
            // plugin installed
            if (provider instanceof IcyCompletionProvider)
            {
                // ((IcyCompletionProvider)
                // provider).findBindingsMethods(engine, clazz);
            }
        }
    }

    /**
     * @author Thomas Provoost
     */
    public class EvalThread extends Thread
    {

        private String s;
        private ScriptEngine evalEngine;

        public EvalThread(ScriptEngine engine, String script)
        {
            this.evalEngine = engine;
            this.s = script;
        }

        @Override
        public void run()
        {
            fireEvaluationStarted();
            if (evalEngine != getEngine())
            {
                evalEngine.getContext().setWriter(pw);
                evalEngine.getContext().setErrorWriter(pw);
            }
            try
            {
                eval(evalEngine, s);

                ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());

                for (String key : localVariables.keySet())
                    engineHandler.getEngineVariables().put(key, localVariables.get(key).lastEntry().getValue());
                engineHandler.getEngineFunctions().putAll(localFunctions);
                engineHandler.getEngineDeclaredImportClasses().addAll(scriptDeclaredImportClasses);
                engineHandler.getEngineDeclaredImports().addAll(scriptDeclaredImports);
                BindingsScriptFrame frame = BindingsScriptFrame.getInstance();
                frame.update();
            }
            catch (ThreadDeath td)
            {
                System.out.println("shutdown");
            }
            catch (final ScriptException se)
            {
                ThreadUtil.invokeLater(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        JTextArea textArea = new JTextArea(s);
                        int lineError = lineNumber(se) - 1;
                        Integer columnNumberI = columnNumber(se);
                        int columnNumber = columnNumberI != null ? columnNumberI : -1;
                        if (columnNumber == -1)
                            columnNumber = 0;

                        try
                        {
                            // verify integrity (>0, < lineCount)
                            if (lineError >= 0 && lineError <= textArea.getLineOfOffset(s.length() - 1))
                            {
                                int lineOffset = textArea.getLineStartOffset(lineError);
                                int lineEndOffset = textArea.getLineEndOffset(lineError);

                                s = s.substring(0, lineOffset) + "\n" + s.substring(lineEndOffset);
                                if (ignoredLines.containsKey(lineError))
                                {
                                    // System.out.println("An error occured with the error parsing.");
                                    return;
                                }
                                ignoredLines.put(lineError, se);

                                updateGutter();
                                updateOutput();
                            }
                            else
                            {
                                if (errorOutput != null)
                                {
                                    Document doc = errorOutput.getDocument();
                                    try
                                    {
                                        Style style = errorOutput.getStyle("error");
                                        if (style == null)
                                            style = errorOutput.addStyle("error", null);
                                        doc.insertString(doc.getLength(), se.getLocalizedMessage() + "\n", style);
                                    }
                                    catch (BadLocationException e)
                                    {
                                    }
                                    errorOutput.repaint();
                                }
                            }
                        }
                        catch (BadLocationException e1)
                        {
                            e1.printStackTrace();
                        }
                    }
                });
            }
            finally
            {
                updateGutter();
                fireEvaluationOver();
                thread = null;
            }
        }
    }

    @SuppressWarnings("deprecation")
    public void killScript()
    {
        // Something is Running !
        if (thread != null)
        {
            thread.stop();
            thread = null;
        }
    }

    public void fireEvaluationStarted()
    {
        for (ScriptListener listener : new ArrayList<ScriptListener>(listeners))
            listener.evaluationStarted();
    }

    public void fireEvaluationOver()
    {
        for (ScriptListener listener : new ArrayList<ScriptListener>(listeners))
            listener.evaluationOver();
    }

    public boolean isRunning()
    {
        return thread != null;
    }

    public void addScriptListener(ScriptListener listener)
    {
        listeners.add(listener);
    }

    public void removeScriptListener(ScriptListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * Formats the text according to the handler.
     */
    public abstract void format();

}

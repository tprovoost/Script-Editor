package plugins.tprovoost.scripteditor.main.scriptinghandlers;

import icy.gui.frame.progress.ProgressFrame;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLoader;
import icy.plugin.PluginRepositoryLoader.PluginRepositoryLoaderListener;
import icy.plugin.abstract_.Plugin;
import icy.resource.icon.IcyIcon;
import icy.system.profile.Chronometer;
import icy.system.thread.ThreadUtil;
import icy.util.ClassUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextArea;
import org.xeustechnologies.jcl.JarClassLoader;

import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scriptenginehandler.ScriptEngineHandler;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;
import sun.org.mozilla.javascript.internal.Context;
import sun.org.mozilla.javascript.internal.EvaluatorException;

/**
 * 
 * This class is in charge of the compilation of the script. It mostly depends
 * on the provider.
 * 
 * @author Thomas Provoost
 * 
 */
public abstract class ScriptingHandler implements KeyListener, PluginRepositoryLoaderListener {

    /**
     * This {@link HashMap} is used to avoid multiple different engines for the
     * same language to be initialized.
     */
    private static final HashMap<String, ScriptEngine> engines = new HashMap<String, ScriptEngine>();

    /** The factory contains all the engines. */
    public static final ScriptEngineManager factory = new ScriptEngineManager();

    /**
     * {@link HashMap} containing all ignored Lines if they contains errors.
     * This allows the parser to no stop at the first line where the error is.
     */
    private HashMap<Integer, Exception> ignoredLines = new HashMap<Integer, Exception>();

    /**
     * List of the variable completions found when script was parsed. Functions
     * and classes are considered as Variables too.
     */
    protected ArrayList<Completion> variableCompletions = new ArrayList<Completion>();

    /** Reference to the provider used for the autocompletion. */
    protected DefaultCompletionProvider provider;

    /**
     * Is the compilation a success? The script will never be run if the
     * compilation / parsing contains issues.
     */
    private boolean compilationOk = false;

    /** Reference to the current engine. */
    protected ScriptEngine engine;

    /** Reference to the {@link RSyntaxTextArea} this item works on. */
    protected JTextComponent textArea;

    /** Contains all declared variables in the script AND in the engine. */
    protected HashMap<String, TreeMap<Integer, Class<?>>> localVariables;
    protected HashMap<String, Class<?>> localFunctions = new HashMap<String, Class<?>>();
    protected ArrayList<String> scriptDeclaredImports = new ArrayList<String>();
    protected ArrayList<String> scriptDeclaredImportClasses = new ArrayList<String>();
    protected HashMap<Integer, IcyFunctionBlock> blockFunctions = new HashMap<Integer, IcyFunctionBlock>();

    /**
     * This is where the warning / errors are displayed, contained in this
     * scrollpane.
     */
    private JTextArea errorOutput;

    /** Reference to the textarea scrollpane gutter. */
    private Gutter gutter;

    /** Filename of the script */
    protected String fileName = "Untitled";

    private boolean advanced;

    private boolean autoCompilation;

    /** Turn to true if you need to display more information in the console. */
    protected static final boolean DEBUG = false;

    // Different relevance of items. Simplify code, but integer values can
    // always be used.
    public static final int RELEVANCE_MIN = 1;
    public static final int RELEVANCE_LOW = 2;
    public static final int RELEVANCE_HIGH = 10;

    /**
     * @param provider
     *            : reference to the provider used by autocomplete. Cannot be
     *            null.
     * @param engineType
     *            : as of now, only "javascript" or "python".
     * @param textArea2
     *            : reference to the textArea. Cannot be null.
     * @param gutter
     *            : reference to the gutter attached to the scrollpane of the
     *            textArea.
     */
    public ScriptingHandler(DefaultCompletionProvider provider, String engineType, JTextComponent textArea, Gutter gutter) {
	this.provider = provider;
	this.textArea = textArea;
	this.gutter = gutter;
	setLanguage(engineType);

	textArea.getDocument().addDocumentListener(new AutoVerify());

	localVariables = new HashMap<String, TreeMap<Integer, Class<?>>>();

    }

    public void setErrorOutput(JTextArea errorOutput) {
	this.errorOutput = errorOutput;
    }

    /**
     * Ex: script.py or script.js
     * 
     * @param fileName
     */
    public void setFileName(String fileName) {
	this.fileName = fileName;
    }

    /**
     * Set the language of the Handler.
     * 
     * @param engineType
     */
    private void setLanguage(String engineType) {
	engine = engines.get(engineType);
	if (engine == null) {
	    engine = factory.getEngineByName(engineType);
	    engines.put(engineType, engine);
	}
	try {
	    installDefaultLanguageCompletions(engineType);

	} catch (ScriptException e) {
	    e.printStackTrace();
	}
    }

    public HashMap<String, Class<?>> getLocalFunctions() {
	return localFunctions;
    }

    public HashMap<String, TreeMap<Integer, Class<?>>> getLocalVariables() {
	return localVariables;
    }

    public HashMap<Integer, IcyFunctionBlock> getBlockFunctions() {
	return blockFunctions;
    }

    /**
     * Get the variable type.
     * 
     * @param name
     * @return
     */
    public Class<?> getVariableDeclaration(String name) {
	return getVariableDeclaration(name, textArea.getCaretPosition());
    }

    /**
     * Get a variable declaration according to a specific offset
     * 
     * @param name
     * @return
     */
    public Class<?> getVariableDeclaration(String name, int offset) {
	TreeMap<Integer, Class<?>> list = localVariables.get(name);
	if (list == null)
	    return null;
	Class<?> type = null;
	for (Integer i : list.keySet()) {
	    if (offset > i)
		type = list.get(i);
	}
	return type;
    }

    public abstract void installDefaultLanguageCompletions(String language) throws ScriptException;

    /**
     * Import all functions annotated with the {@link BindingFunction}
     * annotation.
     * 
     * @throws ScriptException
     */
    public void importFunctions() throws ScriptException {
	if (!(provider instanceof IcyCompletionProvider))
	    return;
	advanced = false;
	if (advanced) {
	    ThreadUtil.bgRun(new Runnable() {

		@Override
		public void run() {
		    ProgressFrame frame = new ProgressFrame("Loading functions...");
		    ((IcyCompletionProvider) provider).findAllMethods(engine, frame);
		    frame.setVisible(false);
		}
	    });
	} else {
	    ThreadUtil.bgRun(new Runnable() {

		@Override
		public void run() {
		    ProgressFrame frame = new ProgressFrame("Loading functions...");
		    Chronometer chrono = new Chronometer("chrono");

		    if (getClass().getClassLoader() instanceof JarClassLoader) {
			Collection<Class> col = PluginLoader.getAllClasses().values();

			frame.setLength(col.size());
			int i = 0;
			for (Class<?> clazz : new ArrayList<Class>(col)) {
			    if (clazz.getName().startsWith("plugins.tprovoost.scripteditor"))
				continue;
			    System.out.println(i + " : " + clazz.getName());
			    ((IcyCompletionProvider) provider).findBindingsMethods(engine, clazz);
			    ++i;
			    frame.setPosition(i);
			}
		    } else {
			ArrayList<PluginDescriptor> list = PluginLoader.getPlugins();
			frame.setLength(list.size());
			int i = 0;
			for (PluginDescriptor pd : list) {
			    System.out.println(pd);
			    ((IcyCompletionProvider) provider).findBindingsMethods(engine, pd.getPluginClass());
			    ++i;
			    frame.setPosition(i);
			}
		    }
		    chrono.displayInSeconds();
		    frame.close();
		}
	    });
	}
    }

    /**
     * Returns if should execute the code or not.
     * 
     * @return
     */
    public boolean isCompilationOk() {
	return compilationOk;
    }

    /**
     * Sets if should execute the code or not.
     * 
     * @param compilationOk
     */
    public void setCompilationOk(boolean compilationOk) {
	this.compilationOk = compilationOk;
    }

    /**
     * Interpret the script. If <code>exec</code> is true, will try to run the
     * code if compile is successful. Be careful: a building code is not
     * necessary a functionnal running code.
     * 
     * @param runAfterCompile
     *            : if true, runs the code.
     */
    public void interpret(boolean autocompilation, boolean runAfterCompile) {
	ignoredLines.clear();
	String s = textArea.getSelectedText();
	if (s == null)
	    interpret(textArea.getText(), autocompilation, runAfterCompile);
	else
	    interpret(s, autocompilation, runAfterCompile);
	if (errorOutput != null) {
	    String textResult = "";
	    for (Integer a : ignoredLines.keySet()) {
		try {
		    if (gutter != null) {
			gutter.addLineTrackingIcon(a, new IcyIcon("arrow_right", 10, false));
			gutter.repaint();
		    }
		} catch (BadLocationException e) {
		}
		Exception ee = ignoredLines.get(a);
		String msg = ee.getLocalizedMessage();

		System.out.println(msg);
		textResult += msg + "\n";
	    }
	    if (textResult.contentEquals("")) {
		if (runAfterCompile)
		    textResult = "Run with no issues.";
		else
		    textResult = "Compiled with no issues.";
	    }
	    errorOutput.setText(textResult);
	    errorOutput.repaint();
	}
    }

    /**
     * Evaluate the script and register the imports
     * 
     * @param s
     */
    public void evalScript(String s) {
	registerImports();
	try {
	    engine.eval(s);
	} catch (ScriptException e) {
	    System.out.println(e.getLocalizedMessage());
	}
    }

    /**
     * This method interprets the code in one or two steps (depending on the
     * user willing to immediately run the code or not). First, the code is
     * Parsed. If any error occurs, the line containing it will be highlighted
     * in the gutter.
     * 
     * @param s
     *            : the code to interpret.
     * @param exec
     *            : run after compile or not.
     */
    private void interpret(String s, boolean autocompilation, boolean exec) {
	RTextArea textArea = new RTextArea();
	textArea.setText(s);
	try {
	    Context context = Context.enter();
	    if (gutter != null)
		gutter.removeAllTrackingIcons();
	    localVariables.clear();
	    localFunctions.clear();
	    scriptDeclaredImports.clear();
	    scriptDeclaredImportClasses.clear();
	    blockFunctions.clear();
	    registerImports();
	    if (provider != null)
		detectVariables(s, context);
	    setCompilationOk(true);
	    if (exec && isCompilationOk()) {
		engine.eval(s);
		ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(engine);

		for (String key : localVariables.keySet())
		    engineHandler.getEngineVariables().put(key, localVariables.get(key).lastEntry().getValue());
		engineHandler.getEngineFunctions().putAll(localFunctions);
		engineHandler.getEngineDeclaredImportClasses().addAll(scriptDeclaredImportClasses);
		engineHandler.getEngineDeclaredImports().addAll(scriptDeclaredImports);
	    }
	} catch (EvaluatorException ee) {
	    int lineError = ee.lineNumber() - 1;
	    int columnNumber = ee.columnNumber();
	    if (columnNumber == -1)
		columnNumber = 0;
	    try {
		if (lineError >= 0 && lineError <= textArea.getLineOfOffset(s.length() - 1)) {
		    int lineOffset = textArea.getLineStartOffset(lineError);
		    int lineEndOffset = textArea.getLineEndOffset(lineError);

		    String textToRemove = s.substring(lineOffset, lineEndOffset);

		    // if (autocompilation && ee.getMessage().contains("\'.\'")
		    // && textToRemove.endsWith(".")) {
		    // textToRemove = textToRemove.substring(0,
		    // textToRemove.length() - 1);
		    // } else
		    textToRemove = "\n";
		    if (ignoredLines.containsKey(lineError)) {
			System.out.println("An error occured with the parsing.");
			return;
		    }
		    ignoredLines.put(lineError, ee);
		    // }
		    s = s.substring(0, lineOffset) + textToRemove + s.substring(lineEndOffset);
		    interpret(s, autocompilation, exec);
		} else {
		    System.out.println("error at unknown line: " + lineError);
		}
	    } catch (BadLocationException e1) {
		e1.printStackTrace();
	    }
	} catch (ScriptException se) {
	    int lineError = lineNumber(se) - 1;
	    Integer columnNumberI = columnNumber(se);
	    int columnNumber = columnNumberI != null ? columnNumberI : -1;
	    if (columnNumber == -1)
		columnNumber = 0;
	    try {
		if (lineError >= 0 && lineError <= textArea.getLineOfOffset(s.length() - 1)) {
		    int lineOffset = textArea.getLineStartOffset(lineError);
		    int lineEndOffset = textArea.getLineEndOffset(lineError);

		    s = s.substring(0, lineOffset) + "\n" + s.substring(lineEndOffset);
		    if (ignoredLines.containsKey(lineError)) {
			System.out.println("An error occured with the error parsing.");
			return;
		    }
		    ignoredLines.put(lineError, se);
		    interpret(s, autocompilation, exec);
		} else {
		    System.out.println("error at unknown line: " + lineError);
		}
	    } catch (BadLocationException e1) {
		e1.printStackTrace();
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	} finally {
	    Context.exit();
	}
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
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
	switch (e.getKeyCode()) {
	case KeyEvent.VK_ENTER:
	    if (e.isControlDown()) {
		interpret(false, false);
		e.consume();
		break;
	    } else if (e.isShiftDown()) {
		break;
	    }

	case KeyEvent.VK_R:
	    if (e.isControlDown())
		interpret(false, true);
	    break;

	case KeyEvent.VK_M:
	    if (e.isControlDown()) {
		Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		for (String s : bindings.keySet()) {
		    System.out.println(s + " : " + bindings.get(s));
		}
		for (String s : localVariables.keySet()) {
		    System.out.println(s + ": " + localVariables.get(s));
		}
		// for (String s : engineDeclaredImportClasses) {
		// System.out.println("importClass(" + s + ")");
		// }
		// for (String s : engineDeclaredImports) {
		// System.out.println("importPackage(" + s + ")");
		// }
	    }
	    break;
	case KeyEvent.VK_O:
	    if (e.isControlDown() && e.isShiftDown()) {
		organizeImports(textArea);
	    }
	    break;
	default:
	    break;
	}
    }

    protected abstract void organizeImports(JTextComponent textArea2);

    @Override
    public void keyReleased(KeyEvent e) {
    }

    /**
     * Returns the column number where the error occurred. This function should
     * be called instead of {@link ScriptException#getColumnNumber()}.
     * 
     * @param se
     *            : the ScriptException raised.
     * @return
     */
    private Integer columnNumber(ScriptException se) {
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
     *            : the ScriptException raised.
     * @return
     */
    private Integer lineNumber(ScriptException se) {
	Throwable cause = se.getCause();
	int lineNumber = se.getLineNumber();
	if (lineNumber >= 0)
	    return lineNumber;
	if (cause == null)
	    return -1;
	else
	    return callMethod(cause, "lineNumber", Integer.class);
    }

    static private Method getMethod(Object object, String methodName) {
	try {
	    if (object != null)
		return object.getClass().getMethod(methodName);
	    return null;
	} catch (NoSuchMethodException e) {
	    return null;
	    /* gulp */
	}
    }

    static private <T> T callMethod(Object object, String methodName, Class<T> cl) {
	try {
	    Method m = getMethod(object, methodName);
	    if (m != null) {
		Object result = m.invoke(object);
		return cl.cast(result);
	    }
	} catch (Exception e) {
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
    public Class<?> resolveClassDeclaration(String type, boolean strict) {
	// try with declared in the script importClass
	for (String s : scriptDeclaredImportClasses) {
	    if (ClassUtil.getSimpleClassName(s).contentEquals(type))
		try {
		    return ClassUtil.findClass(s);
		} catch (ClassNotFoundException e) {
		    System.out.println(e.getLocalizedMessage());
		} catch (NoClassDefFoundError e2) {
		}
	}

	// try with declared in the script importPackage
	for (String s : scriptDeclaredImports) {
	    try {
		return ClassUtil.findClass(s + "." + type);
	    } catch (ClassNotFoundException e) {
	    } catch (NoClassDefFoundError e2) {
	    }
	}

	if (!strict) {
	    // declared in engine
	    ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(engine);

	    // try with declared in the engine importClass
	    for (String s : engineHandler.getEngineDeclaredImportClasses()) {
		if (ClassUtil.getSimpleClassName(s).contentEquals(type))
		    try {
			return ClassUtil.findClass(s);
		    } catch (ClassNotFoundException e) {
		    } catch (NoClassDefFoundError e2) {
		    }
	    }

	    // try with declared in the script importPackage
	    for (String s : engineHandler.getEngineDeclaredImports()) {
		try {
		    return ClassUtil.findClass(s + "." + type);
		} catch (ClassNotFoundException e) {
		} catch (NoClassDefFoundError e2) {
		}
	    }
	}
	return null;
    }

    public void setActiveAutoCompilation(boolean b) {
	autoCompilation = b;
    }

    public ScriptEngine getEngine() {
	return engine;
    }

    /**
     * 
     * @author thomasprovoost
     * 
     */
    private class AutoVerify extends FocusAdapter implements DocumentListener, ActionListener {

	private Timer timer;
	private boolean lastChange;

	public AutoVerify() {
	    timer = new Timer(500, this);
	    timer.setRepeats(false);
	}

	@Override
	public void insertUpdate(DocumentEvent e) {
	    try {
		Document doc = e.getDocument();
		int offset = e.getOffset();
		int len = e.getLength();
		if (doc.getText(offset, len).contentEquals(".")) {
		    timer.stop();
		    lastChange = false;
		    String fullTxt = doc.getText(0, doc.getLength());
		    String s = fullTxt.substring(0, offset);
		    s += fullTxt.substring(offset + len, doc.getLength());

		    interpret(s, true, false);
		}
	    } catch (BadLocationException e1) {
		e1.printStackTrace();
	    }
	}

	@Override
	public void removeUpdate(DocumentEvent e) {
	}

	@Override
	public void changedUpdate(DocumentEvent e) {
	    lastChange = true;
	    if (autoCompilation)
		timer.restart();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
	    lastChange = false;
	    interpret(true, false);
	}

	@Override
	public void focusLost(FocusEvent e) {
	    timer.stop();
	}

	@Override
	public void focusGained(FocusEvent e) {
	    if (lastChange)
		timer.restart();
	}
    }

    @SuppressWarnings("unchecked")
    @Override
    public void pluginRepositeryLoaderChanged(PluginDescriptor plugin) {
	if (plugin == null)
	    return;
	Class<? extends Plugin> clazz;
	try {
	    clazz = (Class<? extends Plugin>) ClassUtil.findClass(plugin.getClassAsString());
	} catch (ClassNotFoundException e) {
	    return;
	}

	if (!plugin.isInstalled()) {
	    // uninstalled the plugin
	    ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(engine);
	    HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>> engineTypesMethod = engineHandler.getEngineTypesMethod();
	    engineTypesMethod.remove(clazz);
	} else {
	    // plugin installed
	    if (provider instanceof IcyCompletionProvider) {
		((IcyCompletionProvider) provider).findBindingsMethods(engine, clazz);
	    }
	}

    }
}

package plugins.tprovoost.scripteditor.main.scriptinghandlers;

import icy.gui.frame.progress.ProgressFrame;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginRepositoryLoader.PluginRepositoryLoaderListener;
import icy.plugin.abstract_.Plugin;
import icy.resource.icon.IcyIcon;
import icy.system.thread.ThreadUtil;
import icy.util.ClassUtil;

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
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.VariableCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextArea;

import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scripteditor.gui.PreferencesWindow;
import plugins.tprovoost.scripteditor.gui.ScriptingPanel;
import plugins.tprovoost.scripteditor.main.ScriptListener;
import plugins.tprovoost.scripteditor.scriptingconsole.BindingsScriptFrame;
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
    private JTextArea errorOutput;

    /** Reference to the textarea scrollpane gutter. */
    private Gutter gutter;

    /** Filename of the script */
    protected String fileName = "Untitled";

    /** for debug purposes: advance will be used to load or not all functions */
    private boolean advanced;

    private boolean forceRun = false;
    private boolean newEngine = true;
    private boolean strict = false;
    private boolean varInterpretation = true;

    volatile StringWriter sw = new StringWriter();
    volatile PrintWriter pw = new PrintWriter(sw, true) {
	@Override
	public void write(String s) {
	    if (errorOutput != null)
		errorOutput.append(s);
	}
    };

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

    public ScriptingHandler(DefaultCompletionProvider provider, String engineType, JTextComponent textArea, Gutter gutter,
	    boolean forceRun, ScriptingPanel scriptingPanel) {
	this.provider = provider;
	this.textArea = textArea;
	this.gutter = gutter;
	this.forceRun = forceRun;
	setLanguage(engineType);

	textArea.getDocument().addDocumentListener(new AutoVerify());

	localVariables = new HashMap<String, TreeMap<Integer, Class<?>>>();
    }

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
     * @param autocompilation2
     */
    public ScriptingHandler(DefaultCompletionProvider provider, String engineType, JTextComponent textArea, Gutter gutter) {
	this(provider, engineType, textArea, gutter, false);
    }

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
     * @param autocompilation2
     */
    public ScriptingHandler(DefaultCompletionProvider provider, String engineType, JTextComponent textArea, Gutter gutter, boolean forceRun) {
	this(provider, engineType, textArea, gutter, forceRun, null);
    }

    public void setOutput(JTextArea errorOutput) {
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
	engine = ScriptEngineHandler.getEngine(engineType);
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

    public abstract void eval(ScriptEngine engine, String s) throws ScriptException;

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
	    // install functions from ScriptEngineHandler
	    ScriptEngineHandler handler = ScriptEngineHandler.getEngineHandler(engine);
	    ArrayList<Method> functions = handler.getFunctions();

	    ((IcyCompletionProvider) provider).installMethods(functions);
	    installMethods(engine, functions);
	}
    }

    public abstract void installMethods(ScriptEngine engine, ArrayList<Method> functions);

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

    public boolean isNewEngine() {
	return newEngine;
    }

    public void setNewEngine(boolean newEngine) {
	this.newEngine = newEngine;
    }

    public boolean isForceRun() {
	return forceRun;
    }

    public void setForceRun(boolean forceRun) {
	this.forceRun = forceRun;
    }

    public boolean isStrict() {
	return strict;
    }

    public void setStrict(boolean strict) {
	this.strict = strict;
    }

    /**
     * Interpret the script. If <code>exec</code> is true, will try to run the
     * code if compile is successful. Be careful: a building code is not
     * necessary a functionnal running code.
     * 
     * @param runAfterCompile
     *            : if true, runs the code.
     * @param b
     */
    public void interpret(boolean exec) {
	ignoredLines.clear();

	// use either selected text if any or all text
	String s = textArea.getSelectedText();
	if (s == null)
	    s = textArea.getText();

	// interpret the code
	interpret(s);
	if (exec && (isCompilationOk() || forceRun))
	    run();

	ThreadUtil.bgRun(new Runnable() {

	    @Override
	    public void run() {
		if (thread != null) {
		    ThreadUtil.sleep(200);
		}
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
		if (errorOutput != null) {
		    errorOutput.append(textResult);
		}
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
     * 
     * @param s
     *            : the code to interpret.
     * @param exec
     *            : run after compile or not.
     */
    private void interpret(String s) {
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
	    if (provider != null && varInterpretation)
		detectVariables(s, context);
	    setCompilationOk(true);
	} catch (EvaluatorException ee) {
	    // get the line and column of error
	    int lineError = ee.lineNumber() - 1;
	    int columnNumber = ee.columnNumber();
	    if (columnNumber == -1)
		columnNumber = 0;
	    try {
		// verify if exists (> 0 and < lineCount)
		if (lineError >= 0 && lineError <= textArea.getLineOfOffset(s.length() - 1)) {
		    int lineOffset = textArea.getLineStartOffset(lineError);
		    int lineEndOffset = textArea.getLineEndOffset(lineError);

		    String textToRemove = s.substring(lineOffset, lineEndOffset);

		    textToRemove = "\n";
		    if (ignoredLines.containsKey(lineError)) {
			// System.out.println("An error occured with the parsing.");
			return;
		    }
		    ignoredLines.put(lineError, ee);
		    s = s.substring(0, lineOffset) + textToRemove + s.substring(lineEndOffset);

		    // interpret again, without the faulty line.
		    interpret(s);
		} else {
		    // stop interpretation
		    // System.out.println("error at unknown line: " +
		    // lineError);
		    ee.printStackTrace();
		    if (errorOutput != null)
			errorOutput.append(ee.getMessage());
		}
	    } catch (BadLocationException e1) {
		e1.printStackTrace();
	    }
	} catch (ScriptException se) {
	    // get line error
	    int lineError = lineNumber(se) - 1;
	    Integer columnNumberI = columnNumber(se);
	    int columnNumber = columnNumberI != null ? columnNumberI : -1;
	    if (columnNumber == -1)
		columnNumber = 0;
	    try {
		// verify integrity (>0, < lineCount)
		if (lineError >= 0 && lineError <= textArea.getLineOfOffset(s.length() - 1)) {
		    int lineOffset = textArea.getLineStartOffset(lineError);
		    int lineEndOffset = textArea.getLineEndOffset(lineError);

		    s = s.substring(0, lineOffset) + "\n" + s.substring(lineEndOffset);
		    if (ignoredLines.containsKey(lineError)) {
			// System.out.println("An error occured with the error parsing.");
			return;
		    }
		    ignoredLines.put(lineError, se);

		    // interpret again, without the faulty line.
		    interpret(s);
		} else {
		    // stops interpretation
		    System.out.println("error at unknown line: " + lineError);
		    se.printStackTrace();
		    if (errorOutput != null)
			errorOutput.append(se.getMessage());
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

    public void run() {
	if (isNewEngine()) {
	    ArrayList<Method> functions = ScriptEngineHandler.getEngineHandler(engine).getFunctions();
	    ScriptEngine engine = ScriptEngineHandler.getFactory().getEngineByName(this.engine.getFactory().getLanguageName());
	    engine.createBindings();
	    installMethods(engine, functions);
	    thread = new EvalThread(engine, textArea.getText());
	    thread.setPriority(Thread.MIN_PRIORITY);
	    thread.start();
	} else {
	    EvalThread thread = new EvalThread(engine, textArea.getText());
	    thread.start();
	    thread.interrupt();
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
		interpret(false);
		e.consume();
		break;
	    } else if (e.isShiftDown()) {
		break;
	    }

	case KeyEvent.VK_R:
	    if (e.isControlDown())
		interpret(true);
	    break;

	case KeyEvent.VK_M:
	    if (e.isControlDown()) {
		Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		for (String s : bindings.keySet()) {
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
    public Class<?> resolveClassDeclaration(String type) {
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

	if (strict) {
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
	    timer = new Timer(1000, this);
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

		    interpret(s);
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
	    ThreadUtil.invokeLater(new Runnable() {

		@Override
		public void run() {
		    if (PreferencesWindow.getPreferencesWindow().isAutoBuildEnabled())
			timer.restart();
		}
	    });
	}

	@Override
	public void actionPerformed(ActionEvent e) {
	    lastChange = false;
	    interpret(textArea.getText());
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
		// ((IcyCompletionProvider)
		// provider).findBindingsMethods(engine, clazz);
	    }
	}

    }

    /**
     * 
     * @author Thomas Provoost
     * 
     */
    public class EvalThread extends Thread {

	private String s;
	private ScriptEngine evalEngine;

	public EvalThread(ScriptEngine engine, String script) {
	    this.evalEngine = engine;
	    this.s = script;
	}

	@Override
	public void run() {
	    fireEvaluationStarted();
	    evalEngine.getContext().setWriter(pw);
	    evalEngine.getContext().setErrorWriter(pw);
	    try {
		eval(evalEngine, s);

		ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(engine);

		Bindings bn = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		for (String s : bn.keySet()) {
		    List<Completion> completions = provider.getCompletionByInputText(s);
		    boolean found = false;
		    if (completions != null) {
			for (Completion c : completions) {
			    if (c.getReplacementText().contentEquals(s))
				found = true;
			}
		    }
		    if (completions == null || !found) {
			Object value = bn.get(s);
			String type = "";
			if (value != null)
			    type = value.toString();
			provider.addCompletion(new VariableCompletion(provider, s, type));
		    }
		}
		for (String key : localVariables.keySet())
		    engineHandler.getEngineVariables().put(key, localVariables.get(key).lastEntry().getValue());
		engineHandler.getEngineFunctions().putAll(localFunctions);
		engineHandler.getEngineDeclaredImportClasses().addAll(scriptDeclaredImportClasses);
		engineHandler.getEngineDeclaredImports().addAll(scriptDeclaredImports);
		BindingsScriptFrame frame = BindingsScriptFrame.getInstance();
		frame.update();
	    } catch (ThreadDeath td) {
		System.out.println("shutdown");
	    } catch (ScriptException se) {
		int lineError = lineNumber(se) - 1;
		Integer columnNumberI = columnNumber(se);
		int columnNumber = columnNumberI != null ? columnNumberI : -1;
		if (columnNumber == -1)
		    columnNumber = 0;

		// se.printStackTrace();
		if (errorOutput != null)
		    errorOutput.append(se.getMessage());
		else
		    System.out.println(se.getMessage());
	    } finally {
		fireEvaluationOver();
		thread = null;
	    }
	}
    }

    public void killScript() {
	// Something is Running !
	if (thread != null) {
	    thread.stop();
	    thread = null;
	}
    }

    public void fireEvaluationStarted() {
	for (ScriptListener listener : new ArrayList<ScriptListener>(listeners))
	    listener.evaluationStarted();
    }

    public void fireEvaluationOver() {
	for (ScriptListener listener : new ArrayList<ScriptListener>(listeners))
	    listener.evaluationOver();
    }

    public boolean isRunning() {
	return thread != null;
    }

    public void addScriptListener(ScriptListener listener) {
	listeners.add(listener);
    }

    public void removeScriptListener(ScriptListener listener) {
	listeners.remove(listener);
    }

}

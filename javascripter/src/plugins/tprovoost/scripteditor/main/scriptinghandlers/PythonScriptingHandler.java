package plugins.tprovoost.scripteditor.main.scriptinghandlers;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.VariableCompletion;
import org.fife.ui.rtextarea.Gutter;
import org.python.antlr.PythonTree;
import org.python.antlr.base.mod;
import org.python.core.CompilerFlags;
import org.python.core.ParserFacade;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyStringMap;
import org.python.core.PySystemState;
import org.python.jsr223.PyScriptEngine;
import org.python.util.PythonInterpreter;

import sun.org.mozilla.javascript.internal.Context;

public class PythonScriptingHandler extends ScriptingHandler {

    private static PythonInterpreter interpreter;

    public PythonScriptingHandler(DefaultCompletionProvider provider,
	    JTextComponent textArea, Gutter gutter, boolean autocompilation) {
	super(provider, "python", textArea, gutter, autocompilation);
    }

    @Override
    public void eval(ScriptEngine engine, String s) throws ScriptException {
	PythonInterpreter py;
	// tests if new engine or current
	if (this.engine == engine) {
	    // simply run the eval method.
	    // py = new PythonInterpreter();
	    // py.setLocals(interpreter.getLocals());
	    py = interpreter;
	} else {
	    // save the state of the PySystemState
	    py = new PythonInterpreter(new PyStringMap(), new PySystemState());
	    py.setLocals(new PyStringMap());
	}
	py.setOut(engine.getContext().getWriter());
	py.setErr(engine.getContext().getErrorWriter());

	// run the eval
	try {
	    py.exec(s);
	} catch (PyException pe) {
	    try {
		engine.getContext().getErrorWriter().write(pe.toString());
	    } catch (IOException e) {
	    }
	}
    }

    public static void setInterpreter(PythonInterpreter interpreter) {
	PythonScriptingHandler.interpreter = interpreter;
    }

    public static PythonInterpreter getInterpreter() {
	return interpreter;
    }

    @Override
    public void installDefaultLanguageCompletions(String language)
	    throws ScriptException {
	importPythonPackages(engine);

	// IMPORT PLUGINS FUNCTIONS
	importFunctions();

    }

    public void importPythonPackages(ScriptEngine engine)
	    throws ScriptException {
    }

    @Override
    public void autoDownloadPlugins() {
    }

    @Override
    protected void detectVariables(String s, Context context) throws Exception {
	if (engine instanceof PyScriptEngine
		&& engine.getContext() instanceof ScriptContext) {
	    CompilerFlags cflags = Py.getCompilerFlags(0, false);
	    try {
		mod node = ParserFacade.parseExpressionOrModule(
			new StringReader(s), "<script>", cflags);
		if (DEBUG)
		    dumpTree(node);
		registerVariables(node);
	    } catch (Exception e) {
	    }
	}
    }

    public void registerVariables(mod node) {
	for (Completion c : variableCompletions)
	    provider.removeCompletion(c);
	variableCompletions.clear();
	for (PythonTree tree : node.getChildren()) {
	    switch (tree.getAntlrType()) {
	    case 46:
		// assign
		String name = tree.getChild(0).getText();
		Class<?> type = getType(name);
		VariableCompletion c = new VariableCompletion(provider, name,
			type == null ? "" : type.getName());
		c.setDefinedIn(fileName);
		c.setSummary("variable");
		c.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
		boolean alreadyExists = false;
		for (int i = 0; i < variableCompletions.size()
			&& !alreadyExists; ++i) {
		    if (variableCompletions.get(i).compareTo(c) == 0)
			alreadyExists = true;
		}
		if (!alreadyExists)
		    variableCompletions.add(c);
		break;
	    }
	}
	provider.addCompletions(variableCompletions);
    }

    public void dumpTree(mod node) {
	for (PythonTree tree : node.getChildren()) {
	    System.out.println(tree.getType());
	}
    }

    @Override
    public void registerImports() {
    }

    @Override
    protected void organizeImports(JTextComponent textArea2) {
    }

    private Class<?> getType(String varName) {
	return engine.getBindings(ScriptContext.ENGINE_SCOPE).get(varName)
		.getClass();
    }

    @Override
    public void installMethods(ScriptEngine engine, ArrayList<Method> functions) {
	// do nothing
    }

}

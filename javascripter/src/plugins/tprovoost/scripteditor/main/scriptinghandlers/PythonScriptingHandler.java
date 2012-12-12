package plugins.tprovoost.scripteditor.main.scriptinghandlers;

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
import org.python.jsr223.PyScriptEngine;

import sun.org.mozilla.javascript.internal.Context;

public class PythonScriptingHandler extends ScriptingHandler {

    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    public PythonScriptingHandler(DefaultCompletionProvider provider, JTextComponent textArea, Gutter gutter, boolean autocompilation) {
	super(provider, "python", textArea, gutter, autocompilation);
    }

    @Override
    public void installDefaultLanguageCompletions(String language) throws ScriptException {
	importPythonPackages(engine);

	// ScriptEngineHandler engineHandler =
	// ScriptEngineHandler.getEngineHandler(engine);
	// HashMap<String, Class<?>> engineFunctions =
	// engineHandler.getEngineFunctions();
	//
	// // IMPORT A FEW IMPORTANT SEQUENCES, TO BE REMOVED
	// FunctionCompletion c;
	// // ArrayList<Parameter> params = new ArrayList<Parameter>();
	// try {
	// engine.eval("from icy.main import Icy\ndef getSequence() :\n\treturn Icy.getMainInterface().getFocusedSequence()");
	// c = new FunctionCompletion(provider, "getSequence", "Sequence");
	// c.setDefinedIn("MainInterface");
	// c.setReturnValueDescription("The focused sequence is returned.");
	// c.setShortDescription("Returns the sequence under focus. Returns null if no sequence opened.");
	// provider.addCompletion(c);
	// engineFunctions.put("getSequence", Sequence.class);
	// } catch (ScriptException e) {
	// System.out.println(e.getMessage());
	// }
	//
	// try {
	// engine.eval("from icy.main import Icy\ndef getImage() :\n\treturn Icy.getMainInterface().getFocusedImage()");
	// c = new FunctionCompletion(provider, "getImage", "IcyBufferedImage");
	// c.setDefinedIn("MainInterface");
	// c.setShortDescription("Returns the current image viewed in the focused sequence.");
	// c.setReturnValueDescription("Returns the focused Image, returns null if no sequence opened");
	// provider.addCompletion(c);
	// engineFunctions.put("getImage", IcyBufferedImage.class);
	// } catch (ScriptException e) {
	// System.out.println(e.getMessage());
	// }

	// IMPORT PLUGINS FUNCTIONS
	importFunctions();

    }

    public void importPythonPackages(ScriptEngine engine) throws ScriptException {
	// icy important packages
    }

    @Override
    public void autoDownloadPlugins() {
	// TODO Auto-generated method stub

    }

    @Override
    protected void detectVariables(String s, Context context) throws Exception {
	if (engine instanceof PyScriptEngine && engine.getContext() instanceof ScriptContext) {
	    CompilerFlags cflags = Py.getCompilerFlags(0, false);
	    try {
		mod node = ParserFacade.parseExpressionOrModule(new StringReader(s), "<script>", cflags);
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
		VariableCompletion c = new VariableCompletion(provider, name, type == null ? "" : type.getName());
		c.setDefinedIn(fileName);
		c.setSummary("variable");
		c.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
		boolean alreadyExists = false;
		for (int i = 0; i < variableCompletions.size() && !alreadyExists; ++i) {
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
	return engine.getBindings(ScriptContext.ENGINE_SCOPE).get(varName).getClass();
    }

    @Override
    public void installMethods(ArrayList<Method> functions) {
    }

}

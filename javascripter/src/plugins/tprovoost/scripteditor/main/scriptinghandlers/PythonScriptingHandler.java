package plugins.tprovoost.scripteditor.main.scriptinghandlers;

import java.io.StringReader;

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

    public PythonScriptingHandler(DefaultCompletionProvider provider, JTextComponent textArea, Gutter gutter) {
	super(provider, "python", textArea, gutter);
    }

    @Override
    public void installDefaultLanguageCompletions(String language) throws ScriptException {
	importPythonPackages(engine);

    }

    public void importPythonPackages(ScriptEngine engine) throws ScriptException {

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
		VariableCompletion c = new VariableCompletion(provider, tree.getChild(0).getText(), "");
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
	// TODO Auto-generated method stub

    }

    @Override
    protected void organizeImports(JTextComponent textArea2) {
	// TODO Auto-generated method stub

    }

}

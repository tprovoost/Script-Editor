package plugins.tprovoost.scripteditor.scriptinghandlers;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import javax.script.Bindings;
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
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.core.PySystemState;
import org.python.jsr223.PyScriptEngine;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;

import sun.org.mozilla.javascript.internal.Context;

public class PythonScriptingHandler extends ScriptingHandler
{

    private static InteractiveInterpreter interpreter;
    private ScriptEngine engine;

    public PythonScriptingHandler(DefaultCompletionProvider provider, JTextComponent textArea, Gutter gutter,
            boolean autocompilation)
    {
        super(provider, "python", textArea, gutter, autocompilation);
        this.engine = getEngine();
    }

    @Override
    public void eval(ScriptEngine engine, String s) throws ScriptException
    {
        PythonInterpreter py;
        // tests if new engine or current
        // TODO
        if (this.engine == engine)
        {
            // simply run the eval method.
            // py = new PythonInterpreter();
            // py.setLocals(interpreter.getLocals());
            py = interpreter;
        }
        else
        {
            this.engine = engine;
            initializer();

            // Set __name__ == "__main__" (useful for python scripting)
            // Without this, it is "__builtin__"
            PyStringMap dict = new PyStringMap();
            dict.__setitem__("__name__", new PyString("__main__"));

            py = new PythonInterpreter(dict, new PySystemState());
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            for (String s2 : bindings.keySet())
            {
                py.set(s2, bindings.get(s2));
            }
        }
        py.setOut(engine.getContext().getWriter());
        py.setErr(engine.getContext().getErrorWriter());

        // run the eval
        try
        {
            py.exec(s);
            PyStringMap locals = (PyStringMap) py.getLocals();
            Object[] values = locals.values().toArray();
            Object[] keys = locals.keys().toArray();
            for (int i = 0; i < keys.length; ++i)
            {
                engine.put((String) keys[i], values[i]);
            }
        }
        catch (PyException pe)
        {
            try
            {
                engine.getContext().getErrorWriter().write(pe.toString());
            }
            catch (IOException e)
            {
            }
        }
    }

    public static void setInterpreter(InteractiveInterpreter interpreter)
    {
        PythonScriptingHandler.interpreter = interpreter;
    }

    public static PythonInterpreter getInterpreter()
    {
        return interpreter;
    }

    @Override
    public void installDefaultLanguageCompletions(String language) throws ScriptException
    {
        importPythonPackages(getEngine());

        // IMPORT PLUGINS FUNCTIONS
        importFunctions();

    }

    public void importPythonPackages(ScriptEngine engine) throws ScriptException
    {
    }

    @Override
    public void autoDownloadPlugins()
    {
    }

    @Override
    protected void detectVariables(String s, Context context) throws Exception
    {
        ScriptEngine engine = getEngine();
        if (engine instanceof PyScriptEngine && engine.getContext() instanceof ScriptContext)
        {
            CompilerFlags cflags = Py.getCompilerFlags(0, false);
            try
            {
                mod node = ParserFacade.parseExpressionOrModule(new StringReader(s), "<script>", cflags);
                if (DEBUG)
                    dumpTree(node);
                registerVariables(node);
            }
            catch (Exception e)
            {
            }
        }
    }

    public void registerVariables(mod node)
    {
        for (Completion c : variableCompletions)
            provider.removeCompletion(c);
        variableCompletions.clear();
        for (PythonTree tree : node.getChildren())
        {
            switch (tree.getAntlrType())
            {
                case 46:
                    // assign
                    String name = tree.getChild(0).getText();
                    Class<?> type = getType(name);
                    VariableCompletion c = new VariableCompletion(provider, name, type == null ? "" : type.getName());
                    c.setDefinedIn(fileName);
                    c.setSummary("variable");
                    c.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
                    boolean alreadyExists = false;
                    for (int i = 0; i < variableCompletions.size() && !alreadyExists; ++i)
                    {
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

    public void dumpTree(mod node)
    {
        for (PythonTree tree : node.getChildren())
        {
            System.out.println(tree.getType());
        }
    }

    @Override
    public void registerImports()
    {
    }

    @Override
    public void organizeImports(JTextComponent textArea2)
    {
    }

    @Override
    public void format()
    {
        // TODO Auto-generated method stub

    }

    private Class<?> getType(String varName)
    {
        return getEngine().getBindings(ScriptContext.ENGINE_SCOPE).get(varName).getClass();
    }

    @Override
    public void installMethods(ScriptEngine engine, ArrayList<Method> functions)
    {
        // do nothing
    }

    /**
     * Initialize the python interpreter state (paths, etc.)
     */
    public static void initializer()
    {
        // Get preProperties postProperties, and System properties
        Properties postProps = new Properties();
        Properties sysProps = System.getProperties();

        // set default python.home property as a subdirectory named "python"
        // inside Icy dir
        if (sysProps.getProperty("python.home") == null)
        {
            sysProps.put("python.home", "python");
        }

        // put System properties (those set with -D on the command line) in
        // postProps
        Enumeration<?> e = sysProps.propertyNames();
        while (e.hasMoreElements())
        {
            String name = (String) e.nextElement();
            if (name.startsWith("python."))
                postProps.put(name, System.getProperty(name));
        }

        // Here's the initialization step
        PythonInterpreter.initialize(sysProps, postProps, null);

        // TODO add bundled libs from jython.jar
        // PySystemState sys = Py.getSystemState();
        // sys.path.append(new PyString("jython.jar/Lib"));

        // TODO add execnet path (and maybe pip, virtualenv, setuptools) to
        // python.path
        // sys.path.append(new
        // PyString("jython.jar/Lib/site-packages/execnet"));

        // TODO here we could add custom path entries (from a GUI) to
        // python.path
        // sys.path.append(new PyString(gui_configured_path));
    }
}

package plugins.tprovoost.scripteditor.scriptinghandlers.py;

import icy.file.FileUtil;

import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import javax.script.ScriptException;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.VariableCompletion;
import org.fife.ui.rtextarea.Gutter;
import org.mozilla.javascript.Context;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.Attribute;
import org.python.antlr.ast.Call;
import org.python.antlr.ast.Name;
import org.python.antlr.base.mod;
import org.python.core.AstList;
import org.python.core.CompilerFlags;
import org.python.core.ParserFacade;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;

import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngine;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngineHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptingHandler;

public class PythonScriptingHandler extends ScriptingHandler
{

	private static InteractiveInterpreter interpreter;

	public PythonScriptingHandler(DefaultCompletionProvider provider, JTextComponent textArea, Gutter gutter, boolean autocompilation)
	{
		super(provider, "Python", textArea, gutter, autocompilation);
	}

	@Override
	public void evalEngine(ScriptEngine engine, String s) throws ScriptException
	{
		((PyScriptEngine) engine).evalFile(fileName);
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
		CompilerFlags cflags = Py.getCompilerFlags(0, false);
		try
		{
			mod node = ParserFacade.parseExpressionOrModule(new StringReader(s), "<script>", cflags);
			if (DEBUG)
				dumpTree(node);
			registerVariables(node);
		} catch (Exception e)
		{
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
			case 9:
			case 46:
				// assign
				String name = tree.getChild(0).getText();
				Class<?> type = resolveType(tree.getChild(1));
				type = getType(name);
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
			case 24:
				// import from
				// TODO
				// System.out.println("importFrom" + tree);
				break;
			}
		}
		provider.addCompletions(variableCompletions);
	}

	private Class<?> resolveType(PythonTree child)
	{
		if (child instanceof Call)
		{
			return resolveCallType(child);
		}
		return null;
	}

	private Class<?> resolveCallType(PythonTree child)
	{
		String function = buildFunction(child);
		System.out.println("fun: " + function);
		return null;
	}

	private String buildFunction(PythonTree child)
	{
		String callName = "";

		callName = buildFunctionRecursive(callName, child);
		if (!callName.isEmpty())
		{
			// removes the last dot
			if (callName.startsWith("."))
				callName = callName.substring(1);
		}
		return callName;
	}

	private String buildFunctionRecursive(String callName, PythonTree n)
	{
		if (n != null)
		{
			// int type = n.getAntlrType();
			if (n instanceof Call)
			{
				Call fn = ((Call) n);
				String args = "";
				args += "(";
				for (int i = 0; i < ((AstList) fn.getArgs()).size(); ++i)
				{
					Object obj = ((AstList) fn.getArgs()).get(i);
					System.out.println(obj);
					// if (i != 0)
					// args += ",";
					// VariableType typeC = getRealType(arg);
					// if (typeC != null && typeC.getClazz() != null)
					// args += typeC.getClazz().getName();
					// else
					// args += "unknown";
					// i++;
				}
				args += ")";
				String functionName = "";
				PyObject target = fn.getFunc();
				String toReturn = "";
				if (target instanceof Attribute)
				{
					Attribute att = ((Attribute) target);
					PyObject name = att.getAttr();
					toReturn = functionName + name + args + "." + callName;
					return buildFunctionRecursive(toReturn, (PythonTree) (att.getValue()));
				} else if (target instanceof Name)
				{
					toReturn = functionName + ((Name) target).getInternalId() + "." + callName;
				}
				// if (targetType == Token.NAME)
				// {
				// functionName = target.getString();
				// toReturn = functionName + args;
				// }
				// else if (targetType == Token.GETPROP)
				// {
				// functionName = ((PropertyGet) target).getRight().getString();
				// toReturn = buildFunctionRecursive(elem, ((PropertyGet)
				// target).getLeft()) + "." +
				// functionName
				// + args;
				// }
				// else if (targetType == Token.GETELEM)
				// {
				// ElementGet get = (ElementGet) target;
				// elem = buildFunctionRecursive("", get.getElement());
				// functionName = elem.substring(0, elem.indexOf('('));
				// String targetName = buildFunctionRecursive("",
				// get.getTarget());
				// toReturn = targetName + "." + elem;
				// }
				// else
				// toReturn = elem;
				// int rp = fn.getRp();
				// if (rp != -1)
				// {
				// rp = n.getAbsolutePosition() + rp + 1;
				// if (DEBUG)
				// System.out.println("function found:" + functionName);
				// IcyFunctionBlock fb = new IcyFunctionBlock(functionName, rp,
				// null);
				// functionBlocksToResolve.add(fb);
				// }
				return toReturn;
			} else if (n instanceof Name)
			{
				callName = ((Name) n).getInternalId() + "." + callName;
			}

		}
		return callName;
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

	}

	private Class<?> getType(String varName)
	{
		return getEngine().get(varName).getClass();
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

		// set default python.home property as a subdirectory named "Python"
		// inside Icy dir
		if (sysProps.getProperty("python.home") == null)
		{
			String sep = File.separator;
			String path = FileUtil.getCurrentDirectory() + sep + "plugins" + sep + "tlecomte" + sep + "jythonForIcy";
			File f = new File(path);
			if (!f.exists() || !f.isDirectory())
			{
				// fallback to current dir if the above path does not exist or
				// is not a directory
				path = System.getProperty("user.dir");
			}
			sysProps.put("python.home", path);
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

	@Override
	public ScriptEngine getEngine()
	{
		return ScriptEngineHandler.getEngine("python");
	}
}

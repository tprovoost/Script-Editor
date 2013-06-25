package plugins.tprovoost.scripteditor.scriptinghandlers.py;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import javax.script.ScriptException;

import org.python.core.PyException;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;

import plugins.tlecomte.jythonForIcy.JythonLibsManager;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngine;

public class PyScriptEngine extends ScriptEngine
{

	private boolean initialized = false;
	PythonInterpreter py;

	public PyScriptEngine()
	{
		if (!initialized)
		{
			initializer();
			initialized = true;
		}

		// Set __name__ == "__main__" (useful for python scripting)
		// Without this, it is "__builtin__"
		PyStringMap dict = new PyStringMap();
		dict.__setitem__("__name__", new PyString("__main__"));

		// start with a fresh PySystemState
		PySystemState sys = new PySystemState();
		// add path entries for python libs to PySystemState
		new JythonLibsManager().addDirsToPythonPath(sys);
		
		py = new PythonInterpreter(dict, sys);
	}

	@Override
	public void eval(String s) throws ScriptException, PyException
	{
		for (String s2 : bindings.keySet())
		{
			py.set(s2, bindings.get(s2));
		}
		py.setOut(getWriter());
		py.setErr(getErrorWriter());
		try
		{
			py.exec(s);
			PyStringMap locals = (PyStringMap) py.getLocals();
			Object[] values = locals.values().toArray();
			Object[] keys = locals.keys().toArray();
			bindings.clear();
			for (int i = 0; i < keys.length; ++i)
			{
				bindings.put((String) keys[i], values[i]);
			}
		} catch (PyException pe)
		{
			getErrorWriter().write(pe.toString());
		}
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

	@Override
	public String getName()
	{
		return "python";
	}

	@Override
	public void clear()
	{
		HashMap<String, Object> bindings = getBindings();
		for (String s : bindings.keySet())
		{
			bindings.put(s, null);
			py.set(s, null);
		}
	}

	@Override
	protected void putInRealEngine(String name, Object value)
	{
		py.set(name, value);
	}

}
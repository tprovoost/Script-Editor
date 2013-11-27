package plugins.tprovoost.scripteditor.scriptinghandlers.py;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import org.python.core.PyException;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;

import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngine;

public class PyScriptEngine extends ScriptEngine
{
	private static boolean initialized = false;
	private PythonInterpreter py;

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

		py = new PythonInterpreter(dict, sys);
	}

	@Override
	public void eval(String s)
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
			getErrorWriter().flush();
		}
	}

	public void evalFile(String s)
	{
		for (String s2 : bindings.keySet())
		{
			py.set(s2, bindings.get(s2));
		}
		py.setOut(getWriter());
		py.setErr(getErrorWriter());
		try
		{
			py.execfile(s);
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
			getErrorWriter().flush();
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
		
		// let Jython do its housekeeping
		py.cleanup();
	}

	@Override
	protected void putInRealEngine(String name, Object value)
	{
		py.set(name, value);
	}

	@Override
	protected void removeFromRealEngine(String name)
	{
		py.set(name, null);
	}

	public PythonInterpreter getPythonInterpreter()
	{
		return py;
	}

}
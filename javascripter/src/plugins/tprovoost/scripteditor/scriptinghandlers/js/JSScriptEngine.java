package plugins.tprovoost.scripteditor.scriptinghandlers.js;

import icy.plugin.PluginLoader;

import java.util.HashMap;

import javax.script.ScriptException;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptableObject;

import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngine;

public class JSScriptEngine extends ScriptEngine
{

	public ScriptableObject scriptable;

	public JSScriptEngine()
	{
		Context context = Context.enter();
		context.setApplicationClassLoader(PluginLoader.getLoader());
		scriptable = new IcyImporterTopLevel(context);
		Context.exit();
	}

	@Override
	public void eval(String s) throws ScriptException, EvaluatorException
	{
		// uses Context from Rhino integrated in JRE or impossibility
		// to use already defined methods in ScriptEngine, such as println or
		// getImage
		Context context = Context.enter();
		context.setApplicationClassLoader(PluginLoader.getLoader());
		// context.setErrorReporter(errorReporter);
		try
		{
			// ScriptableObject scriptable = new ImporterTopLevel(context);
			// Bindings bs = engine.getBindings(ScriptContext.ENGINE_SCOPE);
			// for (String key : bs.keySet())
			// {
			// Object o = bs.get(key);
			// scriptable.put(key, scriptable, o);
			// }
			Script script = context.compileString(s, "script", 0, null);
			script.exec(context, scriptable);
			// for (Object o : scriptable.getIds())
			// {
			// String key = (String) o;
			// bs.put(key, scriptable.get(key, scriptable));
			// }
		} catch (EvaluatorException e)
		{
			// ignoredLines.put(e.lineNumber(), e);
			// updateGutter();
			// throw e;
			throw new ScriptException(e.getMessage(), e.sourceName(), e.lineNumber() + 1, e.columnNumber());
		} catch (RhinoException e3)
		{
			throw new ScriptException(e3.getMessage(), e3.sourceName(), e3.lineNumber() + 1, e3.columnNumber());
		} finally
		{
			bindings.clear();
			for (Object o : scriptable.getIds())
			{
				bindings.put((String) o, scriptable.get(o));
			}
			Context.exit();
		}
	}

	class IcyImporterTopLevel extends ImporterTopLevel
	{

		/**
         * 
         */
		private static final long serialVersionUID = 1L;

		public IcyImporterTopLevel(Context context)
		{
			super(context);
			String[] names =
			{ "println", "print" };
			defineFunctionProperties(names, IcyImporterTopLevel.class, ScriptableObject.DONTENUM);
		}

		public void println(Object o)
		{
			getWriter().write(Context.toString(o) + "\n");
		}

		public void print(Object o)
		{
			getWriter().write(Context.toString(o));
		}

	}

	@Override
	public String getName()
	{
		return "javascript";
	}

	@Override
	public void clear()
	{
		HashMap<String, Object> bindings = getBindings();
		for (String s : bindings.keySet())
		{
			bindings.put(s, null);
			scriptable.delete(s);
		}
	}

	@Override
	public HashMap<String, Object> getBindings()
	{
		return bindings;
	}

	@Override
	protected void putInRealEngine(String name, Object value)
	{
		scriptable.put(name, scriptable, value);
	}
	
	@Override
	protected void removeFromRealEngine(String name)
	{
		scriptable.delete(name);
	}
}
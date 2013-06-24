package plugins.tprovoost.scripteditor.scriptinghandlers;

import java.io.PrintWriter;
import java.util.HashMap;

import javax.script.ScriptException;

public abstract class ScriptEngine
{
	protected HashMap<String, Object> bindings = new HashMap<String, Object>();
	private PrintWriter pw;
	private PrintWriter pwE;

	public void setWriter(PrintWriter pw)
	{
		this.pw = pw;
	}

	public void setErrorWriter(PrintWriter pwE)
	{
		this.pwE = pwE;
	}

	public abstract void eval(String string) throws ScriptException;

	public PrintWriter getWriter()
	{
		return pw;
	}

	public PrintWriter getErrorWriter()
	{
		return pwE;
	}

	public abstract String getName();

	public void clear()
	{
		HashMap<String, Object> bindings = getBindings();
		for (String s : bindings.keySet())
		{
			bindings.put(s, null);
		}
		bindings.clear();
	}

	protected abstract void putInRealEngine(String name, Object value);

	public void put(String name, Object value)
	{
		putInRealEngine(name, value);
		bindings.put(name, value);
	}

	public Object get(String name)
	{
		return bindings.get(name);
	}

	public HashMap<String, Object> getBindings()
	{
		return bindings;
	}

}
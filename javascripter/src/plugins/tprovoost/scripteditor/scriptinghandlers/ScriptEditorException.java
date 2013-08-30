package plugins.tprovoost.scripteditor.scriptinghandlers;

import javax.script.ScriptException;

public class ScriptEditorException extends ScriptException
{

	private boolean warning;

	public ScriptEditorException(String message, boolean warning)
	{
		super(message);
		this.warning = warning;
	}

	public ScriptEditorException(String message, String fileName, int lineNumber)
	{
		super(message, fileName, lineNumber);
		this.warning = false;
	}

	public ScriptEditorException(String message, String fileName, int lineNumber, boolean warning)
	{
		super(message, fileName, lineNumber);
		this.warning = warning;
	}

	public ScriptEditorException(String message, String fileName, int lineNumber, int columnNumber)
	{
		super(message, fileName, lineNumber, columnNumber);
		this.warning = false;
	}

	public ScriptEditorException(String message, String fileName, int lineNumber, int columnNumber, boolean warning)
	{
		super(message, fileName, lineNumber, columnNumber);
		this.warning = warning;
	}

	public boolean isWarning()
	{
		return warning;
	}

	public void setWarning(boolean warning)
	{
		this.warning = warning;
	}

}

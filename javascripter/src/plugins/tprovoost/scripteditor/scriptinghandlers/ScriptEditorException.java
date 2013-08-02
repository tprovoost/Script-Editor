package plugins.tprovoost.scripteditor.scriptinghandlers;

public class ScriptEditorException
{

	private int line;
	private Exception internalException;
	private boolean isWarning;

	public ScriptEditorException(Exception internalException, int line)
	{
		this(internalException, line, false);
	}

	public ScriptEditorException(Exception internalException, int line, boolean isWarning)
	{
		this.internalException = internalException;
		this.line = line;
		this.isWarning = isWarning;
	}

	public boolean isWarning()
	{
		return isWarning;
	}

	public int getLine()
	{
		return line;
	}

	public Exception getInternalException()
	{
		return internalException;
	}

}

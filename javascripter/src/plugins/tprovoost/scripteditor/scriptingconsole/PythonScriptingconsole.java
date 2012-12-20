package plugins.tprovoost.scripteditor.scriptingconsole;

import icy.util.DateUtil;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import javax.script.ScriptEngineFactory;
import javax.swing.JTextArea;

import org.python.util.InteractiveConsole;

public class PythonScriptingconsole extends Scriptingconsole
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private ArrayList<String> history = new ArrayList<String>();
    private int posInHistory = 0;
    private JTextArea output;
    private InteractiveConsole console;

    public PythonScriptingconsole()
    {
	addKeyListener(this);

	console = new InteractiveConsole();

	setMinimumSize(new Dimension(0, 25));
	setPreferredSize(new Dimension(0, 25));
    }

    public void setLanguage(String language)
    {

    }

    @Override
    public void keyTyped(KeyEvent e)
    {
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
	String text = getText();
	switch (e.getKeyCode())
	{
	case KeyEvent.VK_DOWN:
	    posInHistory = (posInHistory + 1) % history.size();
	    setText(history.get(posInHistory));
	    break;

	case KeyEvent.VK_UP:
	    posInHistory = posInHistory == 0 ? history.size() - 1 : posInHistory - 1;
	    setText(history.get(posInHistory));
	    break;

	case KeyEvent.VK_ENTER:
	    if (!text.isEmpty())
	    {
		String time = DateUtil.now("HH:mm:ss");
		if (output != null)
		    output.append("> " + text + "\n");
		else
		    System.out.println(time + ": " + text);
		// PyObject locals = console.getLocals();
		// console.setLocals(new PyStringMap());
		console.push(text);
		// console.setLocals(locals);
		if (history.isEmpty() || !history.get(posInHistory).contentEquals(text))
		    history.add(text);
		setText("");
		posInHistory = 0;
		BindingsScriptFrame.getInstance().update();
	    }
	    break;
	}
    }

    @Override
    public void keyReleased(KeyEvent e)
    {

    }

    /**
     * Get the String language corresponding to the engine factory.<br/>
     * Ex: ECMAScript factory returns JavaScript.
     * 
     * @param factory
     * @return
     */
    public String getLanguageName(ScriptEngineFactory factory)
    {
	String languageName = factory.getLanguageName();
	if (languageName.contentEquals("ECMAScript"))
	    return "javascript";
	if (languageName.contentEquals("python"))
	    return "python";
	return languageName;
    }

    public void setOutput(JTextArea output)
    {
	this.output = output;
    }

}

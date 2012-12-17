package plugins.tprovoost.scripteditor.scriptingconsole;

import icy.util.DateUtil;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngineFactory;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.fife.ui.autocomplete.Completion;

import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scripteditor.main.scriptinghandlers.JSScriptingHandler6;
import plugins.tprovoost.scripteditor.main.scriptinghandlers.PythonScriptingHandler;
import plugins.tprovoost.scripteditor.main.scriptinghandlers.ScriptingHandler;

public class Scriptingconsole extends JTextField implements KeyListener
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private IcyCompletionProvider provider;
    private ScriptingHandler scriptHandler;
    private ArrayList<String> history = new ArrayList<String>();
    private int posInHistory = 0;
    private JTextArea output;

    public Scriptingconsole()
    {
	addKeyListener(this);

	provider = new IcyCompletionProvider();
	provider.installDefaultCompletions("javascript");
	scriptHandler = new JSScriptingHandler6(provider, this, null, false);
	scriptHandler.setNewEngine(false);
	scriptHandler.setForceRun(false);
	scriptHandler.setStrict(false);

	setMinimumSize(new Dimension(0, 25));
	setPreferredSize(new Dimension(0, 25));
    }

    public void setLanguage(String language)
    {
	provider.clear();
	if (language.contentEquals("javascript"))
	{
	    provider = new IcyCompletionProvider();
	    provider.installDefaultCompletions("javascript");
	    scriptHandler = new JSScriptingHandler6(provider, this, null, false);
	}
	else if (language.contentEquals("python"))
	{
	    provider.installDefaultCompletions("python");
	    scriptHandler = new PythonScriptingHandler(provider, this, null, false);
	}
	else
	{
	    scriptHandler = null;
	}
	if (scriptHandler != null)
	{
	    scriptHandler.setNewEngine(false);
	    scriptHandler.setForceRun(false);
	    scriptHandler.setStrict(false);
	}
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
	case KeyEvent.VK_SPACE:
	    e.consume();
	    List<Completion> completions = provider.getCompletions(this);
	    if (completions == null || !e.isControlDown())
	    {
		return;
	    }
	    if (completions.size() == 1)
	    {
		this.setText(completions.get(0).getReplacementText());
	    }
	    else
	    {
		int i = 0;
		String s = "";
		for (Completion c : completions)
		{
		    s += c.getReplacementText() + " ";
		    if (i != 0 && i % 10 == 0)
			s += "\n";
		}
		if (!s.endsWith("\n"))
		    s += "\n";
		System.out.println(s);
	    }
	    break;
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
		    output.append("> "+ text + "\n");
		else
		    System.out.println(time + ": " + text);
		scriptHandler.interpret(true);
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
	if (scriptHandler != null)
	    scriptHandler.setOutput(output);
    }

}

package plugins.tprovoost.scripteditor.scriptingconsole;

import icy.util.DateUtil;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;

import javax.script.ScriptEngineFactory;
import javax.swing.JTextArea;

import org.python.core.PyException;
import org.python.util.InteractiveConsole;

import plugins.tprovoost.scripteditor.main.scriptinghandlers.PythonScriptingHandler;

public class PythonScriptingconsole extends Scriptingconsole {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private ArrayList<String> history = new ArrayList<String>();
    private int posInHistory = 0;
    private JTextArea output;
    private InteractiveConsole console;

    public PythonScriptingconsole() {
	addKeyListener(this);

	console = new InteractiveConsole();

	if (PythonScriptingHandler.getInterpreter() == null) {
	    PythonScriptingHandler.setInterpreter(console);
	}

	setMinimumSize(new Dimension(0, 25));
	setPreferredSize(new Dimension(0, 25));
    }

    public void setLanguage(String language) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
	String text = getText();
	switch (e.getKeyCode()) {
	case KeyEvent.VK_DOWN:
	    if (posInHistory < history.size()) {
		posInHistory = (posInHistory + 1) % history.size();
		setText(history.get(posInHistory));
	    }
	    break;

	case KeyEvent.VK_UP:
	    if (posInHistory > 0) {
		posInHistory = posInHistory == 0 ? history.size() - 1 : posInHistory - 1;
		setText(history.get(posInHistory));
	    }
	    break;

	case KeyEvent.VK_ENTER:
	    if (!text.isEmpty()) {
		String time = DateUtil.now("HH:mm:ss");
		if (output != null)
		    output.append(">>> " + text + "\n");
		else
		    System.out.println(time + ": " + text);
		try {
		    console.setOut(scriptHandler.getEngine().getContext().getWriter());
		    console.setErr(scriptHandler.getEngine().getContext().getWriter());
		    console.push(text);
		} catch (PyException pe) {
		    try {
			scriptHandler.getEngine().getContext().getWriter().write(pe.toString());
		    } catch (IOException e1) {
		    }
		}
		if (history.isEmpty() || !history.get(posInHistory).contentEquals(text))
		    history.add(0, text);
		setText("");
		posInHistory = 0;
		BindingsScriptFrame.getInstance().update();
	    }
	    break;
	}
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    /**
     * Get the String language corresponding to the engine factory.<br/>
     * Ex: ECMAScript factory returns JavaScript.
     * 
     * @param factory
     * @return
     */
    public String getLanguageName(ScriptEngineFactory factory) {
	String languageName = factory.getLanguageName();
	if (languageName.contentEquals("ECMAScript"))
	    return "javascript";
	if (languageName.contentEquals("python"))
	    return "python";
	return languageName;
    }

    public void setOutput(JTextArea output) {
	this.output = output;
    }

}

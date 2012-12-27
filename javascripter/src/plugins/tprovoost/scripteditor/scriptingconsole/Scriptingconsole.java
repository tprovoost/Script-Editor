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

public class Scriptingconsole extends JTextField implements KeyListener {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final int MAX_PER_LINE = 5;
    protected IcyCompletionProvider provider;
    protected ScriptingHandler scriptHandler;
    protected ArrayList<String> history = new ArrayList<String>();
    protected int posInHistory = 0;
    protected JTextArea output;

    public Scriptingconsole() {
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

    public void setLanguage(String language) {
	provider.clear();
	if (language.contentEquals("javascript")) {
	    provider = new IcyCompletionProvider();
	    provider.installDefaultCompletions("javascript");
	    scriptHandler = new JSScriptingHandler6(provider, this, null, false);
	} else if (language.contentEquals("python")) {
	    provider.installDefaultCompletions("python");
	    scriptHandler = new PythonScriptingHandler(provider, this, null, false);
	} else {
	    scriptHandler = null;
	}
	if (scriptHandler != null) {
	    scriptHandler.setNewEngine(false);
	    scriptHandler.setForceRun(false);
	    scriptHandler.setStrict(false);
	}
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
	String text = getText();
	switch (e.getKeyCode()) {
	case KeyEvent.VK_SPACE:
	    e.consume();
	    List<Completion> completions = provider.getCompletions(this);
	    if (completions == null || !e.isControlDown()) {
		return;
	    }
	    if (completions.size() == 1) {
		this.setText(completions.get(0).getReplacementText());
	    } else {
		int i = 0;
		String s = "";
		for (Completion c : completions) {
		    s += c.getReplacementText() + "\t";
		    if (i != 0 && i % MAX_PER_LINE == 0)
			s += "\n";
		    ++i;
		}
		if (!s.endsWith("\n"))
		    s += "\n";
		output.append(s);
		System.out.println(s);
	    }
	    break;
	case KeyEvent.VK_UP:
	    if (posInHistory < history.size() - 1) {
		++posInHistory;
		setText(history.get(posInHistory));
		e.consume();
	    }
	    break;

	case KeyEvent.VK_DOWN:
	    if (posInHistory > 0) {
		--posInHistory;
		setText(history.get(posInHistory));
		e.consume();
	    }
	    break;

	case KeyEvent.VK_ENTER:
	    if (!text.isEmpty()) {
		String time = DateUtil.now("HH:mm:ss");
		if (output != null)
		    output.append("> " + text + "\n");
		else
		    System.out.println(time + ": " + text);
		scriptHandler.interpret(true);
		history.add(0, text);
		setText("");
		posInHistory = -1;
		BindingsScriptFrame.getInstance().update();
		e.consume();
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
	if (scriptHandler != null)
	    scriptHandler.setOutput(output);
    }

    public void clear() {
	if (output != null)
	    output.setText("");
    }

}

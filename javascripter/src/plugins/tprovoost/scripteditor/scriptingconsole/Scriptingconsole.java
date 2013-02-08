package plugins.tprovoost.scripteditor.scriptingconsole;

import icy.util.DateUtil;
import icy.util.EventUtil;

import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngineFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Style;

import org.fife.ui.autocomplete.Completion;

import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scripteditor.scriptinghandlers.JSScriptingHandlerRhino;
import plugins.tprovoost.scripteditor.scriptinghandlers.PythonScriptingHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptingHandler;
// import plugins.tprovoost.scripteditor.scriptinghandlers.JSScriptingHandler62;

// import plugins.tprovoost.scripteditor.main.scriptinghandlers.JSScriptingHandler7;

public class Scriptingconsole extends JTextField implements KeyListener, MouseListener
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final int MAX_PER_LINE = 5;
    protected IcyCompletionProvider provider;
    protected ScriptingHandler scriptHandler;
    protected ArrayList<String> history = new ArrayList<String>();
    protected int posInHistory = 0;
    protected JTextPane output;

    public Scriptingconsole()
    {
        addKeyListener(this);
        addMouseListener(this);

        provider = new IcyCompletionProvider();
        provider.installDefaultCompletions("javascript");
        // // if (System.getProperty("java.version").startsWith("1.6.")) {
        // scriptHandler = new JSScriptingHandler62(provider, this, null,
        // false);
        // // } else {
        scriptHandler = new JSScriptingHandlerRhino(provider, this, null, false);
        // // }
        scriptHandler.setNewEngine(false);
        scriptHandler.setForceRun(false);
        scriptHandler.setStrict(false);
        scriptHandler.setVarInterpretation(false);

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
            // if (System.getProperty("java.version").startsWith("1.6."))
            // scriptHandler = new JSScriptingHandler62(provider, this, null,
            // false);
            // else
            scriptHandler = new JSScriptingHandlerRhino(provider, this, null, false);
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
            scriptHandler.setVarInterpretation(false);
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
                        s += c.getReplacementText() + "\t";
                        if (i != 0 && i % MAX_PER_LINE == 0)
                            s += "\n";
                        ++i;
                    }
                    if (!s.endsWith("\n"))
                        s += "\n";
                    Document doc = output.getDocument();
                    try
                    {
                        Style style = output.getStyle("normal");
                        if (style == null)
                            style = output.addStyle("normal", null);
                        doc.insertString(doc.getLength(), s, style);
                    }
                    catch (BadLocationException e2)
                    {
                    }
                    System.out.println(s);
                }
                break;
            case KeyEvent.VK_UP:
                if (posInHistory < history.size() - 1)
                {
                    ++posInHistory;
                    setText(history.get(posInHistory));
                    e.consume();
                }
                break;

            case KeyEvent.VK_DOWN:
                if (posInHistory > 0)
                {
                    --posInHistory;
                    setText(history.get(posInHistory));
                    e.consume();
                }
                break;

            case KeyEvent.VK_ENTER:
                if (!text.isEmpty())
                {
                    String time = DateUtil.now("HH:mm:ss");
                    if (output != null)
                    {
                        Document doc = output.getDocument();
                        try
                        {
                            Style style = output.getStyle("normal");
                            if (style == null)
                                style = output.addStyle("normal", null);
                            doc.insertString(doc.getLength(), "> " + text + "\n", style);
                        }
                        catch (BadLocationException e2)
                        {
                        }
                    }
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

    public void setOutput(JTextPane output)
    {
        this.output = output;
        if (scriptHandler != null)
            scriptHandler.setOutput(output);
    }

    public void clear()
    {
        if (output != null)
            output.setText("");
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {
        if (EventUtil.isRightMouseButton(e))
        {
            JPopupMenu popup = new JPopupMenu();
            JMenuItem itemCopy = new JMenuItem("Paste");
            itemCopy.addActionListener(new ActionListener()
            {

                @Override
                public void actionPerformed(ActionEvent e)
                {
                    try
                    {
                        String paste = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                                .getData(DataFlavor.stringFlavor);
                        String curText = getText();
                        int pos = getCaretPosition();
                        String before = curText.substring(0, pos);
                        String after = curText.substring(pos, curText.length());
                        setText(before + paste + after);
                    }
                    catch (HeadlessException e1)
                    {
                    }
                    catch (UnsupportedFlavorException e1)
                    {
                    }
                    catch (IOException e1)
                    {
                    }
                }
            });
            popup.add(itemCopy);
            popup.show(this, e.getX(), e.getY());
            e.consume();
        }
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
    }

    @Override
    public void mouseEntered(MouseEvent e)
    {
    }

    @Override
    public void mouseExited(MouseEvent e)
    {
    }

}

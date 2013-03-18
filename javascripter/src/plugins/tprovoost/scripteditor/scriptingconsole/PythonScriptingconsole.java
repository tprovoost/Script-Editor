package plugins.tprovoost.scripteditor.scriptingconsole;

import icy.util.DateUtil;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Style;

import org.python.core.PyException;
import org.python.util.InteractiveConsole;

import plugins.tprovoost.scripteditor.scriptinghandlers.PythonScriptingHandler;

public class PythonScriptingconsole extends Scriptingconsole
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private InteractiveConsole console;

    public PythonScriptingconsole()
    {

        // initialize the global python system state (done once)
        PythonScriptingHandler.initializer();

        // Note: there is no way to reset the system state for an InteractiveConsole,
        // so sys.path (for example) is shared for all instances of them !
        console = new InteractiveConsole();

        if (PythonScriptingHandler.getInterpreter() == null)
        {
            PythonScriptingHandler.setInterpreter(console);
        }

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
                            doc.insertString(doc.getLength(), ">>> " + text + "\n", style);
                        }
                        catch (BadLocationException e2)
                        {
                        }
                    }
                    else
                        System.out.println(time + ": " + text);
                    try
                    {
                        console.push(text);
                    }
                    catch (PyException pe)
                    {
                        try
                        {
                            scriptHandler.getEngine().getContext().getWriter().write(pe.toString());
                        }
                        catch (IOException e1)
                        {
                        }
                    }
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

    public void setOutput(JTextPane outputNew)
    {
        output = outputNew;
        final StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true)
        {
            @Override
            public void write(String s)
            {
                if (output != null)
                {
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
                }
            }
        };
        console.setOut(pw);
        console.setErr(pw);
    }

}

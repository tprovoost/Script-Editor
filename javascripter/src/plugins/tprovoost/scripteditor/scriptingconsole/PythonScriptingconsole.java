package plugins.tprovoost.scripteditor.scriptingconsole;

import icy.util.DateUtil;
import icy.util.GraphicsUtil;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Style;

import org.python.core.PyException;
import org.python.core.PySystemState;
import org.python.core.imp;
import org.python.util.InteractiveConsole;

import plugins.tlecomte.jythonForIcy.JythonLibsManager;
import plugins.tprovoost.scripteditor.scriptinghandlers.py.PyScriptEngine;
import plugins.tprovoost.scripteditor.scriptinghandlers.py.PythonScriptingHandler;

public class PythonScriptingconsole extends Scriptingconsole
{

	/**
     * 
     */
	private static final long serialVersionUID = 1L;
	private static final String STRING_INPUT = ">>> ";
	private static final String STRING_INPUT_MORE = "... ";

	private InteractiveConsole console;
	private boolean waitingForMore;

	public PythonScriptingconsole()
	{
		// Note: there is no way to reset the system state for an
		// InteractiveConsole,
		// so sys.path (for example) is shared for all instances of them!
		console = new InteractiveConsole();
		
		// add path entries for python libs to PySystemState
		PySystemState sys = console.getSystemState();
		new JythonLibsManager().addDirsToPythonPath(sys);

		// Jython 2.5 does not import the site module (see http://bugs.jython.org/issue1552)
		// The site modile is responsible for importing sitecustomize.py,
		// a script that a user can create to tweak its setup (import paths, environment variables)
		imp.load("site");
		

		if (PythonScriptingHandler.getInterpreter() == null)
		{
			PythonScriptingHandler.setInterpreter(console);
		}

		// setText(STRING_INPUT);

		setMinimumSize(new Dimension(0, 25));
		setPreferredSize(new Dimension(0, 25));

		Insets insets = getMargin();
		if (insets != null)
		{
			setMargin(new Insets(insets.top, 30, insets.bottom, insets.right));
		} else
		{
			setMargin(new Insets(0, 30, 0, 0));
		}

	}

	@Override
	public boolean isEditable()
	{
		return super.isEditable();
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
						if (waitingForMore)
							doc.insertString(doc.getLength(), STRING_INPUT_MORE + getText() + "\n", style);
						else
							doc.insertString(doc.getLength(), STRING_INPUT + getText() + "\n", style);
					} catch (BadLocationException e2)
					{
					}
				} else
					System.out.println(time + ": " + text);
				try
				{
					waitingForMore = console.push(text);
				} catch (PyException pe)
				{
					scriptHandler.getEngine().getWriter().write(pe.toString());
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
					} catch (BadLocationException e2)
					{
					}
				}
			}
		};
		console.setOut(pw);
		console.setErr(pw);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setColor(Color.BLACK);
		if (waitingForMore)
			GraphicsUtil.drawCenteredString(g2, STRING_INPUT_MORE, getMargin().left - 10, getHeight() / 2, false);
		else
			GraphicsUtil.drawCenteredString(g2, STRING_INPUT, getMargin().left - 10, getHeight() / 2, false);
		g2.dispose();
	}
}

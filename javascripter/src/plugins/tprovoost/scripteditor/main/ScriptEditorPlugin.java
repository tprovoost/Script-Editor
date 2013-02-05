package plugins.tprovoost.scripteditor.main;

import icy.gui.frame.IcyFrameAdapter;
import icy.gui.frame.IcyFrameEvent;
import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.plugin.abstract_.PluginActionable;
import icy.system.thread.ThreadUtil;

import java.util.ArrayList;

import javax.script.ScriptEngineManager;

import plugins.tprovoost.scripteditor.gui.ScriptingEditor;
import plugins.tprovoost.scripteditor.gui.ScriptingPanel;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;

public class ScriptEditorPlugin extends PluginActionable
{

    private static ArrayList<ScriptingEditor> editors = new ArrayList<ScriptingEditor>();

    @Override
    public void run()
    {
        String jv = System.getProperty("java.version");
        // if (!jv.startsWith("1.6."))
        // {
        // MessageDialog
        // .showDialog("This Plugin is only compatible with Java 6. A version for Java 7 will be released in the future.");
        // return;
        // }
        if (new ScriptEngineManager().getEngineFactories().isEmpty())
        {
            new FailedAnnounceFrame("No interpreter found. Impossible to compile/run a script.");
            System.out.println("No interpreter found. Impossible to compile/run a script.");
            return;
        }
        final ScriptingEditor frame = new ScriptingEditor();
        frame.setSize(500, 500);
        frame.addToMainDesktopPane();
        frame.setVisible(true);
        frame.requestFocus();

        editors.add(frame);
        frame.addFrameListener(new IcyFrameAdapter()
        {
            @Override
            public void icyFrameClosed(IcyFrameEvent e)
            {
                frame.removeFrameListener(this);
                editors.remove(frame);
            }
        });
    }

    @BindingFunction(value = "displayHelloString")
    public static void displayHello(String h)
    {
        System.out.println("Hello " + h + "!");
    }

    @BindingFunction(value = "displayHelloInt")
    public void displayHello(Object a)
    {
        System.out.println("Hello " + a + "!");
    }

    @BindingFunction(value = "getThisSEP")
    public ScriptEditorPlugin getThis(String lol)
    {
        return this;
    }

    @BindingFunction(value = "fnTest")
    public static int testFuncStatic()
    {
        return 42;
    }

    // TODO test
    public static void openInScriptEditor(final String text)
    {
        if (editors.isEmpty())
        {
            ThreadUtil.bgRun(new Runnable()
            {

                @Override
                public void run()
                {
                    new ScriptEditorPlugin().run();
                    ScriptingPanel panel = editors.get(0).createNewPane("editor");
                    panel.getTextArea().setText(text);
                }
            });

        }
        else
        {
            new ScriptEditorPlugin().run();
            ScriptingPanel panel = editors.get(0).createNewPane("editor");
            panel.getTextArea().setText(text);
        }
    }
}

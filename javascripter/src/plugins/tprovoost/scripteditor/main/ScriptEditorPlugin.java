package plugins.tprovoost.scripteditor.main;

import icy.gui.dialog.MessageDialog;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.plugin.abstract_.PluginActionable;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

import javax.script.ScriptEngineManager;

import plugins.tprovoost.scripteditor.gui.ScriptingEditor;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;

public class ScriptEditorPlugin extends PluginActionable {

    @Override
    public void run() {
	String jv = System.getProperty("java.version");
	if (!jv.startsWith("1.6.")) {
	    MessageDialog.showDialog("This Plugin is only compatible with Java 6. A version for Java 7 will be released in the future.");
	    return;
	}
	if (new ScriptEngineManager().getEngineFactories().isEmpty()) {
	    new FailedAnnounceFrame("No interpreter found. Impossible to compile/run a script.");
	    System.out.println("No interpreter found. Impossible to compile/run a script.");
	    return;
	}
	IcyFrame frame = new ScriptingEditor();
	frame.setSize(500, 500);
	frame.addToMainDesktopPane();
	frame.setVisible(true);
	frame.requestFocus();
    }

    @BindingFunction(value = "displayHelloString")
    public static void displayHello(String h) {
	System.out.println("Hello " + h + "!");
    }
    
    @BindingFunction(value = "displayHelloInt")
    public void displayHello(Object a) {
	System.out.println("Hello " + a + "!");
    }

    @BindingFunction(value = "getThisSEP")
    public ScriptEditorPlugin getThis(String lol) {
	return this;
    }
    
    @BindingFunction(value = "fnTest")
    public static int testFuncStatic() {
	return 42;
    }

    /**
     * Get the Working Directory filename.
     * 
     * @return The correct Directory or null if an error occurred.
     */
    public static String getIcyWorkingDirectory() {
	ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

	URL packageURL = classLoader.getResource("icy");

	// build jar file name, then loop through zipped entries
	String jarFileName;
	try {
	    jarFileName = URLDecoder.decode(packageURL.getFile(), "UTF-8");
	    jarFileName = jarFileName.substring(5, jarFileName.indexOf("!") - "icy.jar".length());
	    return jarFileName;
	} catch (UnsupportedEncodingException e) {
	    e.printStackTrace();
	}
	return null;
    }
}

package plugins.tprovoost.scripteditor.main;

import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.plugin.abstract_.PluginActionable;
import icy.sequence.Sequence;

import java.io.BufferedInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

import org.xeustechnologies.jcl.JarClassLoader;

import plugins.tprovoost.scripteditor.gui.ScriptingEditor;
import plugins.tprovoost.scripteditor.main.scriptinghandlers.ScriptingHandler;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;

public class ScriptEditorPlugin extends PluginActionable {

    @Override
    public void run() {
	ClassLoader clazzLoader = getClass().getClassLoader();
	if (clazzLoader instanceof JarClassLoader) {
	    ((JarClassLoader) clazzLoader).add(new BufferedInputStream(clazzLoader
		    .getResourceAsStream("plugins/tprovoost/scripteditor/neededPackages/autocomplete.jar")));
	    ((JarClassLoader) clazzLoader).add(new BufferedInputStream(clazzLoader
		    .getResourceAsStream("plugins/tprovoost/scripteditor/neededPackages/rsyntaxtextarea.jar")));
	    // ((JarClassLoader) clazzLoader).add(new
	    // BufferedInputStream(clazzLoader.getResourceAsStream("plugins/tprovoost/scripteditor/neededPackages/jython.jar")));
	}
	if (ScriptingHandler.factory.getEngineFactories().isEmpty()) {
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

    @BindingFunction(value = "displayHello", pluginClassName = "plugins.tprovoost.scripteditor.main.ScriptEditorPlugin")
    public static void displayHello(String h, int b, Sequence s) {
	System.out.println("Hello World!");
    }

    @BindingFunction(value = "displayHello", pluginClassName = "plugins.tprovoost.scripteditor.main.ScriptEditorPlugin")
    public static void displayHello(String h) {
	System.out.println("Hello " + h + "!");
    }

    @BindingFunction(value = "displayHello", pluginClassName = "plugins.tprovoost.scripteditor.main.ScriptEditorPlugin")
    public void displayHello(int a) {
	System.out.println("Hello " + a + "!");
    }
    
    @BindingFunction(value = "getThis", pluginClassName = "plugins.tprovoost.scripteditor.main.ScriptEditorPlugin")
    public ScriptEditorPlugin getThis() {
	return this;
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

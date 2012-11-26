package plugins.tprovoost.scripteditor.completion;

import icy.util.ClassUtil;

import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;

public abstract class IcyAutoCompletion extends AutoCompletion {

    public IcyAutoCompletion(CompletionProvider provider) {
	super(provider);
    }

    public boolean packageExists(String neededPackage) {
	return getTextComponent().getText().contains(neededPackage);
    }

    public boolean classAlreadyImported(String neededClass) {
	String text = getTextComponent().getText();

	// test if contains the class or if contains the importPackage enclosing
	// the class
	return text.contains("importClass(Packages." + neededClass + ")")
		|| text.contains("importPackage(Packages." + ClassUtil.getPackageName(neededClass) + ")");
    }

    /**
     * Add an import into the textcomponent if necessary.
     * 
     * @param tc
     *            : the text component
     * @param neededClass
     *            : the class to be added
     * @param isClass
     *            : is is a class or a package ?
     */
    public abstract void addImport(JTextComponent tc, String neededClass, boolean isClass);
}

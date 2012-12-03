package plugins.tprovoost.scripteditor.completion;

import icy.util.ClassUtil;

import java.lang.reflect.Method;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;

import plugins.tprovoost.scripteditor.completion.types.BasicJavaClassCompletion;
import plugins.tprovoost.scripteditor.main.scriptinghandlers.JSScriptingHandler;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion;

public class JSAutoCompletion extends IcyAutoCompletion {

    public JSAutoCompletion(CompletionProvider provider) {
	super(provider);
    }

    @Override
    protected void insertCompletion(Completion c, boolean typedParamListStartChar) {
	super.insertCompletion(c, typedParamListStartChar);
	if (c instanceof ScriptFunctionCompletion) {
	    JTextComponent tc = getTextComponent();
	    Method m = ((ScriptFunctionCompletion) c).getMethod();
	    if (m != null) {
		String neededClass = m.getDeclaringClass().getName();
		if (!classAlreadyImported(neededClass)) {
		    addImport(tc, neededClass, true);
		    JSScriptingHandler.organizeImportsStatic(tc);
		}
	    }
	} else if (c instanceof BasicJavaClassCompletion) {
	    JTextComponent tc = getTextComponent();
	    addImport(tc, ((BasicJavaClassCompletion) c).getClazz().getName(), true);
	}
    }

    @Override
    protected String getReplacementText(Completion c, Document doc, int start, int len) {
	String toReturn = super.getReplacementText(c, doc, start, len);
	if (c instanceof ScriptFunctionCompletion) {
	    ScriptFunctionCompletion fc = (ScriptFunctionCompletion) c;
	    String textBefore = "";
	    if (!fc.isStatic()) {
		CompletionProvider provider = getCompletionProvider();
		if (provider instanceof IcyCompletionProvider) {
		    textBefore = provider.getAlreadyEnteredText(getTextComponent());
		    int lastIdx = textBefore.lastIndexOf('.');
		    if (lastIdx != -1)
			textBefore = textBefore.substring(0, lastIdx + 1);
		    else
			textBefore += '.';
		}
	    }
	    toReturn = textBefore + fc.getMethod().getName();
	} else if (c instanceof BasicJavaClassCompletion) {
	    Class<?> clazz = ((BasicJavaClassCompletion) c).getClazz();
	    toReturn = clazz.getSimpleName();
	}
	return toReturn;
    }

    public void addImport(JTextComponent tc, String neededClass, boolean isClass) {
	String resultingImport;
	if (isClass)
	    resultingImport = "importClass(Packages." + neededClass + ")\n";
	else
	    resultingImport = "importPackage(Packages." + ClassUtil.getPackageName(neededClass) + ")\n";

	if (!tc.getText().contains(resultingImport))
	    // add at the beginning
	    tc.setText(resultingImport + "\n" + tc.getText());
    }

}

package plugins.tprovoost.scripteditor.completion;

import icy.util.ClassUtil;

import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.CompletionProvider;

public class PythonAutoCompletion extends IcyAutoCompletion {

	public PythonAutoCompletion(CompletionProvider provider) {
		super(provider);
	}

	public void addImport(JTextComponent tc, String neededClass, boolean isClass) {
		String resultingImport = "";
		String packageName = ClassUtil.getPackageName(neededClass);
		if (!packageName.isEmpty()) {
			if (!isClass)
				return;
			resultingImport += "from " + packageName + " ";
		}
		if (isClass)
			resultingImport += "import " + ClassUtil.getSimpleClassName(neededClass);
		else {
			int lastIdxDot = packageName.lastIndexOf('.');
			if (lastIdxDot != -1)
				resultingImport += "as " + packageName.substring(lastIdxDot);
			else
				resultingImport += "as " + packageName;
		}
		resultingImport += "\n";

		// add at the beginning
		tc.setText(resultingImport + "\n" + tc.getText());
	}

}

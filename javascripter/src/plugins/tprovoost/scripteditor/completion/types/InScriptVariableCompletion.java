package plugins.tprovoost.scripteditor.completion.types;

import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.VariableCompletion;

public class InScriptVariableCompletion extends VariableCompletion {

	public InScriptVariableCompletion(CompletionProvider provider, String name, String type) {
		super(provider, name, type);
	}

}

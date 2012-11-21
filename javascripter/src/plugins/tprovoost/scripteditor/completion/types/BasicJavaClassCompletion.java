package plugins.tprovoost.scripteditor.completion.types;

import java.lang.reflect.Modifier;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.VariableCompletion;

public class BasicJavaClassCompletion extends VariableCompletion implements Completion {

	private Class<?> clazz;

	public BasicJavaClassCompletion(CompletionProvider provider, Class<?> clazz) {
		super(provider, clazz.getSimpleName(), clazz.getSimpleName());
		this.setDefinedIn(clazz.getName());
		this.clazz = clazz;
	}
	
	public Class<?> getClazz() {
		return clazz;
	}
	
	public boolean isAbstract() {
		return Modifier.isAbstract(clazz.getModifiers());
	}

}

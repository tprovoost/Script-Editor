package plugins.tprovoost.scripteditor.completion.types;

import java.lang.reflect.Modifier;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;

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

    @Override
    public String getAlreadyEntered(JTextComponent comp) {
	// return super.getAlreadyEntered(comp);
	Document doc = comp.getDocument();
	Segment seg = new Segment();

	int dot = comp.getCaretPosition();
	Element root = doc.getDefaultRootElement();
	int index = root.getElementIndex(dot);
	Element elem = root.getElement(index);
	int start = elem.getStartOffset();
	int len = dot - start;
	try {
	    doc.getText(start, len, seg);
	} catch (BadLocationException ble) {
	    ble.printStackTrace();
	    return "";
	}

	int segEnd = seg.offset + len;
	start = segEnd - 1;
	while (start >= seg.offset && isValidChar(seg.array[start])) {
	    start--;
	}
	start++;

	len = segEnd - start;
	return len == 0 ? "" : new String(seg.array, start, len);
    }

    private boolean isValidChar(char ch) {
	return Character.isLetterOrDigit(ch) || ch == '_' || ch == '\"';
    }

}

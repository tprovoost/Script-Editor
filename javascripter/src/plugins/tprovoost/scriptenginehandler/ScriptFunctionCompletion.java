package plugins.tprovoost.scriptenginehandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;

import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.FunctionCompletion;

public class ScriptFunctionCompletion extends FunctionCompletion {

    private Method method;
    private boolean isStatic;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BindingFunction {
	String value();
    }

    public ScriptFunctionCompletion() {
	super(null, "", "");
    }

    public ScriptFunctionCompletion(CompletionProvider provider, String name, Method method) {
	super(provider, name, method.getReturnType().getName());
	this.method = method;
    }

    /**
     * Get the correct function call in java.
     * 
     * @return
     */
    public String getMethodCall() {
	String parametersAsString = "";
	Class<?>[] paramTypes = method.getParameterTypes();
	for (int i = 0; i < paramTypes.length; ++i) {
	    if (i != 0)
		parametersAsString += " ,arg" + i;
	    else
		parametersAsString += "arg" + i;
	}
	return "Packages." + method.getDeclaringClass().getName() + "." + method.getName() + "(" + parametersAsString + ");";
    }

    /**
     * Returns if the function should be accessed in a static way.
     * 
     * @return
     */
    public boolean isStatic() {
	return Modifier.isStatic(method.getModifiers());
    }

    /**
     * 
     * @return
     */
    public Class<?> getOriginatingClass() {
	if (isStatic)
	    return null;
	return method.getDeclaringClass();
    }

    public Method getMethod() {
	return method;
    }

    @Override
    public boolean equals(Object arg0) {
	if (!(arg0 instanceof ScriptFunctionCompletion))
	    return false;
	return ((ScriptFunctionCompletion) arg0).getName().contentEquals(getName());
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

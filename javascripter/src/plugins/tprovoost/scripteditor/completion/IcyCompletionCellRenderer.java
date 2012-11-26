package plugins.tprovoost.scripteditor.completion;

import icy.image.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.JList;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.autocomplete.FunctionCompletion;
import org.fife.ui.autocomplete.VariableCompletion;

import plugins.tprovoost.scripteditor.completion.types.BasicJavaClassCompletion;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion;

public class IcyCompletionCellRenderer extends CompletionCellRenderer {

    /** */
    private static final long serialVersionUID = 1L;

    // icons
    public ImageIcon iconStaticFunctions = new ImageIcon(ImageUtil.load(getClass().getClassLoader().getResourceAsStream(
	    "plugins/tprovoost/scripteditor/icons/static_co.gif")));
    public ImageIcon iconFunctions = new ImageIcon(ImageUtil.load(getClass().getClassLoader().getResourceAsStream(
	    "plugins/tprovoost/scripteditor/icons/methpub_obj.gif")));
    public ImageIcon iconVariables = new ImageIcon(ImageUtil.load(getClass().getClassLoader().getResourceAsStream(
	    "plugins/tprovoost/scripteditor/icons/field_default_obj.gif")));
    public ImageIcon iconOther = new ImageIcon(ImageUtil.load(getClass().getClassLoader().getResourceAsStream(
	    "plugins/tprovoost/scripteditor/icons/constant_co.gif")));
    public ImageIcon iconClass = new ImageIcon(ImageUtil.load(getClass().getClassLoader().getResourceAsStream(
	    "plugins/tprovoost/scripteditor/icons/class_obj.gif")));

    @Override
    protected void prepareForFunctionCompletion(JList list, FunctionCompletion fc, int index, boolean selected, boolean hasFocus) {
	if (fc instanceof ScriptFunctionCompletion) {
	    if (((ScriptFunctionCompletion) fc).isStatic())
		setIcon(iconStaticFunctions);
	    else
		setIcon(iconFunctions);
	} else
	    setIcon(iconFunctions);
	super.prepareForFunctionCompletion(list, fc, index, selected, hasFocus);
    }

    @Override
    protected void prepareForVariableCompletion(JList list, VariableCompletion vc, int index, boolean selected, boolean hasFocus) {
	if (vc instanceof BasicJavaClassCompletion)
	    setIcon(iconClass);
	else
	    setIcon(iconVariables);
	super.prepareForVariableCompletion(list, vc, index, selected, hasFocus);
    }

    @Override
    protected void prepareForOtherCompletion(JList list, Completion c, int index, boolean selected, boolean hasFocus) {
	setIcon(iconOther);
	super.prepareForOtherCompletion(list, c, index, selected, hasFocus);
    }
}

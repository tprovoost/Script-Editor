package plugins.tprovoost.scripteditor.completion;

import java.awt.image.BufferedImage;

import icy.image.ImageUtil;
import icy.plugin.PluginLoader;

import javax.swing.ImageIcon;
import javax.swing.JList;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.autocomplete.FunctionCompletion;
import org.fife.ui.autocomplete.VariableCompletion;

import plugins.tprovoost.scripteditor.completion.types.BasicJavaClassCompletion;
import plugins.tprovoost.scripteditor.completion.types.NewInstanceCompletion;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion;

public class IcyCompletionCellRenderer extends CompletionCellRenderer
{

    /** */
    private static final long serialVersionUID = 1L;

    // images
    public static final BufferedImage imgStatic = ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/static_co.gif"));
    public static final BufferedImage imgFunction = ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/methpub_obj.gif"));

    // icons
    public static final ImageIcon iconStaticFunctions = new ImageIcon(imgStatic);
    public static final ImageIcon iconFunctions = new ImageIcon(imgFunction);
    public static final ImageIcon iconVariables = new ImageIcon(ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/field_default_obj.gif")));
    public static final ImageIcon iconOther = new ImageIcon(ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/constant_co.gif")));
    public static final ImageIcon iconClass = new ImageIcon(ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/class_obj.gif")));

    @Override
    protected void prepareForFunctionCompletion(JList list, FunctionCompletion fc, int index, boolean selected,
            boolean hasFocus)
    {
        if (fc instanceof ScriptFunctionCompletion)
        {
            if (((ScriptFunctionCompletion) fc).isStatic())
            {
                BufferedImage func2 = ImageUtil.getCopy(imgFunction);
                func2.getGraphics().drawImage(imgStatic, 0, 0, null);
                setIcon(new ImageIcon(func2));
            }
            else
                setIcon(iconFunctions);
        }
        else if (fc instanceof NewInstanceCompletion)
        {
            setIcon(iconClass);
        }
        else
            setIcon(iconFunctions);
        super.prepareForFunctionCompletion(list, fc, index, selected, hasFocus);
    }

    @Override
    protected void prepareForVariableCompletion(JList list, VariableCompletion vc, int index, boolean selected,
            boolean hasFocus)
    {
        if (vc instanceof BasicJavaClassCompletion)
            setIcon(iconClass);
        else
            setIcon(iconVariables);
        super.prepareForVariableCompletion(list, vc, index, selected, hasFocus);
    }

    @Override
    protected void prepareForOtherCompletion(JList list, Completion c, int index, boolean selected, boolean hasFocus)
    {
        setIcon(iconOther);
        super.prepareForOtherCompletion(list, c, index, selected, hasFocus);
    }
}

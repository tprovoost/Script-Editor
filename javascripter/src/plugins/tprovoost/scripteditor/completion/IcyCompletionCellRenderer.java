package plugins.tprovoost.scripteditor.completion;

import java.awt.Image;
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
    public static final BufferedImage imgDeprecated = ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/deprecated.gif"));

    // icons
    public static final ImageIcon iconFunctions = new ImageIcon(imgFunction);
    public static final ImageIcon iconVariables = new ImageIcon(ImageUtil.load(PluginLoader
            .getResourceAsStream("plugins/tprovoost/scripteditor/resources/icons/field_public_obj.gif")));
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
            ImageIcon icon = iconFunctions;
            BufferedImage img = null;
            if (((ScriptFunctionCompletion) fc).isStatic())
            {
                BufferedImage func2 = ImageUtil.getCopy(imgFunction);
                func2.getGraphics().drawImage(imgStatic, 0, 0, null);
                img = func2;
            }
            if (((ScriptFunctionCompletion) fc).getMethod().getAnnotation(Deprecated.class) != null)
            {
                if (img == null)
                    img = ImageUtil.getCopy(imgFunction);
                img.getGraphics().drawImage(imgDeprecated, 0, 0, null);
            }
            if (img != null)
                setIcon(new ImageIcon(img));
            else
                setIcon(icon);
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
        ImageIcon icon;
        if (vc instanceof BasicJavaClassCompletion)
            icon = iconClass;
        else
            icon = iconVariables;

        if (vc.getSummary().startsWith("Deprecated"))
        {
            Image img = ImageUtil.getCopy(icon.getImage());
            img.getGraphics().drawImage(imgDeprecated, 0, 0, null);
            setIcon(new ImageIcon(img));
        }
        else
            setIcon(icon);
        super.prepareForVariableCompletion(list, vc, index, selected, hasFocus);
    }

    @Override
    protected void prepareForOtherCompletion(JList list, Completion c, int index, boolean selected, boolean hasFocus)
    {
        setIcon(iconOther);
        super.prepareForOtherCompletion(list, c, index, selected, hasFocus);
    }

}

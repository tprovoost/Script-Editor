package plugins.tprovoost.scripteditor.uitools.userdialogs;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JSlider;

public class ItemSlider extends Item
{
    private static final long serialVersionUID = 1L;
    private JSlider spinner;

    public ItemSlider(String name, int min, int max, int defaultValue)
    {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(new JLabel(name));

        spinner = new JSlider(min, max, defaultValue);

        add(spinner);
    }

    @Override
    public Integer getValue()
    {
        return spinner.getValue();
    }

}

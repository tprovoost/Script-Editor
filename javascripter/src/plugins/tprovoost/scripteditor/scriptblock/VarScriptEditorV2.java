package plugins.tprovoost.scripteditor.scriptblock;

import icy.gui.component.IcyTextField;
import icy.gui.component.IcyTextField.TextChangeListener;
import icy.gui.component.button.IcyButton;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.IcyFrameListener;
import icy.resource.icon.IcyIcon;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import plugins.adufour.vars.gui.swing.SwingVarEditor;
import plugins.tprovoost.scripteditor.gui.ScriptingPanel;

public class VarScriptEditorV2 extends SwingVarEditor<String>
{
    ScriptingPanel panel;
    private IcyFrame frame;
    private IcyFrameListener frameListener;
    private TextChangeListener textlistener;
    private IcyTextField tfTitle;

    public VarScriptEditorV2(VarScript varScript, String defaultValue)
    {
        super(varScript);
        panel = new ScriptingPanel(null, "Untitled", "JavaScript", true);
        panel.setText(defaultValue);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        frame = new IcyFrame("untitled", true, true, true, true);
        frame.setContentPane(panel);
        frame.setSize(500, 500);
        frame.setVisible(true);
        frame.addToMainDesktopPane();
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.center();

        textlistener = new TextChangeListener()
        {

            @Override
            public void textChanged(IcyTextField source, boolean validate)
            {
                frame.setTitle(tfTitle.getText());
            }
        };
    }

    @Override
    protected JComponent createEditorComponent()
    {
        tfTitle = new IcyTextField("untitled");

        // building east component
        IcyButton buttonEdit = new IcyButton(new IcyIcon("redo.png", 12));
        buttonEdit.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                frame.setVisible(true);
                frame.requestFocus();
            }
        });
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BoxLayout(eastPanel, BoxLayout.Y_AXIS));
        eastPanel.add(Box.createVerticalGlue());
        eastPanel.add(buttonEdit);
        eastPanel.add(Box.createVerticalGlue());
        eastPanel.setOpaque(false);

        // to Return panel
        JPanel toReturn = new JPanel(new BorderLayout());
        toReturn.add(tfTitle, BorderLayout.CENTER);
        toReturn.setOpaque(false);
        toReturn.add(eastPanel, BorderLayout.EAST);

        return toReturn;
    }

    @Override
    protected void activateListeners()
    {
        frame.addFrameListener(frameListener);
        tfTitle.addTextChangeListener(textlistener);
    }

    @Override
    protected void deactivateListeners()
    {
        // textArea.removeMouseListener(mouseListener);
        frame.removeFrameListener(frameListener);
        frame.close();
    }

    @Override
    protected void updateInterfaceValue()
    {

    }

    public String getText()
    {
        if (panel == null || panel.getTextArea() == null)
            return getVariable().getDefaultValue();
        return panel.getTextArea().getText();
    }

    public void setText(String newValue)
    {
        panel.getTextArea().setText(newValue);
    }

    public String getTitle()
    {
        return tfTitle.getText();
    }

    public void setTitle(String value)
    {
        tfTitle.setText(value);
    }

}

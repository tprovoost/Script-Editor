package plugins.tprovoost.scripteditor.completion;

import java.util.ArrayList;
import java.util.List;

import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;

public class IcyCompletionProviderPython extends IcyCompletionProvider
{

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp)
    {
        ArrayList<Completion> retVal = new ArrayList<Completion>();
        String text = comp.getText();
        doClassicCompletion(text, retVal);
        return retVal;
    }
}

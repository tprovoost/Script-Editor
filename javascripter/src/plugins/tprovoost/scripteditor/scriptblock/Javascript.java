package plugins.tprovoost.scripteditor.scriptblock;

import icy.plugin.abstract_.Plugin;
import icy.roi.ROI;
import icy.sequence.Sequence;

import java.io.File;
import java.util.ArrayList;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.gui.model.TypeSelectionModel;
import plugins.adufour.vars.gui.model.ValueSelectionModel;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarMutable;
import plugins.adufour.vars.lang.VarString;
import plugins.adufour.vars.lang.VarTrigger;
import plugins.adufour.vars.util.VarReferencingPolicy;
import plugins.tprovoost.scripteditor.scriptblock.vartransformer.JSScriptBlock;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngineHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptingHandler;

public class Javascript extends Plugin implements Block
{
    ArrayList<String> languagesInstalled = new ArrayList<String>();

    private VarString scriptType;
    private VarScript inputScript = new VarScript("Script",
            "// Click on the button\n// to edit in a frame.\n\noutput0 = input0 * 2");

    private VarList inputMap;
    private VarList outputMap;

    private int inputIdx = 0;
    private int outputIdx = 0;

    public Javascript()
    {
        ScriptEngineManager factory = new ScriptEngineManager();
        for (ScriptEngineFactory f : factory.getEngineFactories())
        {
            languagesInstalled.add(ScriptEngineHandler.getLanguageName(f));
        }
        scriptType = new VarString("Language:", languagesInstalled.get(0));
        scriptType.setDefaultEditorModel(new ValueSelectionModel<String>(languagesInstalled.toArray(new String[0])));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void run()
    {
        ScriptingHandler handler = inputScript.getEditor().getScriptHandler();
        ScriptEngine engine = handler.createNewEngine();
        // String language = inputScript.getEditor().panelIn.getLanguage();

        for (Var<?> var : inputMap)
        {
            Object value = var.getValue();
            String name = var.getName();
            if (name.contains("input"))
            {
                // For another language, remove this, or use the right one.
                value = JSScriptBlock.transformInputForScript(value);

                // put in the engine the value.
                engine.put(name, value);
            }
        }
        try
        {
            inputScript.evaluate();
        }
        catch (ScriptException e)
        {
            System.out.println(e.getLocalizedMessage());
        }

        for (Var output : outputMap)
        {
            Object resObject = engine.get(output.getName());
            output.setValue(JSScriptBlock.transformScriptOutput(resObject));
        }
    }

    @Override
    public void declareInput(VarList inputMap)
    {
        if (this.inputMap == null)
            this.inputMap = inputMap;
        final VarTrigger triggerInput = new VarTrigger("Add Input", new VarTrigger.TriggerListener()
        {

            @Override
            public void valueChanged(Var<Integer> source, Integer oldValue, Integer newValue)
            {
            }

            @Override
            public void referenceChanged(Var<Integer> source, Var<? extends Integer> oldReference,
                    Var<? extends Integer> newReference)
            {
            }

            @Override
            public void triggered(VarTrigger source)
            {
                String name = "input" + inputIdx++;
                VarMutable myVariable = new VarMutable(name, Object.class);
                myVariable.setDefaultEditorModel(new TypeSelectionModel(new Class<?>[] {Object.class, Object[].class,
                        Sequence.class, ROI[].class, Integer.class, Double.class, int[].class, double[].class,
                        String.class, File.class, File[].class}));
                Javascript.this.inputMap.addRuntimeVariable("" + myVariable.hashCode(), myVariable);
            }
        });
        triggerInput.setReferencingPolicy(VarReferencingPolicy.NONE);

        final VarTrigger triggerOutput = new VarTrigger("Add output", new VarTrigger.TriggerListener()
        {

            @Override
            public void valueChanged(Var<Integer> source, Integer oldValue, Integer newValue)
            {
            }

            @Override
            public void referenceChanged(Var<Integer> source, Var<? extends Integer> oldReference,
                    Var<? extends Integer> newReference)
            {
            }

            @Override
            public void triggered(VarTrigger source)
            {
                String name = "output" + outputIdx++;
                VarMutable myVariable = new VarMutable(name, Object.class);
                myVariable.setDefaultEditorModel(new TypeSelectionModel(new Class<?>[] {Object.class, Object[].class,
                        Sequence.class, ROI[].class, Integer.class, Double.class, int[].class, double[].class,
                        String.class, File.class, File[].class}));
                outputMap.addRuntimeVariable("" + myVariable.hashCode(), myVariable);
            }
        });
        triggerOutput.setReferencingPolicy(VarReferencingPolicy.NONE);

        inputScript.setReferencingPolicy(VarReferencingPolicy.NONE);

        inputMap.add(inputScript);
        inputMap.add(triggerInput);
        inputMap.add(triggerOutput);

        String name = "input" + inputIdx++;
        VarMutable myVariable = new VarMutable(name, Object.class);
        myVariable.setDefaultEditorModel(new TypeSelectionModel(new Class<?>[] {Object.class, Object[].class,
                Sequence.class, ROI[].class, Integer.class, Double.class, int[].class, double[].class, String.class,
                File.class, File[].class}));
        inputMap.add(name, myVariable);
    }

    @Override
    public void declareOutput(final VarList outputMap)
    {
        this.outputMap = outputMap;
        String name = "output" + outputIdx;
        VarMutable myVariable = new VarMutable(name, Object.class);
        myVariable.setDefaultEditorModel(new TypeSelectionModel(new Class<?>[] {Object.class, Object[].class,
                Sequence.class, ROI[].class, Integer.class, Double.class, int[].class, double[].class, String.class,
                File.class, File[].class}));
        outputMap.add(name, myVariable);
        outputIdx++;
    }

}

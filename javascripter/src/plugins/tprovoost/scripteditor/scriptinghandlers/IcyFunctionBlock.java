package plugins.tprovoost.scripteditor.scriptinghandlers;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

public class IcyFunctionBlock {

    private String functionName;
    private int startOffset;
    private Class<?> returnType;

    /**
     * @param functionName
     * @param startOffset
     * @param endOffset
     * @param returnType
     */
    public IcyFunctionBlock(String functionName, int startOffset, Class<?> returnType) {
	this.functionName = functionName;
	this.startOffset = startOffset;
	this.returnType = returnType;
	ScriptEngineManager manager = new ScriptEngineManager();
	for (ScriptEngineFactory factory : manager.getEngineFactories()) {
	    System.out.println(factory.getEngineName() + " / " + factory.getEngineVersion());
	}
    }

    public String getFunctionName() {
	return functionName;
    }

    public int getStartOffset() {
	return startOffset;
    }

    public Class<?> getReturnType() {
	return returnType;
    }
}

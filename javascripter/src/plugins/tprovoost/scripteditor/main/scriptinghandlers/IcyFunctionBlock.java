package plugins.tprovoost.scripteditor.main.scriptinghandlers;

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

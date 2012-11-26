package plugins.tprovoost.scripteditor.main.scriptinghandlers;

public class IcyFunctionBlock {

    private String functionName;
    private int startOffset;
    private int endOffset;
    private Class<?> returnType;

    /**
     * @param functionName
     * @param startOffset
     * @param endOffset
     * @param returnType
     */
    public IcyFunctionBlock(String functionName, int startOffset, int endOffset, Class<?> returnType) {
	this.functionName = functionName;
	this.startOffset = startOffset;
	this.endOffset = endOffset;
	this.returnType = returnType;
    }

    public String getFunctionName() {
	return functionName;
    }

    public int getStartOffset() {
	return startOffset;
    }

    public int getEndOffset() {
	return endOffset;
    }

    public Class<?> getReturnType() {
	return returnType;
    }

    @Override
    public boolean equals(Object arg0) {
	// test if reference is the same, don't go further it true
	if (super.equals(arg0))
	    return true;

	// test the start and end offset values
	if (arg0 instanceof IcyFunctionBlock) {
	    IcyFunctionBlock compare = (IcyFunctionBlock) arg0;
	    return compare.startOffset == startOffset && compare.endOffset == endOffset;
	}
	return false;
    }
}

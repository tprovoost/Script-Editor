package plugins.tprovoost.scripteditor.scriptinghandlers;

public class IcyFunctionBlock
{

    private String functionName;
    private int startOffset;
    private Class<?> returnType;

    /**
     * @param functionName
     * @param startOffset
     * @param endOffset
     * @param returnType
     */
    public IcyFunctionBlock(String functionName, int startOffset, Class<?> returnType)
    {
        this.functionName = functionName;
        this.startOffset = startOffset;
        this.returnType = returnType;
    }

    public String getFunctionName()
    {
        return functionName;
    }

    public int getStartOffset()
    {
        return startOffset;
    }

    public Class<?> getReturnType()
    {
        return returnType;
    }

    public void setStartOffset(int startOffset)
    {
        this.startOffset = startOffset;
    }

    // public void setReturnType(String returnType)
    // {
    // try
    // {
    // if (returnType.contains("<"))
    // {
    // Pattern p = Pattern.compile("(\\w|_)*\\s*<(.*)>");
    // Matcher m = p.matcher(returnType);
    // if (m.matches())
    // {
    // System.out.println("classname: " + m.group(0));
    // System.out.println("Anonymous: " + m.group(2));
    // }
    // }
    // setReturnType(ClassUtil.findClass(returnType));
    // }
    // catch (ClassNotFoundException e)
    // {
    // returnType = null;
    // }
    // }

    public void setReturnType(Class<?> returnType)
    {
        this.returnType = returnType;
    }
}

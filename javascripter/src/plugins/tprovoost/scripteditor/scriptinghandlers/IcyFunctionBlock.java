package plugins.tprovoost.scripteditor.scriptinghandlers;

import java.lang.reflect.Type;

public class IcyFunctionBlock
{

    private String functionName;
    private int startOffset;
    private Class<?> returnTypeClass;
    private Type returnType;

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
        // ScriptEngineManager manager = new ScriptEngineManager();
        // for (ScriptEngineFactory factory : manager.getEngineFactories())
        // {
        // System.out.println(factory.getEngineName() + " / " + factory.getEngineVersion());
        // }
    }

    public String getFunctionName()
    {
        return functionName;
    }

    public int getStartOffset()
    {
        return startOffset;
    }

    public Type getReturnType()
    {
        return returnType;
    }

    public Class<?> getReturnTypeClass()
    {
        return returnTypeClass;
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

    public void setReturnType(Class<?> returnTypeClass)
    {
        this.returnTypeClass = returnTypeClass;
    }

    public void setReturnType(Type returnType)
    {
        this.returnType = returnType;
    }
}

package plugins.tprovoost.scripteditor.scriptinghandlers;

import java.lang.reflect.Type;

/**
 * Object containing the Class needed and if necessary the Type associated. Can be extremely useful
 * when the class is an Array.
 * 
 * @author Thomas Provoost
 */
public class VariableType
{
    private Class<?> cType;
    private Type type;

    public VariableType(Class<?> cType)
    {
        this.cType = cType;
    }

    public VariableType(Class<?> cType, Type type)
    {
        this.cType = cType;
        this.type = type;
    }

    public Class<?> getClassType()
    {
        return cType;
    }

    public void setClassType(Class<?> cType)
    {
        this.cType = cType;
    }

    public Type getType()
    {
        return type;
    }

    public void setType(Type type)
    {
        this.type = type;
    }

    public String[] getTypeParameters()
    {
        String typeParams = type.toString();
        int idxB = typeParams.indexOf('<');
        int idxE = typeParams.indexOf('>');

        typeParams.substring(idxB + 1, idxE);
        return typeParams.split(",");
    }

}

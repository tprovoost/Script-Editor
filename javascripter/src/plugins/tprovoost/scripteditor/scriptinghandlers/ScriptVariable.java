package plugins.tprovoost.scripteditor.scriptinghandlers;

import java.util.ArrayList;

public class ScriptVariable
{
    private ArrayList<ScriptVariableScope> variableScopes = new ArrayList<ScriptVariableScope>();

    public void addType(int offsetBegin, Class<?> returnType)
    {
        addType(offsetBegin, -1, returnType);
    }

    public void addType(int offsetBegin, int offsetEnd, Class<?> returnType)
    {
        if (!variableScopes.isEmpty())
        {
            ScriptVariableScope lastScope = variableScopes.get(variableScopes.size() - 1);
            if (lastScope.endScopeOffset == -1)
                lastScope.endScopeOffset = offsetBegin - 1;
        }
        variableScopes.add(new ScriptVariableScope(offsetBegin, offsetEnd, returnType));
    }

    public Class<?> getVariableClassType(int offset)
    {
        for (ScriptVariableScope svc : variableScopes)
        {
            Class<?> clazz = svc.getType(offset);
            if (clazz != null)
                return clazz;
        }
        return null;
    }

    public Class<?> getVariableLastClassType()
    {
        if (!variableScopes.isEmpty())
        {
            return variableScopes.get(variableScopes.size() - 1).cType;
        }
        return null;
    }

    private class ScriptVariableScope
    {
        private int declarationOffset;
        private int endScopeOffset;
        private Class<?> cType;

        // private Type type;

        public ScriptVariableScope(int declarationOffset, int endScopeOffset, Class<?> type)
        {
            this.declarationOffset = declarationOffset;
            this.endScopeOffset = endScopeOffset;
            this.cType = type;
            variableScopes.add(this);
        }

        public Class<?> getType(int offset)
        {
            if (offset >= declarationOffset && (endScopeOffset == -1 || offset < endScopeOffset))
                return cType;
            return null;
        }
    }

    public boolean isInScope(int offset)
    {
        for (ScriptVariableScope svc : variableScopes)
        {
            boolean inScope = offset >= svc.declarationOffset
                    && (svc.endScopeOffset == -1 || offset < svc.endScopeOffset);
            if (inScope)
                return true;
        }
        return false;
    }
}

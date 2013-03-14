package plugins.tprovoost.scripteditor.scriptblock.vartransformer;

import org.python.core.PyObject;

public class PythonScriptBlock
{

    public static Object transformScriptOutput(Object o)
    {
        if (o instanceof PyObject)
            return ((PyObject) o).__tojava__(Object.class);
        return o;
    }

}

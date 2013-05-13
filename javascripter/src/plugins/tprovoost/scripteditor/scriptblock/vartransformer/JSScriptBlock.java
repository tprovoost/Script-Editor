package plugins.tprovoost.scripteditor.scriptblock.vartransformer;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;

public class JSScriptBlock
{

    public static Object transformScriptOutput(Object o)
    {
        Object toReturn = o;
        if (o instanceof NativeArray)
        {
            toReturn = Context.jsToJava(o, Object[].class);
        }
        else
        {
            toReturn = Context.jsToJava(o, Object.class);
        }
        return toReturn;
    }

    public static Object transformInputForScript(Object o)
    {
        Object toReturn = o;
        // Context cx = Context.enter();
        // try
        // {
        // cx.setApplicationClassLoader(PluginLoader.getLoader());
        // toReturn = Context.javaToJS(toReturn, new ImporterTopLevel(cx));
        // }
        // finally
        // {
        // Context.exit();
        // }
        // if (toReturn instanceof NativeJavaArray)
        // {
        // toReturn = ((NativeJavaArray) toReturn).unwrap();
        // }
        return toReturn;
    }
}

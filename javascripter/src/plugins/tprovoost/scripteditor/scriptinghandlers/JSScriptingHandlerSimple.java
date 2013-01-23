package plugins.tprovoost.scripteditor.scriptinghandlers;

import icy.image.IcyBufferedImage;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginInstaller;
import icy.plugin.PluginLoader;
import icy.plugin.PluginRepositoryLoader;
import icy.sequence.Sequence;
import icy.util.ClassUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.FunctionCompletion;
import org.fife.ui.autocomplete.ParameterizedCompletion.Parameter;
import org.fife.ui.autocomplete.VariableCompletion;
import org.fife.ui.rtextarea.Gutter;

import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scriptenginehandler.ScriptEngineHandler;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;
import sun.org.mozilla.javascript.internal.CompilerEnvirons;
import sun.org.mozilla.javascript.internal.Context;
import sun.org.mozilla.javascript.internal.Function;
import sun.org.mozilla.javascript.internal.FunctionNode;
import sun.org.mozilla.javascript.internal.ImporterTopLevel;
import sun.org.mozilla.javascript.internal.NativeArray;
import sun.org.mozilla.javascript.internal.NativeObject;
import sun.org.mozilla.javascript.internal.Node;
import sun.org.mozilla.javascript.internal.Parser;
import sun.org.mozilla.javascript.internal.RhinoException;
import sun.org.mozilla.javascript.internal.Script;
import sun.org.mozilla.javascript.internal.ScriptOrFnNode;
import sun.org.mozilla.javascript.internal.ScriptableObject;
import sun.org.mozilla.javascript.internal.Token;

import com.sun.script.javascript.RhinoScriptEngine;

public class JSScriptingHandlerSimple extends ScriptingHandler
{

    private int commandStartOffset;
    private int commandEndOffset;

    public JSScriptingHandlerSimple(DefaultCompletionProvider provider, JTextComponent textArea, Gutter gutter,
            boolean autocompilation)
    {
        super(provider, "ECMAScript", textArea, gutter, autocompilation);
    }

    @Override
    public void eval(ScriptEngine engine, String s) throws ScriptException
    {
        Context context = Context.enter();
        context.setApplicationClassLoader(PluginLoader.getLoader());
        try
        {
            ScriptableObject scriptable = new ImporterTopLevel(context);
            Bindings bs = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            for (String key : bs.keySet())
            {
                Object o = bs.get(key);
                scriptable.put(key, scriptable, o);
            }
            Script script = context.compileString(s, "script", 0, null);
            script.exec(context, scriptable);
            for (Object o : scriptable.getIds())
            {
                String key = (String) o;
                bs.put(key, scriptable.get(key, scriptable));
            }
        }
        catch (RhinoException e)
        {
            throw new ScriptException(e.details(), e.sourceName(), e.lineNumber());
        }
        finally
        {
            Context.exit();
        }
    }

    @Override
    public void installDefaultLanguageCompletions(String language)
    {
        ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());
        HashMap<String, Class<?>> engineFunctions = engineHandler.getEngineFunctions();

        // IMPORT PACKAGES
        try
        {
            importJavaScriptPackages(getEngine());
        }
        catch (ScriptException e1)
        {
        }

        // IMPORT A FEW IMPORTANT SEQUENCES, TO BE REMOVED
        FunctionCompletion c;
        // ArrayList<Parameter> params = new ArrayList<Parameter>();
        try
        {
            getEngine().eval(
                    "function getSequence() { return Packages.icy.main.Icy.getMainInterface().getFocusedSequence() }");
            c = new FunctionCompletion(provider, "getSequence", "Sequence");
            c.setDefinedIn("MainInterface");
            c.setReturnValueDescription("The focused sequence is returned.");
            c.setShortDescription("Returns the sequence under focus. Returns null if no sequence opened.");
            provider.addCompletion(c);
            engineFunctions.put("getSequence", Sequence.class);
        }
        catch (ScriptException e)
        {
            System.out.println(e.getMessage());
        }

        try
        {
            getEngine().eval(
                    "function getImage() { return Packages.icy.main.Icy.getMainInterface().getFocusedImage(); }");
            c = new FunctionCompletion(provider, "getImage", "IcyBufferedImage");
            c.setDefinedIn("MainInterface");
            c.setShortDescription("Returns the current image viewed in the focused sequence.");
            c.setReturnValueDescription("Returns the focused Image, returns null if no sequence opened");
            provider.addCompletion(c);
            engineFunctions.put("getImage", IcyBufferedImage.class);
        }
        catch (ScriptException e)
        {
            System.out.println(e.getMessage());
        }

        // ADD JS FUNCTIONS
        engineFunctions.put("importClass", void.class);
        engineFunctions.put("importPackage", void.class);

        // IMPORT PLUGINS FUNCTIONS
        try
        {
            importFunctions();
        }
        catch (ScriptException e)
        {
            e.printStackTrace();
        }
    }

    public void importJavaScriptPackages(ScriptEngine engine) throws ScriptException
    {
        // icy important packages
        engine.eval("importPackage(Packages.icy.main);");
        engine.eval("importPackage(Packages.icy.plugin);");
        engine.eval("importPackage(Packages.icy.sequence)\n");
        engine.eval("importPackage(Packages.icy.image)");
        engine.eval("importPackage(Packages.icy.file);");
        engine.eval("importPackage(Packages.icy.file.xls)");

        ArrayList<String> engineDeclaredImports = ScriptEngineHandler.getEngineHandler(engine)
                .getEngineDeclaredImports();
        if (!engineDeclaredImports.contains("icy.main"))
            engineDeclaredImports.add("icy.main");
        if (!engineDeclaredImports.contains("icy.plugin"))
            engineDeclaredImports.add("icy.plugin");
        if (!engineDeclaredImports.contains("icy.sequence"))
            engineDeclaredImports.add("icy.sequence");
        if (!engineDeclaredImports.contains("icy.image"))
            engineDeclaredImports.add("icy.image");
        if (!engineDeclaredImports.contains("icy.file"))
            engineDeclaredImports.add("icy.file");
        if (!engineDeclaredImports.contains("icy.file.xls"))
            engineDeclaredImports.add("icy.file.xls");
    }

    @Override
    public void installMethods(ScriptEngine engine, ArrayList<Method> methods)
    {
        // hardcoded functions, to remove in the future
        try
        {
            engine.eval("function getSequence() { return Icy.getMainInterface().getFocusedSequence() }");
        }
        catch (ScriptException e1)
        {
        }
        try
        {
            engine.eval("function getImage() { return Icy.getMainInterface().getFocusedImage(); }");
        }
        catch (ScriptException e1)
        {
        }

        for (Method method : methods)
        {
            // is it an annotated with BindingFunction?
            BindingFunction blockFunction = method.getAnnotation(BindingFunction.class);
            if (blockFunction == null)
                continue;
            // Generate the function for the provider
            ArrayList<Parameter> fParams = new ArrayList<Parameter>();
            Class<?>[] paramTypes = method.getParameterTypes();

            // get the parameters
            String params = "";
            String functionName = blockFunction.value();
            // get the parameters
            for (int i = 0; i < paramTypes.length; ++i)
            {
                fParams.add(new Parameter(IcyCompletionProvider.getType(paramTypes[i], true), "arg" + i));
                params += ",arg" + i;
            }
            if (params.length() > 0)
                params = params.substring(1);

            // the object for the provider
            ScriptFunctionCompletion sfc;
            if (Modifier.isStatic(method.getModifiers()))
                sfc = new ScriptFunctionCompletion(null, functionName, method);
            else
                sfc = new ScriptFunctionCompletion(null, method.getName(), method);

            try
            {
                if (engine instanceof RhinoScriptEngine)
                {
                    if (method.getReturnType() == void.class)
                    {
                        engine.eval("function " + functionName + " (" + params + ") {\n\t" + sfc.getMethodCall()
                                + "\n}");
                    }
                    else
                    {
                        engine.eval("function " + functionName + " (" + params + ") {\n\treturn " + sfc.getMethodCall()
                                + "\n}");
                    }
                }
            }
            catch (ScriptException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void registerImports()
    {
        String s = textArea.getText();
        Pattern patternClasses = Pattern.compile("importClass\\((Packages\\.|)((\\w|\\.)+)\\)");
        Matcher m = patternClasses.matcher(s);
        int offset = 0;
        while (m.find(offset))
        {
            String foundString = m.group(0);
            String imported = m.group(2);
            scriptDeclaredImportClasses.add(imported);
            int idxString = s.indexOf(foundString, offset);
            if (idxString == -1)
                break;
            offset = idxString + foundString.length();
        }

        Pattern patternPackages = Pattern.compile("importPackage\\((Packages\\.|)((\\w|\\.)+)\\)");
        m = patternPackages.matcher(s);
        offset = 0;
        while (m.find(offset))
        {
            String foundString = m.group(0);
            String imported = m.group(2);
            scriptDeclaredImports.add(imported);
            int idxString = s.indexOf(foundString, offset);
            if (idxString == -1)
                break;
            offset = idxString + foundString.length();
        }
    }

    @Override
    public void organizeImports(JTextComponent tc)
    {
        organizeImportsStatic(tc);
    }

    @Override
    public void format()
    {
        /*
         * This formatter uses beautify.js by Einar Lielmanis, <einar@jsbeautifier.org>
         * http://jsbeautifier.org/
         */
        InputStream is = PluginLoader.getResourceAsStream("plugins/tprovoost/scripteditor/resources/beautify.js");
        Reader reader = new BufferedReader(new InputStreamReader(is));
        Context context = Context.enter();
        context.setLanguageVersion(Context.VERSION_1_6);
        ScriptableObject scope = context.initStandardObjects();

        try
        {
            context.evaluateReader(scope, reader, "Beautify", 1, null);
        }
        catch (IOException e)
        {
            return;
        }
        Function fct = (Function) scope.get("js_beautify", scope);

        // boolean preserveNewLines = JSBeautifyOptions.getInstance().getOption("preserveNewLines",
        // true);
        // boolean useTabs = JSBeautifyOptions.getInstance().getOption("useTabs", false);
        // boolean spaceBeforeConditional =
        // JSBeautifyOptions.getInstance().getOption("spaceBeforeConditional", true);
        // boolean jslintHappy = JSBeautifyOptions.getInstance().getOption("jslintHappy", false);
        // boolean indentCase = JSBeautifyOptions.getInstance().getOption("indentCase", false);
        // int indentSize = JSBeautifyOptions.getInstance().getOption("indentSize", 1);
        // String braceStyle = JSBeautifyOptions.getInstance().getOption("braceStyle", "collapse");

        boolean preserveNewLines = true;
        boolean useTabs = true;
        boolean spaceBeforeConditional = true;
        boolean jslintHappy = false;
        boolean indentCase = false;
        int indentSize = 1;
        String braceStyle = "collapse";

        NativeObject properties = new NativeObject();

        if (useTabs)
        {
            properties.defineProperty("indent_char", "\t", NativeObject.READONLY);
            properties.defineProperty("indent_size", 1, NativeObject.READONLY);
        }
        else
        {
            int size = 4;
            if (indentSize == 0)
            {
                size = 2;
            }
            else if (indentSize == 1)
            {
                size = 4;
            }
            else
            {
                size = 8;
            }
            properties.defineProperty("indent_size", size, NativeObject.READONLY);
        }

        properties.defineProperty("preserve_newlines", preserveNewLines, NativeObject.READONLY);
        properties.defineProperty("max_preserve_newlines", false, NativeObject.READONLY);
        properties.defineProperty("jslint_happy", jslintHappy, NativeObject.READONLY);
        properties.defineProperty("space_before_conditional", spaceBeforeConditional, NativeObject.READONLY);
        properties.defineProperty("indent_case", indentCase, NativeObject.READONLY);

        properties.defineProperty("brace_style", braceStyle, NativeObject.READONLY);

        Object result = fct.call(context, scope, scope, new Object[] {textArea.getText(), properties});

        String finalText = result.toString();
        textArea.setText(finalText);
    }

    /**
     * Static because of the call in the autocomplete.
     * 
     * @param tc
     */
    public static void organizeImportsStatic(JTextComponent tc)
    {
        ArrayList<String> listImportsClass = new ArrayList<String>();
        ArrayList<String> listImportsPackages = new ArrayList<String>();
        boolean errorHappened = false;

        String originalText = tc.getText();
        String text = "";
        while ((text = tc.getText()).contains("importClass(") && !errorHappened)
        {
            int idxStart = text.indexOf("importClass(");
            if (idxStart == -1) // should never happen because of the contains
                continue;
            int idxStop = text.indexOf(')', idxStart);
            if (idxStop == -1)
            { // something weird happened in the code, stop.
                errorHappened = true;
                break;
            }
            Caret c = tc.getCaret();
            c.setDot(idxStart);
            c.moveDot(idxStop + 1);
            listImportsClass.add(tc.getSelectedText());
            tc.replaceSelection("");
        }
        while ((text = tc.getText()).contains("importPackage(") && !errorHappened)
        {
            int idxStart = text.indexOf("importPackage(");
            if (idxStart == -1) // should never happen because of the contains
                continue;
            int idxStop = text.indexOf(')', idxStart);
            if (idxStop == -1)
            { // something weird happened in the code, stop.
                errorHappened = true;
                break;
            }
            Caret c = tc.getCaret();
            c.setDot(idxStart);
            c.moveDot(idxStop + 1);
            listImportsPackages.add(tc.getSelectedText());
            tc.replaceSelection("");
        }
        if (errorHappened)
        {
            tc.setText(originalText);
        }
        else
        {
            String result = "";
            Collections.sort(listImportsClass);
            Collections.sort(listImportsPackages);

            for (int i = 0; i < listImportsClass.size(); ++i)
            {
                if (i == 0)
                    result += listImportsClass.get(i);
                else
                    result += "\n" + listImportsClass.get(i);
            }
            for (int i = 0; i < listImportsPackages.size(); ++i)
            {
                if (i == 0)
                    result += "\n\n" + listImportsPackages.get(i);
                else
                    result += "\n" + listImportsPackages.get(i);
            }
            String leftText = tc.getText();
            char c;
            while (leftText.length() > 0 && ((c = leftText.charAt(0)) == ' ' || c == '\n' || c == '\t'))
                leftText = leftText.substring(1);
            tc.setText(result + "\n\n" + leftText);
        }
    }

    @Override
    public void autoDownloadPlugins()
    {
        String s = textArea.getText();
        Pattern patternClasses = Pattern.compile("importClass\\((Packages\\.|)((\\w|\\.)+)\\)");
        Matcher m = patternClasses.matcher(s);
        int offset = 0;
        while (m.find(offset))
        {
            String foundString = m.group(0);
            String imported = m.group(2);

            PluginDescriptor plugindesc = PluginRepositoryLoader.getPlugin(imported);
            if (plugindesc != null)
            {
                // method is in the exact plugin
                if (!plugindesc.isInstalled())
                    PluginInstaller.install(plugindesc, false);
            }
            else
            {
                // class around plugin
                for (PluginDescriptor pd : PluginRepositoryLoader.getPlugins())
                {
                    if (pd.getClassName().startsWith(imported) && !pd.isInstalled())
                        PluginInstaller.install(pd, false);
                }
            }

            int idxString = s.indexOf(foundString, offset);
            if (idxString == -1)
                break;
            offset = idxString + foundString.length();
        }

        Pattern patternPackages = Pattern.compile("importPackage\\((Packages\\.|)((\\w|\\.)+)\\)");
        m = patternPackages.matcher(s);
        offset = 0;
        while (m.find(offset))
        {
            String foundString = m.group(0);
            String imported = m.group(2);

            for (PluginDescriptor pd : PluginRepositoryLoader.getPlugins())
            {
                if (pd.getClassName().startsWith(imported) && !pd.isInstalled())
                    PluginInstaller.install(pd, false);
            }

            int idxString = s.indexOf(foundString, offset);
            if (idxString == -1)
                break;
            offset = idxString + foundString.length();
        }
    }

    // --------------------------------------------------------------------
    // --------------------------------------------------------------------
    // JAVASCRIPT DETECTION FEATURES AND ASSISTANCE
    // --------------------------------------------------------------------
    // --------------------------------------------------------------------
    @Override
    protected void detectVariables(String s, Context context) throws ScriptException
    {
        final CompilerEnvirons comp = new CompilerEnvirons();
        comp.initFromContext(context);
        final Parser parser = new Parser(comp, comp.getErrorReporter());
        ScriptOrFnNode root;
        root = parser.parse(s, "", 1);

        if (root == null || !root.hasChildren())
            return;
        // no issue, removes variables
        for (Completion c : variableCompletions)
            provider.removeCompletion(c);
        variableCompletions.clear();
        if (DEBUG)
            dumpTree(root, root, 1, "");

        //
        // initialize offset values
        //
        commandStartOffset = 0;
        char charvalue;
        while (commandStartOffset < s.length()
                && ((charvalue = s.charAt(commandStartOffset)) == '\n' || charvalue == ' ' || charvalue == '\t'))
            commandStartOffset++;

        int idxln = s.indexOf('\n', commandStartOffset);
        int idxSemiColon = s.indexOf(';', commandStartOffset);
        commandEndOffset = -1;

        // choose the best ending character
        if (idxln == -1)
            commandEndOffset = idxSemiColon;
        else if (idxSemiColon == -1)
            commandEndOffset = idxln;
        else
            commandEndOffset = idxln < idxSemiColon ? idxln : idxSemiColon;

        // in case issue
        if (commandEndOffset == -1)
            commandEndOffset = s.length();
        else
            commandEndOffset++;

        // start variable registration
        registerVariables(s, root, root);

        // add the completions
        provider.addCompletions(variableCompletions);
    }

    /**
     * Register all variables in the successfully compiled script.
     * 
     * @param n
     * @param root
     * @throws ScriptException
     */
    private void registerVariables(String text, Node n, ScriptOrFnNode root) throws ScriptException
    {
        if (DEBUG)
            System.out.println(typeToName(n.getType()) + " " + commandStartOffset + "/" + commandEndOffset);
        // register current
        Completion c = generateCompletion(n, root, text);
        if (c != null)
        {
            boolean alreadyExists = false;
            for (int i = 0; i < variableCompletions.size() && !alreadyExists; ++i)
            {
                if (variableCompletions.get(i).compareTo(c) == 0)
                {
                    if (textArea.getCaret().getDot() > commandStartOffset)
                        variableCompletions.remove(i);
                    else
                        alreadyExists = true;
                }
            }
            if (!alreadyExists)
                variableCompletions.add(c);
        }
        // recursive call on children (if any)
        if (n.hasChildren())
        {
            Node child = n.getFirstChild();
            while (child != null)
            {
                registerVariables(text, child, root);
                child = child.getNext();
                if (n == root)
                {
                    commandStartOffset = commandEndOffset;
                    char ch;
                    // while(Pattern.matches("\\s+.*",
                    // text.substring(commandStartOffset))) {
                    // commandStartOffset++;
                    // }
                    while (commandStartOffset < text.length()
                            && ((ch = text.charAt(commandStartOffset)) == '_' || !Character.isLetterOrDigit(ch)))
                        commandStartOffset++;
                    int idxln = text.indexOf('\n', commandStartOffset);
                    int idxSemiColon = text.indexOf(';', commandStartOffset);
                    if (idxln == -1)
                        commandEndOffset = idxSemiColon;
                    else if (idxSemiColon == -1)
                        commandEndOffset = idxln;
                    else
                        commandEndOffset = idxln < idxSemiColon ? idxln : idxSemiColon;
                    if (commandEndOffset == -1)
                        commandEndOffset = commandStartOffset + text.substring(commandStartOffset).length();
                    else
                        commandEndOffset += 1;
                }
            }
        }
    }

    /**
     * Automatically generates the completion for javascript: variable or
     * function.
     * 
     * @param n
     * @param root
     * @param commandStartOffset
     * @param commandEndOffset
     * @return
     * @throws ScriptException
     */
    private Completion generateCompletion(Node n, ScriptOrFnNode root, String text) throws ScriptException
    {
        switch (n.getType())
        {
            case Token.VAR:
                if (n.getFirstChild() != null && n.getFirstChild().getType() == Token.NAME)
                {
                    commandStartOffset += "var".length();
                    // Node resNode = n.getFirstChild().getFirstChild();
                    String typeStr = "";
                    VariableCompletion c = new VariableCompletion(provider, n.getFirstChild().getString(), typeStr);
                    c.setSummary("variable");
                    c.setDefinedIn(fileName);
                    c.setRelevance(RELEVANCE_HIGH);
                    return c;
                }
                break;
            case Token.SETNAME:
            {
                // Node resNode = n.getFirstChild().getNext();
                String type = "";
                VariableCompletion c = new VariableCompletion(provider, n.getFirstChild().getString(), type);
                c.setSummary("Variable");
                c.setDefinedIn(fileName);
                c.setRelevance(RELEVANCE_HIGH);
                return c;
            }
            case Token.FUNCTION:
            {
                // String type = getVariableTypeAsString(n, text,
                // commandStartOffset, commandEndOffset);
                int fnIndex = n.getExistingIntProp(Node.FUNCTION_PROP);
                FunctionNode fn = root.getFunctionNode(fnIndex);
                String funcName = fn.getFunctionName();
                ArrayList<Parameter> params = new ArrayList<Parameter>();
                for (int i = 0; i < fn.getParamAndVarCount(); ++i)
                {
                    String s = fn.getParamOrVarName(i);
                    if (s == null || s == "" || s.contentEquals(funcName))
                        continue;
                    params.add(new Parameter("", s));
                }
                FunctionCompletion fc = new FunctionCompletion(provider, funcName, "");
                fc.setParams(params);
                fc.setRelevance(RELEVANCE_HIGH);
                return fc;
            }
            case Token.IF:
            case Token.IFEQ:
            case Token.IFNE:
            case Token.SWITCH:
            case Token.TRY:
            case Token.CATCH:
            case Token.WITH:
            case Token.FOR:
            case Token.ELSE:
            case Token.DO:
            case Token.FINALLY:
            case Token.WHILE:
            {
                commandStartOffset = commandEndOffset;
                char ch;
                // while(Pattern.matches("\\s+.*",
                // text.substring(commandStartOffset))) {
                // commandStartOffset++;
                // }
                while (commandStartOffset < text.length()
                        && ((ch = text.charAt(commandStartOffset)) == '_' || !Character.isLetterOrDigit(ch)))
                    commandStartOffset++;
                int idxln = text.indexOf('\n', commandStartOffset);
                int idxSemiColon = text.indexOf(';', commandStartOffset);
                if (idxln == -1)
                    commandEndOffset = idxSemiColon;
                else if (idxSemiColon == -1)
                    commandEndOffset = idxln;
                else
                    commandEndOffset = idxln < idxSemiColon ? idxln : idxSemiColon;
                if (commandEndOffset == -1)
                    commandEndOffset = commandStartOffset + text.substring(commandStartOffset).length();
                else
                    commandEndOffset += 1;
            }
                break;

            // case Token.WITH:
            // break;
            case Token.CALL:
                break;
            default:
                break;
        }
        return null;
    }

    protected void addVariableDeclaration(String name, Class<?> type, int offset)
    {
        TreeMap<Integer, Class<?>> list = localVariables.get(name);
        if (list == null)
            list = new TreeMap<Integer, Class<?>>();
        list.put(offset, type);
        localVariables.put(name, list);
    }

    @Override
    public Class<?> resolveClassDeclaration(String type)
    {
        Class<?> toReturn = null;
        int arraySize = 0;
        while (type.endsWith("[]"))
        {
            type = type.substring(0, type.length() - 2);
            arraySize++;
        }

        // try absolute
        if (type.contentEquals("Array"))
        {
            return NativeArray.class;
        }
        else if (type.contentEquals("String"))
        {
            return String.class;
        }
        try
        {
            if (type.startsWith("Packages."))
                type = type.substring("Packages.".length());
            toReturn = ClassUtil.findClass(type);
        }
        catch (ClassNotFoundException e)
        {
        }
        if (toReturn == null)
            toReturn = super.resolveClassDeclaration(type);
        while (toReturn != null && arraySize > 0)
        {
            toReturn = Array.newInstance(toReturn, 1).getClass();
            arraySize--;
        }
        return toReturn;
    }

    /**
     * Returns the line containg the string. Be careful, the first line has
     * index 1.
     * 
     * @param string
     * @return
     */
    @SuppressWarnings("unused")
    private int findLineContaining(String text)
    {
        if (textArea instanceof JTextArea)
        {
            if (text != null)
            {
                JTextArea tx = new JTextArea(text);
                try
                {
                    return tx.getLineOfOffset(commandStartOffset) + 1;
                }
                catch (BadLocationException e)
                {
                }
            }
            return -1;
        }
        else
            return 1;
    }

    @SuppressWarnings("unused")
    private String generateClassName(Node n, String toReturn)
    {
        if (n.getType() == Token.GETPROP)
        {
            toReturn += generateClassName(n.getFirstChild(), toReturn) + "."
                    + generateClassName(n.getLastChild(), toReturn);
        }
        else if (n.getType() == Token.STRING || n.getType() == Token.NAME)
        {
            return n.getString();
        }
        return toReturn;
    }

    /**
     * Given the fact that the text can be different from {@link #textArea} .getText(), this method
     * returns the offset in the original text,
     * corresponding to the commandStartOffset in the modified text.
     * 
     * @param text
     *        : text with or without line modifications
     * @param offset
     *        : the offset wanted
     * @return : Returns the same offset if textArea is not a JTextArea, returns
     *         the correct one if it is.
     * @throws BadLocationException
     *         : An exception is raised of the offset does not exist in the
     *         original text, which should never happen.
     */
    public int getTextAreaOffset(String text, int offset) throws BadLocationException
    {
        if (textArea instanceof JTextArea)
        {
            JTextArea txtTmp = new JTextArea(text);
            int line = txtTmp.getLineOfOffset(offset);
            int offsetFromLine = offset - txtTmp.getLineStartOffset(line);
            return ((JTextArea) textArea).getLineStartOffset(line) + offsetFromLine;
        }
        else
            return offset;
    }

    /**
     * Dump the whole tree in the cconsole.
     * 
     * @param n
     * @param root
     * @param commandIdx
     * @param decal
     */
    private void dumpTree(Node n, ScriptOrFnNode root, int commandIdx, String decal)
    {
        System.out.print(commandIdx + ": " + decal + typeToName(n.getType()));
        switch (n.getType())
        {
            case Token.BINDNAME:
                // System.out.println("bindname");
            case Token.STRING:
            case Token.NAME:
                System.out.println(": " + n.getString());
                break;
            case Token.NUMBER:
                System.out.println(": " + n.getDouble());
                break;
            // case Token.CALL:
            // System.out.println("call");
            // break;
            case Token.FUNCTION:
                int fnIndex = n.getExistingIntProp(Node.FUNCTION_PROP);
                FunctionNode fn = root.getFunctionNode(fnIndex);
                if (fn.getFunctionType() != FunctionNode.FUNCTION_EXPRESSION)
                {
                    System.out.println("not an expression.");
                }
                String funcName = fn.getFunctionName();
                System.out.print(": " + funcName + " ");
                for (int i = 0; i < fn.getParamCount(); ++i)
                {
                    String s = fn.getParamOrVarName(i);
                    if (s == null || s == "" || s.contentEquals(funcName))
                        continue;
                    System.out.print(s + " ");
                }
                n = fn;
                System.out.println();
                break;
            default:
                System.out.println();
        }
        if (n.hasChildren())
        {
            Node child = n.getFirstChild();
            while (child != null)
            {
                dumpTree(child, root, commandIdx, "-" + decal);
                child = child.getNext();
                if (n == root)
                {
                    ++commandIdx;
                    System.out.println();
                }
            }
        }
    }

    /**
     * <i>Extracted from rhino for debugging puproses.</i><br/>
     * Always returns a human-readable string for the token name. For instance, {@link #FINALLY} has
     * the name "FINALLY".
     * 
     * @param token
     *        the token code
     * @return the actual name for the token code
     */
    public String typeToName(int token)
    {
        switch (token)
        {
            case Token.EOF:
                return "EOF";
            case Token.EOL:
                return "EOL";
            case Token.ENTERWITH:
                return "ENTERWITH";
            case Token.LEAVEWITH:
                return "LEAVEWITH";
            case Token.RETURN:
                return "RETURN";
            case Token.GOTO:
                return "GOTO";
            case Token.IFEQ:
                return "IFEQ";
            case Token.IFNE:
                return "IFNE";
            case Token.SETNAME:
                return "SETNAME";
            case Token.BITOR:
                return "BITOR";
            case Token.BITXOR:
                return "BITXOR";
            case Token.BITAND:
                return "BITAND";
            case Token.EQ:
                return "EQ";
            case Token.NE:
                return "NE";
            case Token.LT:
                return "LT";
            case Token.LE:
                return "LE";
            case Token.GT:
                return "GT";
            case Token.GE:
                return "GE";
            case Token.LSH:
                return "LSH";
            case Token.RSH:
                return "RSH";
            case Token.URSH:
                return "URSH";
            case Token.ADD:
                return "ADD";
            case Token.SUB:
                return "SUB";
            case Token.MUL:
                return "MUL";
            case Token.DIV:
                return "DIV";
            case Token.MOD:
                return "MOD";
            case Token.NOT:
                return "NOT";
            case Token.BITNOT:
                return "BITNOT";
            case Token.POS:
                return "POS";
            case Token.NEG:
                return "NEG";
            case Token.NEW:
                return "NEW";
            case Token.DELPROP:
                return "DELPROP";
            case Token.TYPEOF:
                return "TYPEOF";
            case Token.GETPROP:
                return "GETPROP";
            case Token.SETPROP:
                return "SETPROP";
            case Token.GETELEM:
                return "GETELEM";
            case Token.SETELEM:
                return "SETELEM";
            case Token.CALL:
                return "CALL";
            case Token.NAME:
                return "NAME";
            case Token.NUMBER:
                return "NUMBER";
            case Token.STRING:
                return "STRING";
            case Token.NULL:
                return "NULL";
            case Token.THIS:
                return "THIS";
            case Token.FALSE:
                return "FALSE";
            case Token.TRUE:
                return "TRUE";
            case Token.SHEQ:
                return "SHEQ";
            case Token.SHNE:
                return "SHNE";
            case Token.REGEXP:
                return "REGEXP";
            case Token.BINDNAME:
                return "BINDNAME";
            case Token.THROW:
                return "THROW";
            case Token.RETHROW:
                return "RETHROW";
            case Token.IN:
                return "IN";
            case Token.INSTANCEOF:
                return "INSTANCEOF";
            case Token.LOCAL_LOAD:
                return "LOCAL_LOAD";
            case Token.GETVAR:
                return "GETVAR";
            case Token.SETVAR:
                return "SETVAR";
            case Token.CATCH_SCOPE:
                return "CATCH_SCOPE";
            case Token.ENUM_INIT_KEYS:
                return "ENUM_INIT_KEYS";
            case Token.ENUM_INIT_VALUES:
                return "ENUM_INIT_VALUES";
            case Token.ENUM_NEXT:
                return "ENUM_NEXT";
            case Token.ENUM_ID:
                return "ENUM_ID";
            case Token.THISFN:
                return "THISFN";
            case Token.RETURN_RESULT:
                return "RETURN_RESULT";
            case Token.ARRAYLIT:
                return "ARRAYLIT";
            case Token.OBJECTLIT:
                return "OBJECTLIT";
            case Token.GET_REF:
                return "GET_REF";
            case Token.SET_REF:
                return "SET_REF";
            case Token.DEL_REF:
                return "DEL_REF";
            case Token.REF_CALL:
                return "REF_CALL";
            case Token.REF_SPECIAL:
                return "REF_SPECIAL";
            case Token.DEFAULTNAMESPACE:
                return "DEFAULTNAMESPACE";
            case Token.ESCXMLTEXT:
                return "ESCXMLTEXT";
            case Token.ESCXMLATTR:
                return "ESCXMLATTR";
            case Token.REF_MEMBER:
                return "REF_MEMBER";
            case Token.REF_NS_MEMBER:
                return "REF_NS_MEMBER";
            case Token.REF_NAME:
                return "REF_NAME";
            case Token.REF_NS_NAME:
                return "REF_NS_NAME";
            case Token.TRY:
                return "TRY";
            case Token.SEMI:
                return "SEMI";
            case Token.LB:
                return "LB";
            case Token.RB:
                return "RB";
            case Token.LC:
                return "LC";
            case Token.RC:
                return "RC";
            case Token.LP:
                return "LP";
            case Token.RP:
                return "RP";
            case Token.COMMA:
                return "COMMA";
            case Token.ASSIGN:
                return "ASSIGN";
            case Token.ASSIGN_BITOR:
                return "ASSIGN_BITOR";
            case Token.ASSIGN_BITXOR:
                return "ASSIGN_BITXOR";
            case Token.ASSIGN_BITAND:
                return "ASSIGN_BITAND";
            case Token.ASSIGN_LSH:
                return "ASSIGN_LSH";
            case Token.ASSIGN_RSH:
                return "ASSIGN_RSH";
            case Token.ASSIGN_URSH:
                return "ASSIGN_URSH";
            case Token.ASSIGN_ADD:
                return "ASSIGN_ADD";
            case Token.ASSIGN_SUB:
                return "ASSIGN_SUB";
            case Token.ASSIGN_MUL:
                return "ASSIGN_MUL";
            case Token.ASSIGN_DIV:
                return "ASSIGN_DIV";
            case Token.ASSIGN_MOD:
                return "ASSIGN_MOD";
            case Token.HOOK:
                return "HOOK";
            case Token.COLON:
                return "COLON";
            case Token.OR:
                return "OR";
            case Token.AND:
                return "AND";
            case Token.INC:
                return "INC";
            case Token.DEC:
                return "DEC";
            case Token.DOT:
                return "DOT";
            case Token.FUNCTION:
                return "FUNCTION";
            case Token.EXPORT:
                return "EXPORT";
            case Token.IMPORT:
                return "IMPORT";
            case Token.IF:
                return "IF";
            case Token.ELSE:
                return "ELSE";
            case Token.SWITCH:
                return "SWITCH";
            case Token.CASE:
                return "CASE";
            case Token.DEFAULT:
                return "DEFAULT";
            case Token.WHILE:
                return "WHILE";
            case Token.DO:
                return "DO";
            case Token.FOR:
                return "FOR";
            case Token.BREAK:
                return "BREAK";
            case Token.CONTINUE:
                return "CONTINUE";
            case Token.VAR:
                return "VAR";
            case Token.WITH:
                return "WITH";
            case Token.CATCH:
                return "CATCH";
            case Token.FINALLY:
                return "FINALLY";
            case Token.VOID:
                return "VOID";
            case Token.RESERVED:
                return "RESERVED";
            case Token.EMPTY:
                return "EMPTY";
            case Token.BLOCK:
                return "BLOCK";
            case Token.LABEL:
                return "LABEL";
            case Token.TARGET:
                return "TARGET";
            case Token.LOOP:
                return "LOOP";
            case Token.EXPR_VOID:
                return "EXPR_VOID";
            case Token.EXPR_RESULT:
                return "EXPR_RESULT";
            case Token.JSR:
                return "JSR";
            case Token.SCRIPT:
                return "SCRIPT";
            case Token.TYPEOFNAME:
                return "TYPEOFNAME";
            case Token.USE_STACK:
                return "USE_STACK";
            case Token.SETPROP_OP:
                return "SETPROP_OP";
            case Token.SETELEM_OP:
                return "SETELEM_OP";
            case Token.LOCAL_BLOCK:
                return "LOCAL_BLOCK";
            case Token.SET_REF_OP:
                return "SET_REF_OP";
            case Token.DOTDOT:
                return "DOTDOT";
            case Token.COLONCOLON:
                return "COLONCOLON";
            case Token.XML:
                return "XML";
            case Token.DOTQUERY:
                return "DOTQUERY";
            case Token.XMLATTR:
                return "XMLATTR";
            case Token.XMLEND:
                return "XMLEND";
            case Token.TO_OBJECT:
                return "TO_OBJECT";
            case Token.TO_DOUBLE:
                return "TO_DOUBLE";
        }

        // Token without name
        throw new IllegalStateException(String.valueOf(token));
    }

    /**
     * <i>Extracted from rhino for debugging puproses.</i> Convert a keyword
     * token to a name string for use with the
     * {@link Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER} feature.
     * 
     * @param token
     *        A token
     * @return the corresponding name string
     */
    public static String keywordToName(int token)
    {
        switch (token)
        {
            case Token.BREAK:
                return "break";
            case Token.CASE:
                return "case";
            case Token.CONTINUE:
                return "continue";
            case Token.DEFAULT:
                return "default";
            case Token.DELPROP:
                return "delete";
            case Token.DO:
                return "do";
            case Token.ELSE:
                return "else";
            case Token.FALSE:
                return "false";
            case Token.FOR:
                return "for";
            case Token.FUNCTION:
                return "function";
            case Token.IF:
                return "if";
            case Token.IN:
                return "in";
            case Token.NEW:
                return "new";
            case Token.NULL:
                return "null";
            case Token.RETURN:
                return "return";
            case Token.SWITCH:
                return "switch";
            case Token.THIS:
                return "this";
            case Token.TRUE:
                return "true";
            case Token.TYPEOF:
                return "typeof";
            case Token.VAR:
                return "var";
            case Token.VOID:
                return "void";
            case Token.WHILE:
                return "while";
            case Token.WITH:
                return "with";
            case Token.CATCH:
                return "catch";
            case Token.FINALLY:
                return "finally";
            case Token.INSTANCEOF:
                return "instanceof";
            case Token.THROW:
                return "throw";
            case Token.TRY:
                return "try";
            default:
                return null;
        }
    }
}
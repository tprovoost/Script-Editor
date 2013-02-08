package plugins.tprovoost.scripteditor.scriptinghandlers;

import icy.gui.main.MainInterface;
import icy.image.IcyBufferedImage;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginInstaller;
import icy.plugin.PluginLoader;
import icy.plugin.PluginRepositoryLoader;
import icy.sequence.Sequence;
import icy.util.ClassUtil;

import java.awt.Color;
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
import java.util.LinkedList;
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
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.FunctionCompletion;
import org.fife.ui.autocomplete.ParameterizedCompletion.Parameter;
import org.fife.ui.autocomplete.VariableCompletion;
import org.fife.ui.rtextarea.Gutter;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.Assignment;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ElementGet;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.IfStatement;
import org.mozilla.javascript.ast.NewExpression;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.StringLiteral;

import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scripteditor.completion.types.BasicJavaClassCompletion;
import plugins.tprovoost.scriptenginehandler.ScriptEngineHandler;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;
import sun.org.mozilla.javascript.internal.ErrorReporter;
import sun.org.mozilla.javascript.internal.EvaluatorException;
import sun.org.mozilla.javascript.internal.ImporterTopLevel;
import sun.org.mozilla.javascript.internal.RhinoException;
import sun.org.mozilla.javascript.internal.ScriptableObject;

import com.sun.script.javascript.RhinoScriptEngine;

public class JSScriptingHandlerRhino extends ScriptingHandler
{
    private String currentText;

    /** Contains all functions created in {@link #resolveCallType(AstNode, String, boolean)}. */
    private LinkedList<IcyFunctionBlock> functionBlocksToResolve = new LinkedList<IcyFunctionBlock>();
    private ErrorReporter errorReporter = new ErrorReporter()
    {

        @Override
        public void warning(String message, String sourceName, int line, String lineSource, int lineOffset)
        {
            Document doc = errorOutput.getDocument();
            try
            {
                Style style = errorOutput.getStyle("warning");
                if (style == null)
                    style = errorOutput.addStyle("warning", null);
                StyleConstants.setForeground(style, Color.blue);
                String text = message + " at " + (line + 1) + "\n   in " + lineSource + "\n      at column ("
                        + lineOffset + ")";
                doc.insertString(doc.getLength(), text + "\n", style);
            }
            catch (BadLocationException e)
            {
            }
        }

        @Override
        public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource,
                int lineOffset)
        {
            return new EvaluatorException(message, sourceName, line + 1, lineSource, lineOffset);
        }

        @Override
        public void error(String message, String sourceName, int line, String lineSource, int lineOffset)
        {
            if (errorOutput != null)
            {
                Document doc = errorOutput.getDocument();
                try
                {

                    Style style = errorOutput.getStyle("error");
                    if (style == null)
                        style = errorOutput.addStyle("error", null);
                    StyleConstants.setForeground(style, Color.red);
                    String text = message + " at " + (line + 1) + "\n   in " + lineSource + "\n      at column ("
                            + lineOffset + ")";
                    doc.insertString(doc.getLength(), text + "\n", style);
                }
                catch (BadLocationException e)
                {
                }
            }
        }
    };

    public JSScriptingHandlerRhino(DefaultCompletionProvider provider, JTextComponent textArea, Gutter gutter,
            boolean autocompilation)
    {
        super(provider, "javascript", textArea, gutter, autocompilation);
    }

    @Override
    public void eval(ScriptEngine engine, String s) throws ScriptException
    {
        // uses Context from Rhino integrated in JRE or impossibility
        // to use already defined methods in ScriptEngine, such as println or getImage
        sun.org.mozilla.javascript.internal.Context context = sun.org.mozilla.javascript.internal.Context.enter();
        context.setApplicationClassLoader(PluginLoader.getLoader());
        context.setErrorReporter(errorReporter);
        try
        {
            ScriptableObject scriptable = new ImporterTopLevel(context);
            Bindings bs = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            for (String key : bs.keySet())
            {
                Object o = bs.get(key);
                scriptable.put(key, scriptable, o);
            }
            sun.org.mozilla.javascript.internal.Script script = context.compileString(s, "script", 0, null);
            script.exec(context, scriptable);
            for (Object o : scriptable.getIds())
            {
                String key = (String) o;
                bs.put(key, scriptable.get(key, scriptable));
            }
        }
        catch (RhinoException e)
        {
            throw new ScriptException(e.details(), e.sourceName(), e.lineNumber() + 1);
        }
        finally
        {
            sun.org.mozilla.javascript.internal.Context.exit();
        }
    }

    @Override
    public void installDefaultLanguageCompletions(String language)
    {
        ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());
        HashMap<String, Class<?>> engineFunctions = engineHandler.getEngineFunctions();
        HashMap<String, Class<?>> engineVariables = engineHandler.getEngineVariables();

        // IMPORT PACKAGES
        try
        {
            importJavaScriptPackages(getEngine());
        }
        catch (ScriptException e1)
        {
        }

        // ArrayList<Parameter> params = new ArrayList<Parameter>();
        ScriptEngine engine = getEngine();
        if (engine == null)
            return;

        // HARDCODED ITEMS, TO BE REMOVED OR ADDED IN AN XML
        String mainInterface = MainInterface.class.getName();
        try
        {
            engine.eval("function getSequence() { return Packages.icy.main.Icy.getMainInterface().getFocusedSequence() }");
            FunctionCompletion c = new FunctionCompletion(provider, "getSequence", Sequence.class.getName());
            c.setDefinedIn(mainInterface);
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
            engine.eval("function getImage() { return Packages.icy.main.Icy.getMainInterface().getFocusedImage(); }");
            FunctionCompletion c = new FunctionCompletion(provider, "getImage", IcyBufferedImage.class.getName());
            c.setDefinedIn(mainInterface);
            c.setShortDescription("Returns the current image viewed in the focused sequence.");
            c.setReturnValueDescription("Returns the focused Image, returns null if no sequence opened");
            provider.addCompletion(c);
            engineFunctions.put("getImage", IcyBufferedImage.class);
        }
        catch (ScriptException e)
        {
            System.out.println(e.getMessage());
        }

        try
        {
            engine.eval("gui = Packages.icy.main.Icy.getMainInterface()");
            VariableCompletion vc = new VariableCompletion(provider, "gui", mainInterface);
            vc.setDefinedIn(mainInterface);
            vc.setShortDescription("Returns the sequence under focus. Returns null if no sequence opened.");
            provider.addCompletion(vc);
            engineVariables.put("gui", MainInterface.class);
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
    }

    @Override
    public void installMethods(ScriptEngine engine, ArrayList<Method> methods)
    {
        // hardcoded functions, to remove in the future
        try
        {
            engine.eval("function getSequence() { return Packages.icy.main.Icy.getMainInterface().getFocusedSequence() }");
        }
        catch (ScriptException e1)
        {
        }
        try
        {
            engine.eval("function getImage() { return Packages.icy.main.Icy.getMainInterface().getFocusedImage(); }");
        }
        catch (ScriptException e1)
        {
        }
        try
        {
            engine.eval("gui = Packages.icy.main.Icy.getMainInterface()");
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

            try
            {
                if ((provider.getCompletionByInputText(imported)) == null)
                {
                    provider.addCompletion(new BasicJavaClassCompletion(provider, ClassUtil.findClass(imported)));
                }
            }
            catch (ClassNotFoundException e)
            {
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
            scriptDeclaredImports.add(imported);

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

    @Override
    public void organizeImports(JTextComponent tc)
    {
        organizeImportsStatic(tc);
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
        currentText = s;
        final CompilerEnvirons comp = new CompilerEnvirons();
        comp.initFromContext(context);
        final Parser parser = new Parser(comp, comp.getErrorReporter());
        AstRoot root;
        root = parser.parse(s, "", 1);

        if (root == null || !root.hasChildren())
            return;
        // no issue, removes variables
        for (Completion c : variableCompletions)
            provider.removeCompletion(c);
        variableCompletions.clear();
        // functionBlocksToResolve.clear();
        if (DEBUG)
            dumpTree(root, root, 1, "");

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
    private void registerVariables(String text, AstNode n, AstRoot root) throws ScriptException
    {
        if (n == null)
            return;
        if (DEBUG)
            System.out.println("current node: " + typeToName(n.getType()));
        // register current
        Completion c = generateCompletion(n, root, text);
        if (c != null)
        {
            boolean alreadyExists = false;
            for (int i = 0; i < variableCompletions.size() && !alreadyExists; ++i)
            {
                if (variableCompletions.get(i).compareTo(c) == 0)
                {
                    if (textArea.getCaret().getDot() > n.getAbsolutePosition())
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
            AstNode child = (AstNode) n.getFirstChild();
            while (child != null)
            {
                registerVariables(text, child, root);
                child = (AstNode) child.getNext();
            }
        }
        else
        {
            switch (n.getType())
            {
                case Token.IF:
                    IfStatement nIf = (IfStatement) n;
                    registerVariables(text, nIf.getThenPart(), root);
                    registerVariables(text, nIf.getElsePart(), root);
                    break;
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
    private Completion generateCompletion(AstNode n, AstRoot root, String text) throws ScriptException
    {
        switch (n.getType())
        {
            case Token.EXPR_RESULT:
            {
                AstNode expression = ((ExpressionStatement) n).getExpression();
                if (expression instanceof Assignment)
                {
                    AstNode left = ((Assignment) expression).getLeft();
                    AstNode right = ((Assignment) expression).getRight();
                    Class<?> type = resolveRight(right, text);
                    String typeString = "";
                    if (type != null)
                        typeString = IcyCompletionProvider.getType(type, true);
                    VariableCompletion c = new VariableCompletion(provider, left.getString(), typeString);
                    c.setSummary("variable");
                    c.setDefinedIn("script");
                    c.setRelevance(RELEVANCE_HIGH);
                    addVariableDeclaration(c.getName(), type, n.getAbsolutePosition());
                    return c;
                }
                else if (expression instanceof FunctionCall)
                {
                    AstNode target = ((FunctionCall) expression).getTarget();
                    if (!(target.getType() == Token.NAME && (target.getString().contentEquals("importClass") || target
                            .getString().contentEquals("importPackage"))))
                        resolveCallType(expression, text, false);
                }
                else if (expression instanceof PropertyGet)
                {
                    // Do nothing
                }
            }
                break;

            case Token.CALL:
                resolveCallType(n, text, false);
                break;
        }
        return null;
    }

    private Class<?> resolveCallType(AstNode n, String text, boolean noerror)
    {
        ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());
        int offset = n.getAbsolutePosition();
        String s = buildFunction2(n);
        // System.out.println(s);
        boolean containsNew = s.contains("new ");
        if (containsNew)
        {
            s = s.substring("new ".length());
        }

        // create a regex pattern
        Pattern p = Pattern.compile("\\w(\\w|\\.|\\[|\\])*\\((\\w|\\.|\\[|,|\\]|\\(|\\)| )*\\)");
        Matcher match = p.matcher(s);

        int idxP1 = 0;
        int idxP2;
        int decal = 0;
        if (match.find(0))
        {
            String firstCall = match.group(0);
            try
            {
                idxP1 = firstCall.indexOf('(');
                idxP2 = firstCall.indexOf(')');
                decal += idxP2 + 1;
                int lastDot = firstCall.substring(0, idxP1).lastIndexOf('.');
                Class<?> clazz = null;

                // get the className (or binding function name if it is the
                // case)
                String classNameOrFunctionNameOrVariable;
                if (lastDot != -1)
                    classNameOrFunctionNameOrVariable = firstCall.substring(0, lastDot);
                else
                    classNameOrFunctionNameOrVariable = firstCall.substring(0, idxP1);

                // get the arguments
                String argsString = firstCall.substring(idxP1 + 1, idxP2);

                // separate arguments
                String[] args = argsString.split(",");

                // --------------------------
                // TEST IF IS FUNCTION BINDED
                // --------------------------
                clazz = localFunctions.get(classNameOrFunctionNameOrVariable);
                if (clazz == null)
                {
                    clazz = ScriptEngineHandler.getEngineHandler(getEngine()).getEngineFunctions()
                            .get(classNameOrFunctionNameOrVariable);
                    if (classNameOrFunctionNameOrVariable.contentEquals("println")
                            || classNameOrFunctionNameOrVariable.contentEquals("print"))
                        clazz = void.class;
                }

                // -------------------------------------------
                // IT IS SOMETHING ELSE, PERFORM VARIOUS TESTS
                // -------------------------------------------

                // is it a script defined variable?
                if (clazz == null)
                    clazz = getVariableDeclaration(classNameOrFunctionNameOrVariable, offset);

                // is it an engine variable?
                if (clazz == null)
                    clazz = engineHandler.getEngineVariables().get(classNameOrFunctionNameOrVariable);

                // is it a class?
                if (clazz == null)
                {
                    clazz = resolveClassDeclaration(classNameOrFunctionNameOrVariable);
                }

                // unknown type
                if (clazz == null)
                {
                    System.out.println("Unknown: " + classNameOrFunctionNameOrVariable + " at line: " + n.getLineno());
                    return null;
                }

                // generate the Class<?> arguments
                Class<?> clazzes[];
                if (argsString.isEmpty())
                {
                    clazzes = new Class<?>[0];
                }
                else
                {
                    clazzes = new Class<?>[args.length];
                    for (int i = 0; i < clazzes.length; ++i)
                        clazzes[i] = resolveClassDeclaration(args[i]);
                    clazzes = getGenericNumberTypes(text, n, clazz, firstCall.substring(lastDot + 1, idxP1), clazzes);
                }

                // the first type!
                Class<?> returnType = clazz;

                String call = firstCall.substring(lastDot + 1, idxP1);
                if (lastDot != -1)
                {
                    // Static access to a class
                    Method m = resolveMethod(clazz, call, clazzes);
                    returnType = m.getReturnType();
                }
                IcyFunctionBlock fb = functionBlocksToResolve.pop();
                fb.setReturnType(returnType);
                if (DEBUG)
                    System.out.println("function edited: (" + (fb.getStartOffset() + n.getPosition()) + ") "
                            + text.substring(offset));
                blockFunctions.put(fb.getStartOffset(), fb);
                // iterate over the next functions, based on the returnType
                while (match.find(decal) && !(firstCall = match.group()).isEmpty())
                {
                    if (returnType == void.class)
                    {
                        System.out.println("Void return, impossible to call something else on it. at line:"
                                + +n.getLineno());
                    }
                    idxP1 = firstCall.indexOf('(');
                    idxP2 = firstCall.indexOf(')');
                    decal += idxP2 + 2; // account for ) and .
                    argsString = firstCall.substring(idxP1 + 1, idxP2);
                    args = argsString.split(",");
                    if (argsString.isEmpty())
                    {
                        clazzes = new Class<?>[0];
                    }
                    else
                    {
                        clazzes = new Class<?>[args.length];
                        for (int i = 0; i < clazzes.length; ++i)
                            clazzes[i] = resolveClassDeclaration(args[i]);
                        lastDot = firstCall.substring(0, idxP1).lastIndexOf('.');
                        if (lastDot < 0)
                        {
                            lastDot = -1; // in case of new for instance.
                        }
                        clazzes = getGenericNumberTypes(text, n, returnType, firstCall.substring(lastDot + 1, idxP1),
                                clazzes);
                    }
                    String call2;
                    if (lastDot != -1)
                        call2 = firstCall.substring(lastDot + 1, idxP1);
                    else
                        call2 = firstCall.substring(0, idxP1);
                    if (call2.contentEquals("newInstance"))
                    {
                        clazz.getConstructor(clazzes);
                    }
                    else
                    {
                        Method m = resolveMethod(returnType, firstCall.substring(0, idxP1), clazzes);
                        returnType = m.getReturnType();
                    }

                    fb = functionBlocksToResolve.pop();
                    fb.setReturnType(returnType);
                    if (DEBUG)
                        System.out.println("function edited: (" + (fb.getStartOffset() + n.getPosition()) + ") "
                                + text.substring(offset));
                    blockFunctions.put(fb.getStartOffset(), fb);
                }
                return returnType;
            }
            catch (SecurityException e)
            {
            }
            catch (NoSuchMethodException e)
            {
                System.out.println("Var Detection: no such method: " + e.getLocalizedMessage() + " at line "
                        + n.getLineno());
            }
        }
        return null;
    }

    /**
     * Fix: Issue with same name functions, will always use the first one.
     * 
     * @param clazz
     * @param function
     * @param argsClazzes
     * @param commandStartOffset
     * @param commandEndOffset
     * @return
     */
    private Class<?>[] getGenericNumberTypes(String text, AstNode n, Class<?> clazz, String function,
            Class<?>[] argsClazzes)
    {
        Class<?>[] toReturn = new Class<?>[argsClazzes.length];
        int offset = n.getAbsolutePosition();
        String fullCommand = text.substring(offset, offset + n.getLength());
        int idxStart = fullCommand.indexOf(function);

        if (idxStart == -1)
            return argsClazzes;

        int idxP1 = fullCommand.indexOf('(', idxStart);
        int idxP2 = fullCommand.indexOf(')', idxStart);

        if (idxP1 == -1 || idxP2 == -1)
            return argsClazzes;

        String argumentsChained = fullCommand.substring(idxP1 + 1, idxP2);
        String[] args = argumentsChained.split(",");

        if (args.length != argsClazzes.length)
            return argsClazzes;

        // FIXME here
        boolean hasNumber = false;
        for (int i = 0; i < argsClazzes.length; ++i)
        {
            if (argsClazzes[i] == Number.class)
                hasNumber = true;
            toReturn[i] = argsClazzes[i];
        }
        if (hasNumber)
        {
            for (Method m : clazz.getMethods())
            {
                if (m.getName().contentEquals(function))
                {
                    Class<?> params[] = m.getParameterTypes();
                    boolean ok = true;
                    if (params.length == argsClazzes.length)
                    {
                        for (int i = 0; i < params.length && ok; ++i)
                        {
                            if (params[i].isAssignableFrom(argsClazzes[i]))
                                toReturn[i] = params[i];
                            else if (params[i].isPrimitive())
                            {
                                if ((params[i] == float.class || params[i] == double.class) && args[i].contains("."))
                                    toReturn[i] = params[i];
                                else if (!(params[i] == float.class || params[i] == double.class)
                                        && !args[i].contains("."))
                                    toReturn[i] = params[i];
                                else
                                    break;
                            }
                            else
                                break;
                        }
                    }
                }
            }
            return toReturn;
        }
        else
            return toReturn;
    }

    private Class<?> resolveRight(AstNode right, String text) throws ScriptException
    {
        switch (right.getType())
        {
            case Token.NUMBER:
                return Number.class;

            case Token.CALL:
                return resolveCallType(right, text, false);

            case Token.FUNCTION:
                return Void.class;

            case Token.NEW:
            {
                NewExpression nexp = (NewExpression) right;
                AstNode target = nexp.getTarget();
                if (target != null)
                {
                    String className = generateClassName(target, "");
                    return resolveClassDeclaration(className);
                }
            }
            case Token.ARRAYLIT:
                return Object[].class;
            case Token.GETPROP:
            {
                AstNode target = ((PropertyGet) right).getTarget();
                if (target.getType() == Token.GETELEM)
                {
                    // array
                    String rightStr = generateClassName(right, "");
                    // Class<?> clazz = resolveArrayItemTypeComponent(target);
                    // clazz = createArrayItemType(clazz, target);
                    if (rightStr.contentEquals("length"))
                        return int.class;
                }
                else
                {
                    // class
                    String className = generateClassName(right, "");
                    Class<?> clazz = resolveClassDeclaration(className);
                    if (clazz != null)
                        return clazz;
                    // try if it is an enum
                    int idx = className.lastIndexOf('.');
                    if (idx != -1)
                    {
                        clazz = resolveClassDeclaration(className.substring(0, idx));
                        return clazz;
                    }
                }
                break;
            }
            case Token.GETELEM:
            {
                // access a table
                ElementGet get = (ElementGet) right;
                AstNode index = get.getElement();
                AstNode target = get.getTarget();
                Class<?> clazz = resolveArrayItemTypeComponent(target);
                clazz = createArrayItemType(clazz, target);
                return clazz;
            }
        }
        return null;
    }

    private Class<?> resolveArrayItemTypeComponent(AstNode node)
    {
        int type = node.getType();
        if (type == Token.NAME)
        {
            String varname = node.getString();
            return getVariableDeclaration(varname, node.getAbsolutePosition());
        }
        else if (type == Token.GETELEM)
        {
            return resolveArrayItemTypeComponent(((ElementGet) node).getTarget());
        }
        return null;
    }

    private Class<?> createArrayItemType(Class<?> clazz, AstNode node)
    {
        int type = node.getType();
        if (type == Token.GETELEM)
        {
            return createArrayItemType(clazz.getComponentType(), ((ElementGet) node).getTarget());
        }
        else if (type == Token.NAME)
        {
            clazz = clazz.getComponentType();
        }
        return clazz;
    }

    private Method resolveMethod(Class<?> clazz, String name, Class<?>[] parameterTypes) throws SecurityException,
            NoSuchMethodException
    {
        try
        {
            return clazz.getMethod(name, parameterTypes);
        }
        catch (SecurityException e)
        {
        }
        catch (NoSuchMethodException e)
        {
        }
        L1: for (Method m : clazz.getMethods())
        {
            Class<?>[] types = m.getParameterTypes();
            if (m.getName().contentEquals(name) && types.length == parameterTypes.length)
            {
                // check types super etc
                for (int i = 0; i < types.length; ++i)
                {
                    if (types[i] == null || parameterTypes[i] == null || !types[i].isAssignableFrom(parameterTypes[i]))
                        continue L1;
                }
                return m;
            }
        }
        return clazz.getMethod(name, parameterTypes);
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

    private String generateClassName(Node n, String toReturn)
    {
        if (n != null)
        {
            if (n.getType() == Token.GETPROP)
            {
                String left = generateClassName(((PropertyGet) n).getLeft(), toReturn);
                String right = generateClassName(((PropertyGet) n).getRight(), toReturn);
                toReturn += left + (left.contentEquals("") ? "" : ".") + right;
            }
            else if (n.getType() == Token.NAME)
            {
                return n.getString();
            }
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
    private void dumpTree(Node n, AstRoot root, int commandIdx, String decal)
    {
        if (n == null)
            return;
        System.out.print(commandIdx + ": " + decal + typeToName(n.getType()));
        switch (n.getType())
        {
            case Token.EXPR_RESULT:
                System.out.println();
                AstNode expression = ((ExpressionStatement) n).getExpression();
                if (expression instanceof Assignment)
                {
                    Node left = ((Assignment) expression).getLeft();
                    Node right = ((Assignment) expression).getRight();
                    System.out.println("");
                    int level = commandIdx + 1;
                    dumpTree(left, root, level, "-" + decal);
                    dumpTree(right, root, level, "-" + decal);
                }
                break;
            case Token.GETPROP:
                Node propLeft = ((PropertyGet) n).getLeft();
                Node propRight = ((PropertyGet) n).getRight();
                System.out.println(":");
                int level = commandIdx + 1;
                dumpTree(propLeft, root, level, "-" + decal);
                dumpTree(propRight, root, level, "-" + decal);
                break;
            case Token.BINDNAME:
                // System.out.println("bindname");
            case Token.STRING:
                StringLiteral str = (StringLiteral) n;
                System.out.println(": " + str.getValue());
                break;
            case Token.NAME:
                System.out.println(": " + n.getString());
                break;
            case Token.NUMBER:
                System.out.println(": " + n.getDouble());
                break;
            case Token.CALL:
                System.out.println();
                level = commandIdx + 1;
                FunctionCall fn = ((FunctionCall) n);
                for (AstNode arg : fn.getArguments())
                {
                    dumpTree(arg, root, level, decal);
                }
                dumpTree(fn.getTarget(), root, level, "-" + decal);
                break;
            case Token.FUNCTION:
                FunctionNode fn2 = (FunctionNode) n;
                System.out.print(" " + fn2.getName());
                break;
            case Token.IF:
                System.out.println();
                IfStatement nIf = (IfStatement) n;
                level = commandIdx + 1;
                dumpTree(nIf.getThenPart(), root, level, "-" + decal);
                dumpTree(nIf.getElsePart(), root, level, "-" + decal);
                break;
            case Token.BLOCK:
                System.out.println();
                Scope scope = (Scope) n;
                Node child = scope.getFirstChild();
                level = commandIdx + 1;
                while (child != null)
                {
                    dumpTree(child, root, level, "-" + decal);
                    child = child.getNext();
                }
                break;
            case Token.GETELEM:
                System.out.println();
                level = commandIdx + 1;
                ElementGet get = (ElementGet) n;
                dumpTree(get.getElement(), root, level, "-" + decal);
                dumpTree(get.getTarget(), root, level, "-" + decal);
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

    private String buildFunction2(AstNode n)
    {
        String callName = "";

        callName = buildFunctionRecursive(callName, n);
        if (!callName.isEmpty())
        {

            // removes the last dot
            if (callName.startsWith("."))
                callName = callName.substring(1);

            // removes eventual "Packages." String
            if (callName.startsWith("Packages.") || callName.startsWith("packages."))
                callName = callName.substring("Packages.".length());

        }
        return callName;
    }

    /**
     * Recursive version
     * 
     * @throws ScriptException
     */
    private String buildFunctionRecursive(String elem, AstNode n)
    {
        if (n != null)
        {
            int type;
            if ((type = n.getType()) == Token.GETPROP)
            {
                AstNode propLeft = ((PropertyGet) n).getLeft();
                AstNode propRight = ((PropertyGet) n).getRight();

                String left = buildFunctionRecursive(elem, propLeft);
                String right = buildFunctionRecursive(elem, propRight);
                return left + right + elem;
            }
            else if (type == Token.CALL)
            {
                FunctionCall fn = ((FunctionCall) n);
                String args = "";
                args += "(";
                int i = 0;
                for (AstNode arg : fn.getArguments())
                {
                    if (i != 0)
                        args += ",";
                    Class<?> typeC = getRealType(arg);
                    if (typeC != null)
                        args += typeC.getName();
                    else
                        args += "unknown";
                    i++;
                }
                args += ")";
                String functionName = "";
                AstNode target = fn.getTarget();
                String toReturn;
                int targetType = target.getType();
                if (targetType == Token.NAME)
                {
                    functionName = target.getString();
                    toReturn = functionName + args;
                }
                else if (targetType == Token.GETPROP)
                {
                    functionName = ((PropertyGet) target).getRight().getString();
                    toReturn = buildFunctionRecursive(elem, ((PropertyGet) target).getLeft()) + "." + functionName
                            + args;
                }
                else if (targetType == Token.GETELEM)
                {
                    ElementGet get = (ElementGet) target;
                    elem = buildFunctionRecursive("", get.getElement());
                    functionName = elem.substring(0, elem.indexOf('('));
                    String targetName = buildFunctionRecursive("", get.getTarget());
                    toReturn = targetName + "." + elem;
                }
                else
                    toReturn = elem;
                int rp = fn.getRp();
                if (rp != -1)
                {
                    rp = n.getAbsolutePosition() + rp + 1;
                    if (DEBUG)
                        System.out.println("function found:" + functionName);
                    IcyFunctionBlock fb = new IcyFunctionBlock(functionName, rp, null);
                    functionBlocksToResolve.add(fb);
                }
                return toReturn;
            }
            else if (type == Token.NAME)
            {
                // if (functionNext)
                // callName = "." + n.getString() + "()" + callName;
                // else
                return "." + n.getString();
                // return buildFunctionRecursive(callName, n.getFirstChild());
            }
            else if (type == Token.NUMBER)
            {
                return Number.class.getName();
            }
            else if (type == Token.STRING)
            {
                return ((StringLiteral) n).getValue();
            }
            else if (type == Token.GETELEM)
            {
                Class<?> clazz = resolveArrayItemTypeComponent(n);
                return clazz.getCanonicalName();
            }
        }
        return elem;
    }

    /**
     * Get the type of a variable definition.
     * 
     * @param n
     * @param commandStartOffset
     * @param commandEndOffset
     * @return
     * @throws ScriptException
     */
    public Class<?> getRealType(AstNode n)
    {
        if (n == null)
            return null;
        switch (n.getType())
        {
            case Token.NUMBER:
                return Number.class;
            case Token.STRING:
                return String.class;
            case Token.TRUE:
            case Token.FALSE:
                return boolean.class;
            case Token.NAME:
                return getVariableDeclaration(n.getString(), n.getAbsolutePosition());
            case Token.CALL:
                Class<?> res = resolveCallType(n, currentText, false);
                return res;
            case Token.GETPROP:
                // class wanted
                String className = generateClassName(n, "");
                Class<?> clazz = resolveClassDeclaration(className);
                if (clazz != null)
                    return clazz;
                // try if it is an enum
                int idx = className.lastIndexOf('.');
                if (idx != -1)
                {
                    clazz = resolveClassDeclaration(className.substring(0, idx));
                    return clazz;
                }
            case Token.ARRAYLIT:
                return Object[].class;
            case Token.NEW:
                NewExpression nexp = (NewExpression) n;
                AstNode target = nexp.getTarget();
                className = generateClassName(target, "");
                return resolveClassDeclaration(className);
        }

        return null;
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
        org.mozilla.javascript.ScriptableObject scope = context.initStandardObjects();

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
}

package plugins.tprovoost.scripteditor.completion;

import icy.gui.frame.progress.ProgressFrame;
import icy.util.ClassUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.FunctionCompletion;
import org.fife.ui.autocomplete.ParameterizedCompletion.Parameter;
import org.fife.ui.autocomplete.Util;
import org.fife.ui.autocomplete.VariableCompletion;

import plugins.tprovoost.scripteditor.completion.types.BasicJavaClassCompletion;
import plugins.tprovoost.scripteditor.scriptinghandlers.IcyFunctionBlock;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptingHandler;
import plugins.tprovoost.scriptenginehandler.ScriptEngineHandler;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;

public class IcyCompletionProvider extends DefaultCompletionProvider
{

    private ScriptingHandler handler;
    private boolean advanced = true;
    private static Comparator<Completion> comparatorFull = new Comparator<Completion>()
    {
        @Override
        public int compare(Completion c1, Completion c2)
        {
            int compare = ((Integer) c2.getRelevance()).compareTo(c1.getRelevance());
            if (compare == 0)
            {
                return c1.getInputText().compareTo(c2.getInputText());
            }
            return compare;
        }
    };

    public void setHandler(ScriptingHandler handler)
    {
        this.handler = handler;
    }

    @SuppressWarnings("unchecked")
    public void installMethods(ArrayList<Method> methods)
    {
        for (final Method method : methods)
        {
            Class<?> clazz = method.getDeclaringClass();
            // make sure the method is public and annotated
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers))
                continue;

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
                fParams.add(new Parameter(getType(paramTypes[i], true), "arg" + i));
                params += ",arg" + i;
            }
            if (params.length() > 0)
                params = params.substring(1);

            // the object for the provider
            ScriptFunctionCompletion sfc;
            if (Modifier.isStatic(method.getModifiers()))
                sfc = new ScriptFunctionCompletion(this, functionName, method);
            else
                sfc = new ScriptFunctionCompletion(this, method.getName(), method);
            sfc.setDefinedIn(clazz.getName());
            sfc.setParams(fParams);
            sfc.setRelevance(2);

            // remove existing completions with same name (since it is
            // impossible de have the same name in scripting).
            List<Completion> list = getCompletionByInputText(functionName);
            if (list != null)
                removeCompletion(list.get(0));
            addCompletion(sfc);
            list = getCompletionByInputText(clazz.getSimpleName());
            if (list == null)
                addCompletion(new BasicJavaClassCompletion(this, clazz));
        }
    }

    /**
     * @param localFunctions
     * @param engineTypesMethod
     * @param provider
     * @param engine
     * @param frame
     */
    @Deprecated
    public void findAllMethods(ScriptEngine engine, ProgressFrame frame)
    {

        ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(engine);
        HashMap<String, Class<?>> listFunction = engineHandler.getEngineFunctions();
        HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>> engineTypesMethod = engineHandler.getEngineTypesMethod();

        ArrayList<String> clazzes;

        try
        {
            clazzes = getClassNamesFromPackage("icy");
        }
        catch (IOException e1)
        {
            e1.printStackTrace();
            return;
        }
        if (frame != null)
            frame.setLength(clazzes.size());
        Collections.sort(clazzes);
        for (int idxClass = 0; idxClass < clazzes.size(); ++idxClass)
        {
            if (frame != null)
                frame.setPosition(idxClass);
            String className = clazzes.get(idxClass).replace('/', '.');
            try
            {
                // the current class
                Class<?> clazz = Class.forName(className);

                // get all methods
                Method[] methods = clazz.getMethods();

                // iterate through each
                for (final Method method : methods)
                {
                    ArrayList<Parameter> fParams = new ArrayList<Parameter>();

                    Class<?>[] paramTypes = method.getParameterTypes();

                    // get the parameters
                    for (int i = 0; i < paramTypes.length; ++i)
                    {
                        fParams.add(new Parameter(getType(paramTypes[i], true), "arg" + i));
                    }

                    ScriptFunctionCompletion sfc = new ScriptFunctionCompletion(this, method.getName(), method);
                    sfc.setDefinedIn(clazz.getName());
                    sfc.setParams(fParams);
                    BindingFunction blockFunction = method.getAnnotation(BindingFunction.class);
                    if (blockFunction == null)
                        sfc.setRelevance(2);
                    else
                        sfc.setRelevance(4);
                    addCompletion(sfc);
                    if (getCompletionByInputText(clazz.getSimpleName()) == null)
                        addCompletion(new BasicJavaClassCompletion(this, clazz));

                    if (listFunction != null)
                        listFunction.put(sfc.getMethodCall().substring("packages.".length()), method.getReturnType());
                    if (engineTypesMethod != null)
                    {
                        ArrayList<ScriptFunctionCompletion> methodsExisting = engineTypesMethod.get(clazz);
                        if (methodsExisting == null)
                            methodsExisting = new ArrayList<ScriptFunctionCompletion>();
                        methodsExisting.add(sfc);
                        engineTypesMethod.put(clazz, methodsExisting);
                    }
                }
            }
            catch (ClassNotFoundException e)
            {
            }
        }

    }

    @Override
    public List<Completion> getCompletions(JTextComponent comp)
    {
        List<Completion> toReturn = new ArrayList<Completion>();
        List<Completion> originalList = getCompletionsImpl(comp);
        List<Completion> inScriptVariables = new ArrayList<Completion>();
        List<Completion> inScriptBasic = new ArrayList<Completion>();
        List<Completion> inScriptFunctions = new ArrayList<Completion>();
        List<Completion> variables = new ArrayList<Completion>();
        List<Completion> basic = new ArrayList<Completion>();
        List<Completion> functions = new ArrayList<Completion>();

        // String enteredText = getAlreadyEnteredText(comp);

        for (Completion c : new ArrayList<Completion>(originalList))
        {
            String rText = c.getReplacementText();
            // int previousDotIdx = enteredText.lastIndexOf('.') + 1;
            // if (previousDotIdx < 0)
            // previousDotIdx = 0;
            // int nextDotIdx = rText.indexOf('.', previousDotIdx);
            int previousDotIdx = 0;
            int nextDotIdx = -1;

            if (c instanceof FunctionCompletion)
            {

                // the completion is a function.

                if (previousDotIdx == 0 && nextDotIdx == -1)
                {
                    if (!isFunctionAlreadyDeclared((FunctionCompletion) c, functions))
                        functions.add(c);
                }
                else
                {

                    String res;
                    if (nextDotIdx == -1)
                    {
                        res = rText.substring(previousDotIdx);
                        FunctionCompletion co = (FunctionCompletion) c;
                        FunctionCompletion c2 = new FunctionCompletion(c.getProvider(), res, co.getType());
                        c2.setReturnValueDescription(co.getReturnValueDescription());
                        c2.setDefinedIn(co.getDefinedIn());
                        c2.setShortDescription(co.getShortDescription());
                        c2.setSummary(co.getSummary());
                        c2.setRelevance(co.getRelevance());
                        List<Parameter> parameters = new ArrayList<Parameter>();
                        for (int i = 0; i < co.getParamCount(); ++i)
                            parameters.add(co.getParam(i));
                        if (c instanceof ScriptFunctionCompletion)
                            inScriptFunctions.add(c2);
                        else
                        {
                            if (!isFunctionAlreadyDeclared(c2, functions))
                                functions.add(c2);
                        }
                    }
                    else
                    {
                        res = rText.substring(previousDotIdx, nextDotIdx);
                        BasicCompletion c2 = duplicateBasicCompletion((BasicCompletion) c, res);
                        if (!exists(c2, basic))
                            basic.add(c2);
                    }
                }
                originalList.remove(c);
            }
            else if (c instanceof VariableCompletion)
            {

                if (previousDotIdx == 0 && nextDotIdx == -1)
                {
                    if (c instanceof ScriptFunctionCompletion)
                        inScriptVariables.add(c);
                    else
                        variables.add(c);
                }
                else
                {
                    String res;
                    if (nextDotIdx == -1)
                    {
                        res = rText.substring(previousDotIdx);
                        VariableCompletion co = (VariableCompletion) c;
                        VariableCompletion c2 = new VariableCompletion(co.getProvider(), res, co.getType());
                        c2.setDefinedIn(co.getDefinedIn());
                        c2.setShortDescription(co.getShortDescription());
                        c2.setSummary(co.getSummary());
                        c2.setRelevance(co.getRelevance());
                        if (!isFunctionAlreadyDeclared((FunctionCompletion) c2, variables))
                            variables.add(c2);
                    }
                    else
                    {
                        res = rText.substring(previousDotIdx, nextDotIdx);
                        BasicCompletion c2 = duplicateBasicCompletion((BasicCompletion) c, res);
                        if (!isFunctionAlreadyDeclared((FunctionCompletion) c2, basic))
                            basic.add(c2);
                    }
                }
                originalList.remove(c);
            }
            else if (c instanceof BasicCompletion)
            {
                // the completion is something else
                if (previousDotIdx == 0 && nextDotIdx == -1)
                {
                    basic.add(c);
                }
                else
                {
                    String res = nextDotIdx == -1 ? rText.substring(previousDotIdx) : rText.substring(previousDotIdx,
                            nextDotIdx);
                    BasicCompletion c2 = duplicateBasicCompletion((BasicCompletion) c, res);
                    if (!exists(c2, basic))
                        basic.add(c2);
                }
                originalList.remove(c);
            }
        }
        toReturn.addAll(inScriptVariables);
        toReturn.addAll(inScriptFunctions);
        toReturn.addAll(inScriptBasic);
        toReturn.addAll(variables);
        toReturn.addAll(functions);
        toReturn.addAll(basic);
        toReturn.addAll(originalList);
        Collections.sort(toReturn, comparatorFull);
        return toReturn;
    }

    @Override
    protected boolean isValidChar(char ch)
    {
        return super.isValidChar(ch) || ch == '\"';
    }

    protected boolean isValidCharStrict(char ch)
    {
        return super.isValidChar(ch) || ch == '.' || ch == '(' || ch == ')' || ch == ',' || ch == '\"';
    }

    public static boolean exists(Completion c, List<Completion> list)
    {
        boolean found = false;
        for (Completion ctmp : list)
        {
            if (ctmp.getReplacementText().contentEquals(c.getReplacementText()))
            {
                found = true;
                break;
            }
        }
        return found;
    }

    public static boolean isFunctionAlreadyDeclared(FunctionCompletion fc, List<Completion> completions)
    {
        if (completions.isEmpty())
            return false;
        boolean alreadyDeclared = false;
        for (int i = 0; i < completions.size(); ++i)
        {
            Completion c = completions.get(i);
            if (fc.getReplacementText() == c.getReplacementText())
            {
                if (c instanceof FunctionCompletion)
                {
                    FunctionCompletion fctmp = (FunctionCompletion) c;
                    if (fctmp.getParamCount() == fc.getParamCount())
                    {
                        for (int paramIdx = 0; paramIdx < fctmp.getParamCount(); ++paramIdx)
                        {
                            if (fctmp.getParam(paramIdx).getType() != fc.getParam(paramIdx).getType())
                                alreadyDeclared = true;
                        }
                    }
                }
            }
        }
        return alreadyDeclared;
    }

    public BasicCompletion duplicateBasicCompletion(BasicCompletion c, String res)
    {
        BasicCompletion co = (BasicCompletion) c;
        BasicCompletion c2 = new BasicCompletion(c.getProvider(), res);
        c2.setShortDescription(co.getShortDescription());
        c2.setSummary(co.getSummary());
        c2.setRelevance(co.getRelevance());
        return c2;
    }

    /**
     * {@inheritDoc}
     */
    protected List<Completion> getCompletionsImpl(JTextComponent comp)
    {

        // return completions;
        List<Completion> retVal = new ArrayList<Completion>();
        String text = getAlreadyEnteredTextWithFunc(comp);
        int lastIdx = text.lastIndexOf('.');

        ScriptEngineHandler engineHandler = ScriptEngineHandler.getLastEngineHandler();
        HashMap<String, Class<?>> engineVariables = ScriptEngineHandler.getLastEngineHandler().getEngineVariables();

        // Cannot work directly because returns null on the provider.
        HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>> engineTypesMethod = engineHandler.getEngineTypesMethod();
        HashMap<Integer, IcyFunctionBlock> localFunctions;
        if (handler != null)
            localFunctions = handler.getBlockFunctions();
        else
            localFunctions = new HashMap<Integer, IcyFunctionBlock>();

        if (text != null)
        {
            // test if inside parenthesis
            if (text.contains("("))
            {
                String text2 = String.copyValueOf(text.toCharArray());
                int idx;
                int i = 0;
                int pOpen = 0;
                int pClose = 0;
                while (i < text2.length() - 1 && (idx = text2.indexOf('(', i)) != -1)
                {
                    ++pOpen;
                    i += idx + 1;
                }
                i = 0;
                while (i < text2.length() - 1 && (idx = text2.indexOf(')', i)) != -1)
                {
                    ++pClose;
                    i += idx + 1;
                }
                int ppCount = pOpen - pClose;
                if (ppCount > 0)
                {
                    text = text2.substring(text2.lastIndexOf('(') + 1);
                    lastIdx = text.lastIndexOf('.');
                }
            }
            if (text.isEmpty() || text.startsWith("Math.") || lastIdx == -1)
            {
                doClassicCompletion(text, retVal);
            }
            else
            {
                if (handler != null)
                {
                    String command;
                    if (lastIdx != -1)
                    {
                        command = text.substring(0, lastIdx);
                        if (lastIdx <= text.length() - 1) // dot is before the
                            // last
                            text = text.substring(lastIdx + 1, text.length());
                    }
                    else
                        command = text;
                    ArrayList<ScriptFunctionCompletion> methods = null;

                    // is the command a classname ?
                    Class<?> clazz = handler.resolveClassDeclaration(command);
                    if (clazz != null)
                    {
                        // ----------------------------
                        // STATIC ACCESS
                        // ----------------------------
                        if ((methods = engineTypesMethod.get(clazz)) != null && !advanced)
                        {
                            for (ScriptFunctionCompletion complete : methods)
                            {
                                if (complete.isStatic()
                                        && (text.isEmpty() || complete.getName().toLowerCase()
                                                .startsWith(text.toLowerCase())))
                                    retVal.add(generateSFCCopy(complete, true));
                            }
                        }
                        else
                        {
                            populateWithClassTypes(clazz, text, retVal, true);
                        }
                    }

                    // check in the local variables if it is a variable
                    // if it is : propose depending on the variable type
                    if ((clazz = handler.getVariableDeclaration(command)) != null
                            || (clazz = engineVariables.get(command)) != null)
                    {
                        // ----------------------------
                        // VARIABLE ACCESS
                        // ----------------------------
                        methods = engineTypesMethod.get(clazz);
                        if (methods != null && !advanced)
                        {
                            for (ScriptFunctionCompletion complete : methods)
                            {
                                if (!complete.getName().toLowerCase().startsWith(text.toLowerCase()))
                                    continue;
                                if (complete.isStatic())
                                    complete.setRelevance(ScriptingHandler.RELEVANCE_LOW);
                                else if (!complete.isStatic())
                                    complete.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
                                if (text.isEmpty() || complete.getName().toLowerCase().startsWith(text.toLowerCase()))
                                    retVal.add(generateSFCCopy(complete));
                            }
                        }
                        else
                        {
                            populateWithClassTypes(clazz, text, retVal);
                        }
                    }
                    else
                    {
                        // ----------------------------
                        // FUNCTION ACCESS
                        // ----------------------------
                        // if not : look the type of the function (if declared).
                        int startOffset = getStartOffset(comp) - 1;
                        // System.out.println("offset:" + startOffset);
                        for (Integer i : localFunctions.keySet())
                            System.out.println(i);
                        IcyFunctionBlock fb = localFunctions.get(startOffset);
                        if (fb != null)
                        {
                            clazz = fb.getReturnType();
                            methods = engineTypesMethod.get(clazz);
                            if (methods != null && !advanced)
                            {
                                for (ScriptFunctionCompletion complete : methods)
                                {
                                    if (complete.isStatic())
                                        complete.setRelevance(ScriptingHandler.RELEVANCE_LOW);
                                    else if (!complete.isStatic())
                                        complete.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
                                    if (text.isEmpty()
                                            || complete.getName().toLowerCase().startsWith(text.toLowerCase()))
                                        retVal.add(generateSFCCopy(complete));
                                }
                            }
                            else
                            {
                                populateWithClassTypes(clazz, text, retVal);
                            }
                        }
                    }
                }
                else
                {
                    // doClassicCompletion(text, retVal);
                }
            }
        }
        return retVal;
    }

    private void populateWithClassTypes(Class<?> clazz, String text, List<Completion> retVal)
    {
        populateWithClassTypes(clazz, text, retVal, false);
    }

    private void populateWithClassTypes(Class<?> clazz, String text, List<Completion> retVal, boolean staticOnly)
    {
        ArrayList<Completion> listFields = new ArrayList<Completion>();
        ArrayList<Completion> listMethods = new ArrayList<Completion>();
        for (Field f : clazz.getFields())
        {
            String name = f.getName();
            if (!name.toLowerCase().startsWith(text.toLowerCase()))
                continue;
            int mod = f.getModifiers();
            if (Modifier.isPublic(mod))
            {
                if (!staticOnly)
                {
                    VariableCompletion vc = new VariableCompletion(this, name, getType(f.getType(), true));
                    if (Modifier.isStatic(mod))
                        vc.setRelevance(ScriptingHandler.RELEVANCE_LOW);
                    else
                        vc.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
                    listFields.add(vc);
                }
                else if (Modifier.isStatic(mod))
                {
                    VariableCompletion vc = new VariableCompletion(this, name, getType(f.getType(), true));
                    vc.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
                    listFields.add(vc);
                }
            }
        }
        for (Method m : clazz.getMethods())
        {
            if (!m.getName().toLowerCase().startsWith(text.toLowerCase()))
                continue;
            int mod = m.getModifiers();
            if (!staticOnly)
            {
                ScriptFunctionCompletion fc = new ScriptFunctionCompletion(this, m.getName(), m);
                if (Modifier.isStatic(mod))
                    fc.setRelevance(ScriptingHandler.RELEVANCE_LOW);
                else
                    fc.setRelevance(ScriptingHandler.RELEVANCE_HIGH);

                // TODO relevance assignment = type / expr = void
                fc.setDefinedIn(clazz.toString());
                ArrayList<Parameter> params = new ArrayList<Parameter>();
                int i = 0;
                for (Class<?> clazzParam : m.getParameterTypes())
                {
                    params.add(new Parameter(getType(clazzParam, true), "arg" + i));
                    ++i;
                }
                fc.setParams(params);
                listMethods.add(fc);
            }
            else if (Modifier.isStatic(mod))
            {
                ScriptFunctionCompletion fc = new ScriptFunctionCompletion(this, m.getName(), m);
                fc.setRelevance(ScriptingHandler.RELEVANCE_HIGH);

                // TODO relevance assignment = type / expr = void
                fc.setDefinedIn(clazz.toString());
                ArrayList<Parameter> params = new ArrayList<Parameter>();
                int i = 0;
                for (Class<?> clazzParam : m.getParameterTypes())
                {
                    params.add(new Parameter(getType(clazzParam, true), "arg" + i));
                    ++i;
                }
                fc.setParams(params);
                listMethods.add(fc);
            }
        }
        retVal.addAll(listFields);
        retVal.addAll(listMethods);
    }

    private Completion generateSFCCopy(ScriptFunctionCompletion complete, boolean b)
    {
        Method method = complete.getMethod();
        String shortDescript = complete.getShortDescription();

        ScriptFunctionCompletion sfcCopy = new ScriptFunctionCompletion(this, method.getName(), method);
        sfcCopy.setDefinedIn(complete.getDefinedIn());
        sfcCopy.setRelevance(complete.getRelevance());
        sfcCopy.setReturnValueDescription(method.getReturnType().toString());
        if (shortDescript != null)
            sfcCopy.setShortDescription(shortDescript);
        ArrayList<Parameter> params = new ArrayList<Parameter>();
        for (int i = 0; i < complete.getParamCount(); ++i)
            params.add(complete.getParam(i));
        sfcCopy.setParams(params);
        return sfcCopy;
    }

    private Completion generateSFCCopy(ScriptFunctionCompletion complete)
    {
        return generateSFCCopy(complete, false);
    }

    @SuppressWarnings("unchecked")
    protected void doClassicCompletion(String text, List<Completion> retVal)
    {
        // nothing worked, display normal
        int index = Collections.binarySearch(completions, text, comparator);
        if (index < 0)
        { // No exact match
            index = -index - 1;
        }
        else
        {
            // If there are several overloads for the function being
            // completed, Collections.binarySearch() will
            // return the index of one of those overloads, but we must
            // return all of them, so search backward until we find the
            // first one.
            int pos = index - 1;
            while (pos > 0 && comparator.compare(completions.get(pos), text) == 0)
            {
                retVal.add((Completion) completions.get(pos));
                pos--;
            }
        }

        while (index < completions.size())
        {
            Completion c = (Completion) completions.get(index);
            if (Util.startsWithIgnoreCase(c.getInputText(), text))
            {
                if (c instanceof ScriptFunctionCompletion)
                {
                    if (((ScriptFunctionCompletion) c).isStatic())
                        retVal.add(c);
                }
                else
                    retVal.add(c);
                index++;
            }
            else
            {
                break;
            }
        }

    }

    @Override
    public String getAlreadyEnteredText(JTextComponent comp)
    {
        // used only for insertion of text
        Document doc = comp.getDocument();

        int dot = comp.getCaretPosition();
        Element root = doc.getDefaultRootElement();
        int index = root.getElementIndex(dot);
        Element elem = root.getElement(index);
        int start = elem.getStartOffset();
        int len = dot - start;
        try
        {
            doc.getText(start, len, seg);
        }
        catch (BadLocationException ble)
        {
            ble.printStackTrace();
            return EMPTY_STRING;
        }

        int segEnd = seg.offset + len;
        start = segEnd - 1;
        while (start >= seg.offset && isValidChar(seg.array[start]))
        {
            start--;
        }
        start++;

        len = segEnd - start;
        return len == 0 ? EMPTY_STRING : new String(seg.array, start, len);
    }

    public String getAlreadyEnteredTextWithFunc(JTextComponent comp)
    {

        Document doc = comp.getDocument();

        int dot = comp.getCaretPosition();
        Element root = doc.getDefaultRootElement();
        int index = root.getElementIndex(dot);
        Element elem = root.getElement(index);
        int start = elem.getStartOffset();
        int len = dot - start;
        try
        {
            doc.getText(start, len, seg);
        }
        catch (BadLocationException ble)
        {
            ble.printStackTrace();
            return EMPTY_STRING;
        }

        int segEnd = seg.offset + len;
        start = segEnd - 1;
        while (start >= seg.offset && isValidCharStrict(seg.array[start]))
        {
            start--;
        }
        start++;

        len = segEnd - start;
        return len == 0 ? EMPTY_STRING : new String(seg.array, start, len);
    }

    public int getStartOffset(JTextComponent comp)
    {

        Document doc = comp.getDocument();

        int dot = comp.getCaretPosition();
        Element root = doc.getDefaultRootElement();
        int index = root.getElementIndex(dot);
        Element elem = root.getElement(index);
        int start = elem.getStartOffset();
        int len = dot - start;
        try
        {
            doc.getText(start, len, seg);
        }
        catch (BadLocationException ble)
        {
            ble.printStackTrace();
            return -1;
        }

        int segEnd = seg.offset + len;
        start = segEnd - 1;
        while (start >= seg.offset && isValidChar(seg.array[start]))
        {
            start--;
        }
        start++;

        return start;
    }

    /**
     * @param clazz
     *        : the class the get the type from
     * @return
     */
    public static String getType(Class<?> clazz, boolean simpleName)
    {
        if (simpleName)
        {
            if (clazz.isArray())
                return clazz.getCanonicalName();
            return ClassUtil.getSimpleClassName(clazz.getName());
        }
        else
        {
            return clazz.getName();
        }
    }

    public ArrayList<String> getClassNamesFromPackage(String packageName) throws IOException
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL packageURL;
        ArrayList<String> names = new ArrayList<String>();

        packageName = packageName.replace(".", "/");
        packageURL = classLoader.getResource(packageName);

        if (packageURL.getProtocol().equals("jar"))
        {
            String jarFileName;

            // build jar file name, then loop through zipped entries
            jarFileName = URLDecoder.decode(packageURL.getFile(), "UTF-8");
            jarFileName = jarFileName.substring(5, jarFileName.indexOf("!"));
            // System.out.println(">" + jarFileName);
            names.addAll(getNames(jarFileName, packageName));

            // loop through files in classpath
        }
        else
        {
            File folder = new File(packageURL.getFile());
            File[] contenuti = folder.listFiles();
            String entryName;
            for (File actual : contenuti)
            {
                entryName = actual.getName();
                entryName = entryName.substring(0, entryName.lastIndexOf('.'));
                names.add(entryName);
            }
        }
        return names;
    }

    private ArrayList<String> getNames(String jarFileName, String packageName) throws IOException
    {
        JarFile jf;
        Enumeration<JarEntry> jarEntries;
        String entryName;
        ArrayList<String> toReturn = new ArrayList<String>();

        jf = new JarFile(jarFileName);
        jarEntries = jf.entries();
        while (jarEntries.hasMoreElements())
        {
            entryName = jarEntries.nextElement().getName();
            if (entryName.startsWith(packageName) && entryName.length() > packageName.length() + 5)
            {
                Pattern p = Pattern.compile("((\\w|\\.|\\\\|/)+)(|\\$[a-zA-Z0-9]*[a-zA-Z]).class");
                Matcher m = p.matcher(entryName);
                if (m.matches())
                {
                    entryName = m.group(1) + m.group(3);
                    toReturn.add(entryName);
                }
            }
        }
        return toReturn;
    }

    public void installDefaultCompletions(String language)
    {
        InputStream in = getClass().getClassLoader().getResourceAsStream(
                "plugins/tprovoost/scripteditor/resources/lang/" + language.toLowerCase() + ".xml");
        try
        {
            if (in != null)
            {
                loadFromXML(in);
                in.close();
            }
            else
                System.out.println("File not found: " + "plugins/tprovoost/scripteditor/resources/lang/"
                        + language.toLowerCase() + ".xml");
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }
}

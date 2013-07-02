package plugins.tprovoost.scripteditor.scriptinghandlers.py;

import icy.util.ClassUtil;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptException;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.VariableCompletion;
import org.fife.ui.rtextarea.Gutter;
import org.mozilla.javascript.Context;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.Assign;
import org.python.antlr.ast.Attribute;
import org.python.antlr.ast.BinOp;
import org.python.antlr.ast.Call;
import org.python.antlr.ast.For;
import org.python.antlr.ast.Name;
import org.python.antlr.ast.Num;
import org.python.antlr.ast.Str;
import org.python.antlr.base.expr;
import org.python.antlr.base.mod;
import org.python.core.CompilerFlags;
import org.python.core.ParserFacade;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;

import plugins.tprovoost.scripteditor.scriptinghandlers.IcyFunctionBlock;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngine;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptEngineHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptVariable;
import plugins.tprovoost.scripteditor.scriptinghandlers.ScriptingHandler;
import plugins.tprovoost.scripteditor.scriptinghandlers.VariableType;

public class PythonScriptingHandler extends ScriptingHandler
{

	private static InteractiveInterpreter interpreter;
	private LinkedList<IcyFunctionBlock> functionBlocksToResolve = new LinkedList<IcyFunctionBlock>();
	private HashMap<String, String> aliases = new HashMap<String, String>();
	private String currentText;

	public PythonScriptingHandler(DefaultCompletionProvider provider, JTextComponent textArea, Gutter gutter, boolean autocompilation)
	{
		super(provider, "Python", textArea, gutter, autocompilation);
	}

	@Override
	public void evalEngine(ScriptEngine engine, String s) throws ScriptException
	{
		((PyScriptEngine) engine).evalFile(fileName);
	}

	public static void setInterpreter(InteractiveInterpreter interpreter)
	{
		PythonScriptingHandler.interpreter = interpreter;
	}

	public static PythonInterpreter getInterpreter()
	{
		return interpreter;
	}

	@Override
	public void installDefaultLanguageCompletions(String language) throws ScriptException
	{
		importPythonPackages(getEngine());

		// IMPORT PLUGINS FUNCTIONS
		importFunctions();

		HashMap<String, VariableType> engineFunctions = ScriptEngineHandler.getEngineHandler(getEngine()).getEngineFunctions();
		engineFunctions.put("range", new VariableType(Object[].class));
	}

	public void importPythonPackages(ScriptEngine engine) throws ScriptException
	{
	}

	@Override
	public void autoDownloadPlugins()
	{
	}

	@Override
	protected void detectVariables(String s, Context context) throws Exception
	{
		currentText = s;
		final CompilerFlags cflags = Py.getCompilerFlags(0, false);
		for (Completion c : variableCompletions)
			provider.removeCompletion(c);
		variableCompletions.clear();

		// register external variables prio to detection.
		// Otherwise, references to external variables will not
		// be detected.
		addExternalVariables();

		try
		{
			mod node = ParserFacade.parseExpressionOrModule(new StringReader(s), "<script>", cflags);
			if (DEBUG)
				dumpTree(node);
			registerVariables(node);
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		// add the completions
		provider.addCompletions(variableCompletions);
	}

	public void registerVariables(PythonTree node)
	{
		if (node == null)
			return;
		for (Completion c : generateCompletion(node))
		{
			if (c != null)
			{
				boolean alreadyExists = false;
				if (c instanceof VariableCompletion)
				{
					ScriptVariable vc = localVariables.get(((VariableCompletion) c).getName());
					if (vc != null && !vc.isInScope(textArea.getCaretPosition()))
						alreadyExists = true;
				}
				for (int i = 0; i < variableCompletions.size() && !alreadyExists; ++i)
				{
					if (variableCompletions.get(i).compareTo(c) == 0)
					{
						if (textArea.getCaret().getDot() > node.getCharStartIndex())
							variableCompletions.remove(i);
						else
							alreadyExists = true;
					}
				}
				if (!alreadyExists)
					variableCompletions.add(c);
			}
		}
		// recursive call on children (if any)
		if (node.getChildCount() > 0)
		{
			for (PythonTree tree : node.getChildren())
			{
				registerVariables(tree);
			}
		}
	}

	private ArrayList<Completion> generateCompletion(PythonTree node)
	{
		ArrayList<Completion> toReturn = new ArrayList<Completion>();

		if (node instanceof Assign)
		{
			// assign
			String name = node.getChild(0).getText();
			VariableType type = resolveType(node.getChild(1));
			VariableCompletion c = new VariableCompletion(provider, name, type == null || type.getClazz() == null ? "" : type.toString());
			c.setDefinedIn(fileName);
			c.setSummary("variable");
			c.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
			toReturn.add(c);
			addVariableDeclaration(c.getName(), type, node.getCharStartIndex());
		} else if (node instanceof Call)
		{
			resolveCallType(node, currentText, false);
		} else if (node instanceof For)
		{
			String name = ((Name) ((For) node).getTarget()).getInternalId();
			VariableType type = resolveType((PythonTree) ((For) node).getIter());
			String typeS = "";
			if (type != null)
			{
				Class<?> clazz = type.getClazz();
				if (clazz != null)
				{
					if (clazz.isArray())
					{
						type = new VariableType(clazz.getComponentType());
					}
					typeS = type.toString();
				}
			}
			addVariableDeclaration(name, null, node.getCharStartIndex());
			localVariables.get(name).getVariableScopes().get(0).setEndScopeOffset(node.getCharStopIndex());
			VariableCompletion c = new VariableCompletion(provider, name, typeS);
			c.setDefinedIn(fileName);
			c.setRelevance(ScriptingHandler.RELEVANCE_HIGH);
			toReturn.add(c);
		}
		return toReturn;
	}

	protected void addVariableDeclaration(String name, VariableType type, int offset)
	{
		ScriptVariable vc = localVariables.get(name);
		if (vc == null)
		{
			vc = new ScriptVariable();
			localVariables.put(name, vc);
		}
		vc.addType(offset, type);
	}

	private VariableType resolveType(PythonTree child)
	{
		if (child instanceof Call)
			return resolveCallType(child, currentText, false);
		else if (child instanceof Num)
			return new VariableType(Number.class);

		else if (child instanceof Str)
			return new VariableType(String.class);
		else if (child instanceof BinOp)
		{
			PyObject left = ((BinOp) child).getLeft();
			return resolveType((PythonTree) left);
		} else if (child instanceof Name)
		{
			return getVariableDeclaration(child.getText(), child.getCharStartIndex());
		}
		if (DEBUG)
			System.out.println("TODO handle: " + child.getClass());
		return null;
	}

	private VariableType resolveCallType(PythonTree child, String text, boolean noerror)
	{
		VariableType toReturn = null;
		ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(getEngine());
		int offset = child.getCharStartIndex();
		String s = buildFunction(child);
		if (DEBUG)
			System.out.println("Built function : " + s);
		// create a regex pattern
		Pattern p = Pattern.compile("\\w(\\w|\\.|\\[|\\])*\\((\\w|\\.|\\[|,|\\]|\\(|\\)| )*\\)");
		Matcher match = p.matcher(s);

		int idxP1 = 0;
		int idxP2;
		int decal = 0;
		boolean isField = false;
		if (match.find(0))
		{
			// TODO handle spot.points
			String firstCall = match.group(0);
			idxP1 = firstCall.indexOf('(');
			idxP2 = firstCall.indexOf(')');
			decal += idxP2 + 1;
			int lastDot = firstCall.substring(0, idxP1).lastIndexOf('.');
			VariableType vt = null;

			// get the className (or binding function name if it is the
			// case)
			String classNameOrFunctionNameOrVariable;
			if (lastDot != -1)
				classNameOrFunctionNameOrVariable = firstCall.substring(0, lastDot);
			else if (idxP1 != -1)
				classNameOrFunctionNameOrVariable = firstCall.substring(0, idxP1);
			else
				classNameOrFunctionNameOrVariable = firstCall.substring(0);

			if (classNameOrFunctionNameOrVariable.contains("."))
			{
				// is it a class?
				Class<?> clazz = resolveClassDeclaration(classNameOrFunctionNameOrVariable);
				if (clazz != null)
					vt = new VariableType(clazz);
				else
				{
					String res[] = classNameOrFunctionNameOrVariable.split("\\.");
					classNameOrFunctionNameOrVariable = res[0];
					isField = true;
					for (int i = 1; i < res.length; ++i)
					{
						lastDot = classNameOrFunctionNameOrVariable.length() + 1;
						decal = classNameOrFunctionNameOrVariable.length() + 1;
					}
				}
			}

			if (vt == null)
			{
				// --------------------------
				// TEST IF IS FUNCTION BINDED
				// --------------------------
				vt = localFunctions.get(classNameOrFunctionNameOrVariable);
			}
			if (vt == null)
			{
				vt = ScriptEngineHandler.getEngineHandler(getEngine()).getEngineFunctions().get(classNameOrFunctionNameOrVariable);
				if (classNameOrFunctionNameOrVariable.contentEquals("println") || classNameOrFunctionNameOrVariable.contentEquals("print"))
					vt = new VariableType(void.class);
			}

			// -------------------------------------------
			// IT IS SOMETHING ELSE, PERFORM VARIOUS TESTS
			// -------------------------------------------

			// is it a script defined variable?
			if (vt == null)
				vt = getVariableDeclaration(classNameOrFunctionNameOrVariable, offset);

			// is it an engine variable?
			if (vt == null)
				vt = engineHandler.getEngineVariables().get(classNameOrFunctionNameOrVariable);

			// is it a class?
			if (vt == null)
			{
				Class<?> clazz = resolveClassDeclaration(classNameOrFunctionNameOrVariable);
				if (clazz != null)
					vt = new VariableType(clazz);
			}

			// unknown type
			if (vt == null)
			{
				System.out.println("Error while parsing code: unknown: " + classNameOrFunctionNameOrVariable + " at line: " + child.getLine());
				return null;
			}

			// the first type!
			Class<?> returnType = vt.getClazz();

			if (returnType == null)
				return null;

			String call;
			if (decal < idxP1)
				call = firstCall.substring(lastDot + 1);
			else
				call = firstCall.substring(lastDot + 1, idxP1);

			// FIND THE CORRESPONDING METHOD
			String genericType = vt.getType();
			if (lastDot != -1)
			{
				if (!isField)
				{
					try
					{
						// generate the Class<?> arguments
						Class<?> clazzes[];

						// get the arguments
						String argsString = firstCall.substring(idxP1 + 1, idxP2);

						// separate arguments
						String[] args = argsString.split(",");

						if (argsString.isEmpty())
						{
							clazzes = new Class<?>[0];
						} else
						{
							clazzes = new Class<?>[args.length];
							for (int i = 0; i < clazzes.length; ++i)
								clazzes[i] = resolveClassDeclaration(args[i]);
							clazzes = getGenericNumberTypes(text, child, vt.getClazz(), firstCall.substring(lastDot + 1, idxP1), clazzes);
						}
						Method m = resolveMethod(returnType, call, clazzes);

						String genericReturnType = m.getGenericReturnType().toString();
						if (Pattern.matches("(\\[*)E", genericReturnType) && !genericType.isEmpty())
						{
							try
							{
								// TOOD array
								returnType = ClassUtil.findClass(genericType);
								genericType = "";
							} catch (ClassNotFoundException e)
							{
							}
						} else
						{
							// set the new return type.
							returnType = m.getReturnType();
							if (returnType.getTypeParameters().length > 0)
							{
								genericType = VariableType.getType(m.getGenericReturnType().toString());
							}
						}
					} catch (SecurityException e1)
					{
					} catch (NoSuchMethodException e1)
					{
						try
						{
							Field f = returnType.getField(call);
							returnType = f.getType();
						} catch (SecurityException e)
						{
						} catch (NoSuchFieldException e)
						{
							return null;
						}
					}
				} else
				{
					// TODO
					// System.out.println("not a method");
					Field f;
					try
					{
						String next = firstCall.substring(decal);
						next = next.substring(0, next.indexOf('.'));
						decal += next.length();
						f = returnType.getField(next);
						returnType = f.getType();
					} catch (SecurityException e)
					{
					} catch (NoSuchFieldException e)
					{
					}
				}

			}

			// Create the VariableType containing the result.
			toReturn = new VariableType(returnType, genericType);

			// Pop the function Block and set its type.
			if (functionBlocksToResolve.isEmpty())
			{
				System.out.println("No function to resolve.");
				return null;
			}
			IcyFunctionBlock fb = functionBlocksToResolve.pop();
			fb.setReturnType(toReturn);

			if (DEBUG)
				System.out.println("function edited: (" + (child.getCharStartIndex()) + ") " + text.substring(offset));

			// Add the function block to the index of blockFunctions.
			blockFunctions.put(fb.getStartOffset(), fb);

			// iterate over the next functions, based on the returnType
			while (match.find(decal) && !(firstCall = match.group()).isEmpty())
			{
				if (returnType == void.class)
				{
					System.out.println("Void return, impossible to call something else on it. at line:" + child.getLine());
				}
				idxP1 = firstCall.indexOf('(');
				idxP2 = firstCall.indexOf(')');
				decal += idxP2 + 2; // account for ) and .
				String argsString = firstCall.substring(idxP1 + 1, idxP2);
				String[] args = argsString.split(",");
				Class<?>[] clazzes;
				if (argsString.isEmpty())
				{
					clazzes = new Class<?>[0];
				} else
				{
					clazzes = new Class<?>[args.length];
					for (int i = 0; i < clazzes.length; ++i)
						clazzes[i] = resolveClassDeclaration(args[i]);
					lastDot = firstCall.substring(0, idxP1).lastIndexOf('.');
					if (lastDot < 0)
					{
						lastDot = -1; // in case of new for instance.
					}
					clazzes = getGenericNumberTypes(text, child, returnType, firstCall.substring(lastDot + 1, idxP1), clazzes);
				}
				String call2;
				// if (lastDot != -1)
				// call2 = firstCall.substring(lastDot + 1, idxP1);
				// else
				call2 = firstCall.substring(0, idxP1);
				if (call2.contentEquals("newInstance"))
				{
					try
					{
						returnType.getConstructor(clazzes);
					} catch (SecurityException e)
					{
					} catch (NoSuchMethodException e)
					{
					}
				} else
				{
					Method m;
					try
					{
						m = resolveMethod(returnType, firstCall.substring(0, idxP1), clazzes);
						// Check if the return type is E or [E or [[E, etc. That
						// means that 'E'
						// corresponds
						// to the previous generic Type.
						String genericReturnType = m.getGenericReturnType().toString();
						if (Pattern.matches("(\\[*)E", genericReturnType) && !genericType.isEmpty())
						{
							try
							{
								// TOOD array
								returnType = ClassUtil.findClass(genericType);
								genericType = "";
							} catch (ClassNotFoundException e)
							{
							}
						} else
						{
							// set the new return type.
							returnType = m.getReturnType();
							if (returnType.getTypeParameters().length > 0)
							{
								genericType = VariableType.getType(m.getGenericReturnType().toString());
							}
						}
					} catch (SecurityException e1)
					{
					} catch (NoSuchMethodException e1)
					{
						try
						{
							Field f = returnType.getField(call);
							returnType = f.getType();
						} catch (SecurityException e)
						{
						} catch (NoSuchFieldException e)
						{
							return null;
						}
					}

				}
				if (functionBlocksToResolve.isEmpty())
					return null;
				// get the last function block to resolve and set its type
				fb = functionBlocksToResolve.pop();
				toReturn = new VariableType(returnType, genericType);
				fb.setReturnType(toReturn);

				if (DEBUG)
					System.out.println("function edited: (" + (fb.getStartOffset() + child.getCharStartIndex() - fb.getStartOffset()) + ") "
							+ text.substring(offset));

				// add the Function Block to the index of blockFunctions.
				blockFunctions.put(fb.getStartOffset(), fb);
			}
			return toReturn;
		}
		return null;
	}

	private String buildFunction(PythonTree child)
	{
		String callName = "";

		callName = buildFunctionRecursive(callName, child);
		if (!callName.isEmpty())
		{
			// removes the last dot
			if (callName.startsWith("."))
				callName = callName.substring(1);
		}
		if (callName.endsWith("."))
			callName = callName.substring(0, callName.length() - 1);
		return callName;
	}

	private String buildFunctionRecursive(String callName, PythonTree n)
	{
		if (n != null)
		{
			// int type = n.getAntlrType();
			if (n instanceof Call)
			{
				Call fn = ((Call) n);
				String args = "";
				args += "(";
				int i = 0;
				for (expr exp : fn.getInternalArgs())
				{
					if (i != 0)
						args += ",";
					VariableType typeC = resolveType(exp);
					if (typeC != null && typeC.getClazz() != null)
						args += typeC.getClazz().getName();
					else
						args += "unknown";
					i++;
				}
				args += ")";
				String functionName = "";
				PyObject target = fn.getFunc();
				String toReturn = "";
				if (target instanceof Attribute)
				{
					Attribute att = ((Attribute) target);
					functionName = att.getAttr().toString();
					toReturn = functionName + args + "." + callName;
					toReturn = buildFunctionRecursive(toReturn, (PythonTree) (att.getValue()));
				} else if (target instanceof Name)
				{
					functionName = ((Name) target).getInternalId();
					toReturn = functionName + args + "." + callName;
				}
				// if (targetType == Token.NAME)
				// {
				// functionName = target.getString();
				// toReturn = functionName + args;
				// }
				// else if (targetType == Token.GETPROP)
				// {
				// functionName = ((PropertyGet) target).getRight().getString();
				// toReturn = buildFunctionRecursive(elem, ((PropertyGet)
				// target).getLeft()) + "." +
				// functionName
				// + args;
				// }
				// else if (targetType == Token.GETELEM)
				// {
				// ElementGet get = (ElementGet) target;
				// elem = buildFunctionRecursive("", get.getElement());
				// functionName = elem.substring(0, elem.indexOf('('));
				// String targetName = buildFunctionRecursive("",
				// get.getTarget());
				// toReturn = targetName + "." + elem;
				// }
				// else
				// toReturn = elem;
				int rp = fn.getCharStopIndex();
				if (DEBUG)
					System.out.println("function found:" + functionName);
				IcyFunctionBlock fb = new IcyFunctionBlock(functionName, rp, null);
				functionBlocksToResolve.add(fb);
				return toReturn;
			} else if (n instanceof Name)
			{
				callName = ((Name) n).getInternalId() + "." + callName;
			}

		}
		return callName;
	}

	public void dumpTree(mod node)
	{
		for (PythonTree tree : node.getChildren())
		{
			System.out.println(tree.getType());
		}
	}

	@Override
	public void registerImports()
	{
		scriptDeclaredImportClasses.clear();
		String s = textArea.getText();
		// Regex 1 : (from\s+((\w|\.|_)+)\s+)?import\s+((\w|\s|,|_|$)+)\n
		// Regex 2 : ((\w|_|$)+)\s*(as\s+((\w|_|$)+))?
		// group 2 : Package (from)
		// group 4 : Class (import) : use regex 2
		Pattern patternImport = Pattern.compile("(from\\s+((\\w|\\.|_)+)\\s+)?import\\s+((\\w|\\s|,|_|$)+)\\n");
		Matcher matchImport = patternImport.matcher(s);
		int offset = 0;
		while (matchImport.find(offset))
		{
			String foundString = matchImport.group(0);
			String foundPackage = matchImport.group(2);
			String foundClasses = matchImport.group(4);
			if (foundClasses.endsWith("\n"))
				foundClasses = foundClasses.substring(0, foundClasses.length() - 1);
			Pattern patternClasses = Pattern.compile("((\\w|_|$)+)(\\s*as\\s+((\\w|_|$)+))?");
			Matcher matchClasses = patternClasses.matcher(foundClasses);
			int offsetClasses = 0;
			while (matchClasses.find(offsetClasses))
			{
				String foundStringClass = matchClasses.group(0);
				if (foundStringClass.contentEquals(""))
					break;
				String foundClass = matchClasses.group(1);
				String foundAlias = matchClasses.group(4);
				String className = foundPackage + "." + foundClass;

				if (foundClass.contentEquals("*"))
				{
					scriptDeclaredImports.add(foundPackage);
				} else
				{
					if (foundAlias != null)
					{
						aliases.put(foundAlias, className);
					}
					scriptDeclaredImportClasses.add(className);
				}

				int idxString = foundClasses.indexOf(foundStringClass, offset);
				if (idxString == -1)
					break;
				offsetClasses = idxString + foundStringClass.length();
			}

			int idxString = s.indexOf(foundString, offset);
			if (idxString == -1)
				break;
			offset = idxString + foundString.length();
		}
	}

	@Override
	public void organizeImports(JTextComponent textArea2)
	{
	}

	@Override
	public void format()
	{
		// TODO
	}

	@Override
	public void installMethods(ScriptEngine engine, ArrayList<Method> functions)
	{
	}

	@Override
	public ScriptEngine getEngine()
	{
		return ScriptEngineHandler.getEngine("python");
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
	private static Class<?>[] getGenericNumberTypes(String text, PythonTree child, Class<?> clazz, String function, Class<?>[] argsClazzes)
	{
		Class<?>[] toReturn = new Class<?>[argsClazzes.length];
		String fullCommand = text.substring(child.getCharStartIndex(), child.getCharStopIndex());
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
					if (params.length == argsClazzes.length)
					{
						for (int i = 0; i < params.length; ++i)
						{
							if (params[i] == null || argsClazzes[i] == null)
								break;
							if (params[i].isAssignableFrom(argsClazzes[i]))
								toReturn[i] = params[i];
							else if (params[i].isPrimitive())
							{
								if (!(params[i] == float.class || params[i] == double.class) && !args[i].contains("."))
									toReturn[i] = params[i];
								else if (params[i] == float.class || params[i] == double.class)
									toReturn[i] = params[i];
								else
									break;
							} else
								break;
						}
					}
				}
			}
			return toReturn;
		} else
			return toReturn;
	}
}

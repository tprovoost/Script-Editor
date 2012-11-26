package plugins.tprovoost.scriptenginehandler;

import java.util.ArrayList;
import java.util.HashMap;

import javax.script.ScriptEngine;

import icy.plugin.abstract_.Plugin;

public class ScriptEngineHandler extends Plugin {

    private static HashMap<ScriptEngine, ScriptEngineHandler> engineHandlers = new HashMap<ScriptEngine, ScriptEngineHandler>();

    private static ScriptEngineHandler lastEngineHandler = null;

    private HashMap<String, Class<?>> engineVariables = new HashMap<String, Class<?>>();
    private HashMap<String, Class<?>> engineFunctions = new HashMap<String, Class<?>>();
    private ArrayList<String> engineDeclaredImports = new ArrayList<String>();
    private ArrayList<String> engineDeclaredImportClasses = new ArrayList<String>();
    private HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>> engineTypesMethod = new HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>>();;

    private ScriptEngineHandler() {
    }

    public static ScriptEngineHandler getLastEngineHandler() {
	return lastEngineHandler;
    }

    public static ScriptEngineHandler getEngineHandler(ScriptEngine engine) {
	ScriptEngineHandler engineHandler = engineHandlers.get(engine);
	if (engineHandler == null) {
	    engineHandler = new ScriptEngineHandler();
	    engineHandlers.put(engine, engineHandler);
	}
	lastEngineHandler = engineHandler;
	return engineHandler;
    }

    public ArrayList<String> getEngineDeclaredImportClasses() {
	return engineDeclaredImportClasses;
    }

    public ArrayList<String> getEngineDeclaredImports() {
	return engineDeclaredImports;
    }

    public HashMap<String, Class<?>> getEngineFunctions() {
	return engineFunctions;
    }

    public HashMap<String, Class<?>> getEngineVariables() {
	return engineVariables;
    }

    public HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>> getEngineTypesMethod() {
	return engineTypesMethod;
    }
}

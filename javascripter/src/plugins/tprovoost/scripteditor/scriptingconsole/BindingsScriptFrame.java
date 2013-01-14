package plugins.tprovoost.scripteditor.scriptingconsole;

import icy.gui.frame.IcyFrame;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

import plugins.tprovoost.scriptenginehandler.ScriptEngineHandler;

import sun.org.mozilla.javascript.internal.IdScriptableObject;
import sun.org.mozilla.javascript.internal.NativeArray;

public class BindingsScriptFrame extends IcyFrame {

    private ScriptEngine engine;
    private JButton btnFreeVar;
    private JTable listVariables;
    private BindingsTableModel model;
    private static BindingsScriptFrame singleton = new BindingsScriptFrame();

    protected BindingsScriptFrame() {
	super("Engine Bindings", true, true, true);

	JPanel mainPanel = new JPanel();

	btnFreeVar = new JButton("Free Var");
	btnFreeVar.addActionListener(new ActionListener() {

	    @Override
	    public void actionPerformed(ActionEvent arg0) {
		Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		Object o = listVariables.getValueAt(listVariables.getSelectedRow(), 0);
		Object val = listVariables.getValueAt(listVariables.getSelectedRow(), 1);
		try {
		    if (val instanceof NativeArray) {
			for (int i = 0; i < ((NativeArray) val).getLength(); ++i) {
			    ((NativeArray) val).delete(i);
			}
		    } else if (val instanceof IdScriptableObject) {
			// IdScriptableObject val2 = (IdScriptableObject) val;
			// val2.delete(0);
		    }
		    engine.eval(o + " = null");
		} catch (ScriptException e) {
		    e.printStackTrace();
		}
		bindings.remove(o);
	    }
	});
	JButton btnRefresh = new JButton("Refresh");
	btnRefresh.addActionListener(new ActionListener() {

	    @Override
	    public void actionPerformed(ActionEvent e) {
		update();
	    }
	});

	model = new BindingsTableModel();
	listVariables = new JTable(model);

	mainPanel.setLayout(new BorderLayout());
	JScrollPane scroll = new JScrollPane(listVariables);
	scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
	scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
	mainPanel.add(scroll, BorderLayout.CENTER);
	JPanel panelSouth = new JPanel();
	panelSouth.setLayout(new BoxLayout(panelSouth, BoxLayout.X_AXIS));
	panelSouth.add(Box.createHorizontalGlue());
	panelSouth.add(btnFreeVar);
	panelSouth.add(Box.createHorizontalGlue());
	panelSouth.add(btnRefresh);
	panelSouth.add(Box.createHorizontalGlue());
	mainPanel.add(panelSouth, BorderLayout.SOUTH);

	setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
	setContentPane(mainPanel);
	addToMainDesktopPane();
	setSize(200, 700);
    }

    public void setEngine(ScriptEngine engine) {
	this.engine = engine;
    }

    public void update() {
	if (engine != null) {
	    String languageName = engine.getFactory().getLanguageName();
	    ScriptEngine engine = ScriptEngineHandler.getEngine(languageName);
	    if (engine != this.engine)
		this.engine = engine;
	    // TODO update engine
	    model.fireTableDataChanged();
	    listVariables.repaint();
	}
    }

    private class BindingsTableModel extends AbstractTableModel {

	/**
		 * 
		 */
	private static final long serialVersionUID = 1L;

	@Override
	public String getColumnName(int column) {
	    if (column == 0) {
		return "Binding";
	    } else if (column == 1) {
		return "Value";
	    } else {
		return "";
	    }
	}

	@Override
	public int getColumnCount() {
	    return 2;
	}

	@Override
	public int getRowCount() {
	    if (engine != null)
		return engine.getBindings(ScriptContext.ENGINE_SCOPE).values().size();
	    return 0;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
	    if (engine == null)
		return null;
	    if (columnIndex == 0) {
		return engine.getBindings(ScriptContext.ENGINE_SCOPE).keySet().toArray()[rowIndex];
	    } else {
		Object o = engine.getBindings(ScriptContext.ENGINE_SCOPE).values().toArray()[rowIndex];
		// Class<?> clazz = o.getClass();
		return o;
	    }
	}
    }

    public static BindingsScriptFrame getInstance() {
	return singleton;
    }
}

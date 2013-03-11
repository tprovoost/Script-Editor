package plugins.tprovoost.scripteditor.search;

import icy.file.xml.XMLPersistent;
import icy.plugin.PluginLoader;
import icy.util.XMLUtil;

import javax.swing.ImageIcon;

import org.w3c.dom.Node;

import plugins.tprovoost.scripteditor.main.ScriptEditorPlugin;

public class ScriptDescriptor implements XMLPersistent
{
    private String name;
    private String description;
    private String author;
    private String url;

    public ScriptDescriptor(Node node)
    {
        loadFromXML(node);
    }

    public String getName()
    {
        return name;
    }

    public String getDescription()
    {
        return description;
    }

    public String getAuthor()
    {
        return author;
    }

    public String getUrl()
    {
        return url;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    @Override
    public boolean loadFromXML(Node node)
    {
        this.name = XMLUtil.getValue(XMLUtil.getElement(node, "name"), "No name");
        this.author = XMLUtil.getValue(XMLUtil.getElement(node, "author"), "No author");
        this.url = XMLUtil.getValue(XMLUtil.getElement(node, "url"), "http://icy.bioimageanalysis.org");
        this.description = XMLUtil.getValue(XMLUtil.getElement(node, "shortDescription"), "No description");
        return true;
    }

    @Override
    public boolean saveToXML(Node node)
    {
        return false;
    }

    public ImageIcon getIcon()
    {
        return PluginLoader.getPlugin(ScriptEditorPlugin.class.getName()).getIcon();
    }
}

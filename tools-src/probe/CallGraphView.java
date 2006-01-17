package probe;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import net.sourceforge.gxl.*;
import probe.*;

public class CallGraphView extends Jui {
    public static void usage() {
        System.out.println( "Usage: java probe.CallGraphView [options] supergraph.gxl [subgraph.gxl]" );
        System.out.println( "  -port p: listen on port p (default: 8088)" );
        System.exit(1);
    }
    public static final void main( String[] args ) {
        new CallGraphView().run(args);
    }
    private Collection subgraphReachables;
    private CallGraph supergraph;
    private String supergraphName;
    private String subgraphName;
    public void run(String[] args) {
        List newArgs = new ArrayList();
        GXLDocument gxlDocument = null;
        System.out.println( "reading graph" );
        String filename = null;
        for( int i = 0; i < args.length; i++ ) {
            if( args[i].equals( "-port" ) ) {
                newArgs.add(args[i]);
                i++;
                newArgs.add(args[i]);
            } else {
                if( supergraph == null ) {
                    supergraph = readCallGraph(args[i]);
                    supergraphName = args[i];
                } else if( subgraphReachables == null ) {
                    subgraphReachables = readCallGraph(args[i]).findReachables();
                    subgraphName = args[i];
                } else usage();
            }
        }
        if( supergraph == null ) usage();
        for( Iterator eIt = supergraph.edges().iterator(); eIt.hasNext(); ) {
            final Edge e = (Edge) eIt.next();
            addMethod( e.src() );
            addMethod( e.dst() );
        }
        for( Iterator mIt = supergraph.entryPoints().iterator(); mIt.hasNext(); ) {
            final ProbeMethod m = (ProbeMethod) mIt.next();
            addMethod( m );
        }
        System.out.println( "starting server" );
        super.run((String[])newArgs.toArray(new String[0]));
    }
    public String process( Map args ) {
        if( args.containsKey("node") ) {
            return nodePage((String) args.get("node"));
        } else if( args.containsKey("search") ) {
            return search((String) args.get("search"));
        } else {
            return search();
        }
    }
    public String nodePage( String nodeid ) {
        if( nodeid.startsWith("c") ) return nodePage((ProbeClass)idClassMap.get(nodeid));
        else if( nodeid.startsWith("m") ) return nodePage((ProbeMethod)idMethodMap.get(nodeid));
        else throw new RuntimeException("bad node id");
    }
    public String nodePage( ProbeClass cls ) {
        StringBuffer sb = new StringBuffer();
        String nodeid = (String) classIdMap.get(cls);
        sb.append( searchForm() );
        sb.append( node(nodeid) );
        for( Iterator mIt = methodIdMap.keySet().iterator(); mIt.hasNext(); ) {
            final ProbeMethod m = (ProbeMethod) mIt.next();
            if( m.cls().equals(cls) ) sb.append(node(m));
        }
        return html( sb.toString() );
    }
    public String nodePage( ProbeMethod m ) {
        StringBuffer sb = new StringBuffer();
        String nodeid = (String) methodIdMap.get(m);
        sb.append( searchForm() );
        sb.append( "<center>" );
        sb.append( node(nodeid) );
        sb.append( "</center>" );
        sb.append( "<table width=\"100%\">" );
        sb.append( "<tr><td>" );
        sb.append( "<center>Incoming edges:</center>" );
        sb.append( "</td><td>" );
        sb.append( "<center>Outgoing edges:</center>" );
        sb.append( "</td></tr><tr><td width=\"50%\" valign=\"top\">" );
        if( supergraph.entryPoints().contains(m) ) {
            sb.append( "<table><tr>" );
            sb.append( "<td bgcolor=\"lightgreen\">ROOT" );
            sb.append("</td></tr></table>");
        }
        for( Iterator eIt = supergraph.edges().iterator(); eIt.hasNext(); ) {
            final Edge e = (Edge) eIt.next();
            if( e.dst().equals(m) ) sb.append(node(e.src()));
        }
        sb.append( "</td><td width=\"50%\" valign=\"top\">" );
        for( Iterator eIt = supergraph.edges().iterator(); eIt.hasNext(); ) {
            final Edge e = (Edge) eIt.next();
            if( e.src().equals(m) ) sb.append(node(e.dst()));
        }
        sb.append( "<td><tr>" );
        sb.append( "</table>\n" );
        return html( sb.toString() );
    }
    public String node( String nodeid ) {
        if( nodeid.startsWith("c") ) return node((ProbeClass)idClassMap.get(nodeid));
        else if( nodeid.startsWith("m") ) return node((ProbeMethod)idMethodMap.get(nodeid));
        else throw new RuntimeException("bad node id");
    }
    public String node( ProbeMethod m ) {
        StringBuffer sb = new StringBuffer();
        String nodeid = (String)methodIdMap.get(m);
        sb.append( "<table><tr>" );
        if( subgraphReachables != null && subgraphReachables.contains(m) ) {
            sb.append( "<td bgcolor=\"pink\">" );
        } else {
            sb.append( "<td bgcolor=\"lightblue\">" );
        }
        sb.append(node(m.cls()));
        sb.append(link("node", nodeid));
        sb.append(escape(m.name()));
        sb.append("(");
        sb.append(escape(m.signature()));
        sb.append(")");
        sb.append("</a></td></tr></table>");
        return sb.toString();
    }
    public String node( ProbeClass cls ) {
        StringBuffer sb = new StringBuffer();
        String nodeid = (String)classIdMap.get(cls);
        sb.append( "<table><tr><td>" );
        sb.append("<b>");
        sb.append(link("node", nodeid));
        sb.append(escape(cls.pkg()));
        sb.append(".");
        sb.append(escape(cls.name()));
        sb.append("</a>" );
        sb.append("</b>");
        sb.append("</td></tr></table>");
        return sb.toString();
    }
    public String search( String key ) {
        StringBuffer sb = new StringBuffer();
        sb.append( searchForm() );
        sb.append( "Search results for "+key );
        for( Iterator clsIt = classIdMap.keySet().iterator(); clsIt.hasNext(); ) {
            final ProbeClass cls = (ProbeClass) clsIt.next();
            if( matches( key, cls.pkg() ) 
            || matches( key, cls.name() ) )
                sb.append(node(cls));
        }
        for( Iterator mIt = methodIdMap.keySet().iterator(); mIt.hasNext(); ) {
            final ProbeMethod m = (ProbeMethod) mIt.next();
            if( matches( key, m.name() ) )
                sb.append(node(m));
        }
        return html( sb.toString() );
    }
    public boolean matches( String needle, String haystack ) {
        return haystack.indexOf(needle) >= 0;
    }
    public String search() {
        return html(searchForm());
    }
    public String searchForm() {
        return ""
            +"<form>"
            +"<table><tr><td>"
            +"Search for: "
            +"<input type=\"text\" name=\"search\"></td>"
            +"<td bgcolor=\"pink\">"+supergraphName+" /\\ "+subgraphName+"</td>"
            +"<td bgcolor=\"lightblue\">"+supergraphName+" - "+subgraphName+"</td>"
            +"</tr></table>"
            +"</form>"
            +"<hr>";
    }
    static int nextId = 1;
    public void addMethod(ProbeMethod m) {
        if( methodIdMap.containsKey(m) ) return;
        addClass(m.cls());
        String id = "m"+nextId++;
        methodIdMap.put(m, id);
        idMethodMap.put(id, m);
    }
    public void addClass(ProbeClass cls) {
        if( classIdMap.containsKey(cls) ) return;
        String id = "c"+nextId++;
        classIdMap.put(cls, id);
        idClassMap.put(id, cls);
    }
    private Map classIdMap = new HashMap();
    private Map idClassMap = new HashMap();
    private Map methodIdMap = new HashMap();
    private Map idMethodMap = new HashMap();
    private static CallGraph readCallGraph(String filename) {
        CallGraph ret;
        try {
            try {
                ret = new GXLReader().readCallGraph(new FileInputStream(filename));
            } catch( RuntimeException e ) {
                ret = new GXLReader().readCallGraph(new GZIPInputStream(new FileInputStream(filename)));
            }
        } catch( IOException e ) {
            throw new RuntimeException( "caught IOException "+e );
        }
        return ret;
    }

}

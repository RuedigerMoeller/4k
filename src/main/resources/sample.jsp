<%
import java.util.*;
import java.io.*;
import de.ruedigermoeller.template.*;

// add imports you need during generation =>
import de.ruedigermoeller.templatesample.*;

// this header is always required to make it work. Cut & Paste this as template
public class CLAZZNAME implements IContextReceiver
{
    public void receiveContext(Object o, PrintStream out) throws Exception
    {
       TemplateSample.TemplateContext CTX = (TemplateSample.TemplateContext)o; // cast to your expected context class
%>

<%
    // stuff denoted here is just executed at generation time
    int xy = 13;
%>

// imports of output go here
import java.util.*;

public class <%+CTX.clazzName%> {

    public <%+CTX.clazzName%>() {
        System.out.println(\"value of xy at generation time \"+<%+xy%>); // note the quoting !
    }

<% // example for a loop
   for(int i=0; i < CTX.getters.length; i++ ) { %>
    String <%+CTX.getters[i]%>;

    public get<%+CTX.getters[i]%>( String val ) {
        return <%+CTX.getters[i]%>;
    }
<%} /* for */ %>
}

<%
    // this footer is always required (to match opening braces in header
    }
}
%>
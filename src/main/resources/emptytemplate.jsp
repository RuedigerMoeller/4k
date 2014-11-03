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
            // asign your context
            MyClass CTX = (MyClass)o;
%>
// start of output file
import java.util.*;

<%+ CTX.clazzName %> // insert string result of expression


<%
    // normal java code (loops etc.)
%>

// end of output file
<%
    // this footer is always required (to match opening braces in header
        }
    }
%>
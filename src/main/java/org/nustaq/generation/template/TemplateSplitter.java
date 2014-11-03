
/******************************************************
 * File: TemplateSplitter.java
 * created 09-May-00 3:23:35 PM by moelrue
 */

package org.nustaq.generation.template;


import java.io.*;


/**
 */
public class TemplateSplitter {
    InputStream in;
    PrintStream out;
    String clazzName;

    /**
     * this method replaces all occurences of 'key' in the String 's' by the String 'value'.
     * Lookup starts at the character at index 'start'.
     *
     * @return a String with the replacements made
     */
    String replace(final String s, final String key, final String value, final int start) {
        final int i = s.indexOf(key, start);
        if (i < 0)
            return s;
        final StringBuffer res = new StringBuffer(s.length());
        res.append(s.substring(0, i));
        res.append(value);
        res.append(s.substring(i + key.length()));
        return replace(res.toString(), key, value, i + value.length());
    }

    /**
     * Constructs an empty TemplateSplitter
     */
    public TemplateSplitter() {
    }

    /**
     * Constructs a TemplateSplitter with the given input and output streams
     */
    public TemplateSplitter(final InputStream in, final PrintStream out) {
        this.in = in;
        this.out = out;
    }

    /**
     * starts transformation of input to output
     */
    public void run(final InputStream in, final PrintStream out) throws IOException {
        this.in = in;
        this.out = out;
        run();
    }

    /**
     * special: all occurences of 'CLAZZNAME' in the inputfile will be replaced by the given String 'clazzName'
     */
    public void setClazzName(final String clazzName) {
        this.clazzName = clazzName;
    }

    public String getClazzName() {
        return clazzName;
    }

    /**
     * starts transformation of input to output
     */
    public void run() throws IOException {
        int prevChar = -1;
        int c = 0;
        final StringBuffer line = new StringBuffer(2000);
        int lineCount = 0;
        while ((c = in.read()) >= 0) {
            if (c == 13) {
                continue;
            }
            if (c == 10) {
                lineCount++;
            }
            if (c == '%' && prevChar == '<') {                                    // a tagged area starts
                boolean isInLine = false;        // tagged area starts in the middle of a line
                if (line.length() > 0) {
                    line.setLength(line.length() - 1);
                    if (line.length() > 0) {
                        isInLine = true;
                        out.print("out.print( \"" + line.toString() + "\"");
                    }
                }
                // read in the tagged area
                final StringBuffer b = new StringBuffer(2000);
                while (!((c = in.read()) == '>' && prevChar == '%') && c > 0) {
                    if (c == 10) {
                        lineCount++;
                    }
                    b.append((char) c);
                    prevChar = c;
                }
                b.setLength(b.length() - 1);
                // check if it starts with a '+' => print( "text"+[tagged area] );
                if (isInLine && (b.length() <= 0 || b.charAt(0) != '+')) {
                    out.println("); // template line:" + lineCount);
                    line.setLength(0);
                    isInLine = false;
                }
                String toInsert = b.toString();  // the tagged area
                if (clazzName != null)           // special
                {
                    toInsert = replace(toInsert, "CLAZZNAME", clazzName, 0);
                }
                if (isInLine) {
                    out.print(toInsert);
                    out.println(");// template line:" + lineCount);
                    line.setLength(0);
                } else {
                    if (toInsert.length() > 0 && toInsert.charAt(0) == '+') {
                        out.println("out.print(\"\"" + toInsert + " ); // template line:" + lineCount);
                    } else {
                        out.println(toInsert + "// template line:" + lineCount);
                    }
                }
                prevChar = -1;
            } else {
                line.append((char) c);
                if ( /* prevChar >= 0 && */c == 10) {
                    while (line.length() > 0 && line.charAt(line.length() - 1) <= 32) {
                        line.setLength(line.length() - 1);
                    }
                    out.println("out.println( \"" + line.toString() + "\" ); // template line:" +
                            lineCount);
                    line.setLength(0);
                }
                prevChar = c;
            }
        }

        if (line.length() > 0) {
            while (line.length() > 0 && line.charAt(line.length() - 1) <= 0) {
                line.setLength(line.length() - 1);
            }
            out.println("out.println( \"" + line.toString() + "\" ); // template line:" + lineCount);
        }
    }

    public void closeIn() {
        try {
            in.close();
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    public void closeOut() {
        try {
            out.flush();
            out.close();
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * sample code
     */
    public static void main(final String[] args) throws Exception {
        final FileInputStream in = new FileInputStream("\\temp\\tpl\\sample.jpl");
        final PrintStream out = new PrintStream(new FileOutputStream("\\temp\\tpl\\sample.java"));
        final TemplateSplitter splitter = new TemplateSplitter(in, out);
        splitter.run();
        splitter.closeIn();
        splitter.closeOut();
    }

}

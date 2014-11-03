
/******************************************************
 * File: IContextReceiver.java
 * created 09-May-00 10:34:06 PM by moelrue
 */

package org.nustaq.generation.template;


import java.io.PrintStream;


/**
 * All templates have to implement this interface
 */
public interface IContextReceiver {
    /**
     * @param context an arbitrary context as given in the TemplateExecutor class
     * @param out     the stream to write output
     */
    public void receiveContext(Object context, PrintStream out) throws Exception;

}


/******************************************************
 * File: TemplateExecutor.java
 * created 09-May-00 8:22:18 PM by moelrue
 */

package org.nustaq.generation.template;


import javax.tools.*;
import java.io.*;
import java.util.*;


/**
 * <pre>
 * this class does the following:
 *  - a template file (=input) is processed by the TemplateSplitter (see doc of .).
 *    the resulting outputfile is put into a temporary directory (.\tempgen).
 *  - the javac compiler is invoked to compile the generated file. The resulting class is loaded, executed
 *    and produces the final result (a file).
 *
 * </pre>
 */
public class TemplateExecutor {

    public static int Run(String outputFile, String templateFile, Object context) {
        TemplateExecutor exec = new TemplateExecutor(new File(templateFile), new File(outputFile), context);
        boolean res = false;
        try {
            res = exec.execute(new PrintStream(outputFile));
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return res ? -1 : 1;
    }

    public static int Run(String templateFile, Object context) {
        return Run("gen", templateFile, context);
    }

    File templateFile, outputFile = new File("gen");
    Object context;
    String tmpDir = "tempgen";

    // specifies wether the output is doubled on system.out
    public boolean sysout = false;

    /**
     * creates a new, unitialized TemplateExecutor
     */
    public TemplateExecutor() {
    }

    public void setTemplateFile(File templateFile) {
        this.templateFile = templateFile;
    }

    public File getTemplateFile() {
        return templateFile;
    }

    /**
     * creates a new TemplateExecutor.
     *
     * @param templateFile - the templatefile to process
     * @param outputFile   - the file to write to
     * @param context      - an Object which is forwarded to the template
     */
    public TemplateExecutor(File templateFile, File outputFile, Object context) {
        setFiles(templateFile, outputFile, context);
    }

    /**
     * helper method
     *
     * @param f - a file
     * @return the name of the file without extension (i.e. '.xml', '.java' cut off )
     */
    String getNameWithoutExt(File f) {
        String fil = f.getName();
        if (fil.lastIndexOf('.') >= 0) {
            return fil.substring(0, fil.lastIndexOf('.'));
        }
        return fil;
    }

    /**
     * sets the files to be processed by the TemplateExecutor
     *
     * @param templateFile - the templatefile to process
     * @param outputFile   - the file to write to
     * @param context      - an Object which is forwarded to the template
     */
    public void setFiles(File templateFile, File outputFile, Object context) {
        this.templateFile = templateFile;
        this.outputFile = outputFile;
        this.context = context;
    }

    public boolean execute() throws Exception {
        return execute(null);
    }

    /**
     * set the directory to put temporary files
     */
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    public String getTmpDir() {
        return tmpDir;
    }

    /**
     * execute the template and produce the outputfiles.<br>
     * Note: if there are errors like "bad classformat" or "invalid magic number", clear all files in the
     * .\tempgen directory
     * @param output - output is written here if != null, else a file is created in the output directory
     * @return
     * @throws Exception
     */
    public boolean execute(PrintStream output) throws Exception {
        File tempFile = new File(tmpDir);
        MyCL myCl = new MyCL();
        if (!tempFile.exists()) {
            tempFile.mkdir();
        }
        if (!tempFile.exists() || !tempFile.isDirectory()) {
            throw new TemplateExecutionException("Could not create temporary directory, aborting. (" +
                    tempFile.getAbsolutePath() + ")");
        }
        try {
            String resultClassName = getNameWithoutExt(outputFile);
            String className = getNameWithoutExt(templateFile) + "GEN";
            File clazzFile = new File(tmpDir + File.separator + className + ".class");
            tempFile = new File(tmpDir + File.separator + className + ".java");
            if (!clazzFile.exists() || clazzFile.lastModified() < templateFile.lastModified() ||
                !templateFile.exists() ) // if template file is loaded via classpath => always recreate ...
            {
                // changed ? if not skip compiling
                PrintStream javaOut = new PrintStream(new FileOutputStream(tempFile));
                // process the template
                new File(tmpDir + File.separator + className + ".class").delete();
                InputStream in = null;
                if ( templateFile.exists() ) {
                    in = new FileInputStream(templateFile);
                } else {
                    final String resource = templateFile.getPath().replace('\\', '/');
                    in = getClass().getResourceAsStream(resource);
                    if ( in == null ) {
                        in = getClass().getResourceAsStream("/"+resource);
                        if ( in == null ) {
                            System.out.println("could not locate template file " + templateFile + ", could not locate via CP " + resource);
                            System.exit(-1);
                        }
                    }
                }
                TemplateSplitter splitter = new TemplateSplitter(in, javaOut);
                splitter.setClazzName(className);
                splitter.run();
                splitter.closeIn();
                splitter.closeOut();
                System.out.println("Compiling:" + tempFile.getAbsolutePath());
                // compile the intermediate file, only until Java Version 1.5
//                int res = compile(new String [] { "-d", tmpDir, "-classpath",
//                                                  System.getProperty("java.class.path")
//                                                    + File.pathSeparator + "tempgen",
//                                                  tempFile.getAbsolutePath(),
//                                                });
//                if (res != 0)
//                {
//                    throw new TemplateExecutionException("COMPILE ERROR:" + res);
//                }
//                System.out.println("COMPILE PROCESS RESULT:" + res);

                // since Java Version 1.6
                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
                StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
                File[] files = new File[]{tempFile};
                Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(files));
                compiler.getTask(null, fileManager, diagnostics, null, null, compilationUnits).call();
                boolean error = false;
                for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                    if (diagnostic.getSource() != null) {
                        error = true;
                        System.out.println(diagnostic.getMessage(Locale.getDefault()) + " in line "
                                + diagnostic.getLineNumber() + " in "
                                + diagnostic.getSource());
                    }
                }
                fileManager.close();
                if (error) {
                    return false;
                }
            }
            // load and define the generated class
            File clzFi = new File(tmpDir + File.separator + className + ".class");
            int len = (int) clzFi.length();
            byte clz[] = new byte[len];
            FileInputStream clzIn = new FileInputStream(clzFi);
            clzIn.read(clz);
            clzIn.close();
            myCl.defineClass(clz);
            // instantiate the generated class & run it
            Class clazz = null;
            try {
                clazz = myCl.loadClass(className);
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();

                throw new TemplateExecutionException(
                        "ERROR: Could not locate compiled class, please add '.\\tempgen' to your bootclasspath");
            }
            System.out.println("loaded class:" + clazz.getName());
            Object target = clazz.newInstance();
            //System.out.println("creating:"+outputFile.getAbsoluteFile());
            PrintStream out = output;
            if (out == null) {
                out = new PrintStream(new FileOutputStream(outputFile));
            }
            if (sysout) {
                ((IContextReceiver) target).receiveContext(context, System.out);
            }
            ((IContextReceiver) target).receiveContext(context, out);
            if (outputFile != null) {
                out.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TemplateExecutionException("Error during template Execution, view System.out");
        }
        return true;
    }

    public File getOutputFile() {
        return outputFile;
    }

    static class MyCL extends ClassLoader {
        public void defineClass(byte b[]) {
            super.defineClass(b, 0, b.length);
        }
    }

    public static void main(String[] arg) {
        // template, context.
        Run(arg[0], arg[1]);
    }
}

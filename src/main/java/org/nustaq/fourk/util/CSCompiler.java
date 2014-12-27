package org.nustaq.fourk.util;

import javax.script.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Created by ruedi on 26/12/14.
 */
public class CSCompiler {

    static CSCompiler theCompiler;

    public static CSCompiler getInstance() {
        synchronized (CSCompiler.class) { // only used once per file, so synchronize
            if ( theCompiler == null )
                theCompiler = new CSCompiler();
            return theCompiler;
        }
    }

    private final CompiledScript compiledScript;
    private final Bindings bindings;

    public CSCompiler() {
        String script = readScript("/coffee-script.js");

        ScriptEngine nashorn = new ScriptEngineManager().getEngineByName("nashorn");
        try {
            compiledScript = ((Compilable) nashorn).compile(script + "\nCoffeeScript.compile(__source, {bare: true});");
            bindings = nashorn.getBindings(ScriptContext.ENGINE_SCOPE);
        } catch (ScriptException e) {
            throw new RuntimeException("Unable to compile script", e);
        }
    }

    private static String readScript(String path) {
        try (InputStream input = CSCompiler.class.getResourceAsStream(path)) {
            Scanner s = new Scanner(input,"UTF8").useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        } catch (IOException e) {
            throw new RuntimeException("Unable to read " + path, e);
        }
    }

    public synchronized String toJs(String coffee) throws ScriptException {
        bindings.put("__source", coffee);
        return compiledScript.eval(bindings).toString();
    }

    public void toJs(File f, File jsName) throws ScriptException, IOException {
        if ( jsName.exists() && jsName.lastModified() >= f.lastModified() )
            return;
        System.out.println("compiling "+f.getAbsolutePath()+" to "+jsName.getAbsolutePath());
        Path path = FileSystems.getDefault().getPath(f.getParent(), f.getName());
        byte[] bytes = Files.readAllBytes(path);
        String js = CSCompiler.getInstance().toJs(new String(bytes, Charset.forName("UTF-8")));
        FileOutputStream fos = new FileOutputStream(jsName);
        fos.write(js.getBytes(Charset.forName("UTF-8")));
        fos.close();
    }

    public static void main(String arg[]) throws ScriptException {
        CSCompiler comp = new CSCompiler();
        String js = comp.toJs("switch day\n" +
                "  when \"Mon\" then go work\n" +
                "  when \"Tue\" then go relax\n" +
                "  when \"Thu\" then go iceFishing\n" +
                "  when \"Fri\", \"Sat\"\n" +
                "    if day is bingoDay\n" +
                "      go bingo\n" +
                "      go dancing\n" +
                "  when \"Sun\" then go church\n" +
                "  else go work");
        System.out.println(js);
    }

}

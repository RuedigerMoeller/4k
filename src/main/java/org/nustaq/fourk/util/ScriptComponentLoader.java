package org.nustaq.fourk.util;

import org.nustaq.fourk.FourK;
import org.nustaq.kson.Kson;

import javax.script.ScriptException;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ruedi on 26.10.14.
 *
 * A class merging a given list of js or html template snippets into a single file.
 * The location snippets/js files are looked up using the lookup path (similar to how
 * java looks up classpath for classes).
 *
 * This way only two http requests are required to load libraries and template snippets
 * for SPA applications without the need for client-side hacks.
 *
 */
public class ScriptComponentLoader {

    File resourcePath[];

    public ScriptComponentLoader setResourcePath( String ... path ) {
        resourcePath = new File[path.length];
        for (int i = 0; i < path.length; i++) {
            String dir = path[i];
            File f = new File( dir );
            if ( f.exists() && ! f.isDirectory() ) {
                throw new RuntimeException("only directorys can reside on resourcepath");
            }
            resourcePath[i] = f;
        }
        return this;
    }

    public List<File> lookupResource( String finam, HashSet<String> alreadyFound, HashSet<String> alreadyChecked) {
        if ( alreadyChecked.contains(finam) ) {
            return new ArrayList<>();
        }
        ArrayList<File> res = new ArrayList<>();
        finam  = finam.replace('/',File.separatorChar);
        while ( finam.startsWith("/") )
            finam = finam.substring(1);
        for (int i = 0; i < resourcePath.length; i++) {
            File file = resourcePath[i];
            File loc = new File(file.getAbsolutePath() + File.separatorChar + finam);
            if ( finam.indexOf('.') >= 0 ) { // assume is a file
                if (loc.exists() && !alreadyFound.contains(finam+"#"+loc.getName())) {
                    res.add(loc);
//                    System.out.println("ressolving "+finam+" to "+loc.getAbsolutePath());
                    alreadyFound.add(finam+"#"+loc.getName());
                    return res; // in case of single file, return immediately
                }
            } else { // assume dir, add all files in this dir to result if not alreadyFound
                if ( loc.exists() && loc.isDirectory() ) {
                    File dep = new File(loc, "dep.kson");
                    // problem: dependency resolution ignores respath for snippet files .. put them in front
                    File f[] = loc.listFiles();
                    for (int j = 0; f != null && j < f.length; j++) {
                        File singleFile = f[j];
                        if ( ! singleFile.isDirectory() && !alreadyFound.contains(finam+"#"+singleFile.getName()) &&
                            singleFile.getName().endsWith(".tpl.html"))
                        {
                            res.add(singleFile);
//                            System.out.println("ressolving "+finam+" to "+singleFile.getAbsolutePath());
                            alreadyFound.add(finam+"#"+singleFile.getName());
                        }
                    }
                    if ( dep.exists() ) {
                        try {
                            String deps[] = ((FourKModule) new Kson().map(FourKModule.class).readObject(dep, FourKModule.class)).depends;
                            for (int j = 0; j < deps.length; j++) {
                                String lib = deps[j];
                                if ( ! alreadyChecked.contains(lib) ) {
                                    if (FourK.DevMode)
                                        System.out.println("=> lib dependency "+lib+" of "+finam);
                                    res.addAll(lookupResource(lib,alreadyFound,alreadyChecked));
                                    alreadyChecked.add(lib);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if ( alreadyChecked.contains(finam) ) // might have been included by dependencies (cyclic)
                        return res;

                    f = loc.listFiles();
                    for (int j = 0; f != null && j < f.length; j++) {
                        File singleFile = f[j];
                        if ( ! singleFile.isDirectory() && !alreadyFound.contains(finam+"#"+singleFile.getName() )) {
                            res.add(singleFile);
//                            System.out.println("ressolving "+finam+" to "+singleFile.getAbsolutePath());
                            alreadyFound.add(finam+"#"+singleFile.getName());
                        }
                    }
                }
            }
        }
        return res;
    }


    /**
     * lookup and merge scripts.
     *
     * rules:
     *  if a name is a .js file, lookup ressourcepath in order for the file
     *  if a name is a component name (no '.' in name !!!), search ressoucepath
     *  for each directory of resourcepath
     *   - look for subdirectory named [component name] and load all .js files of this directory.
     *   - if a file is found twice, first match wins (override .js via resourcePath)
     *
     * @param jsFileNames list of component names or direct .js filename
     * @return
     */
    public byte[] mergeScripts( String ... jsFileNames ) {
        // inefficient, however SPA's load once, so expect not too many requests
        ByteArrayOutputStream bout = new ByteArrayOutputStream(200000);
        HashSet hs = new HashSet();
        HashSet<String> done = new HashSet<>();
        for (int i = 0; i < jsFileNames.length; i++) {
            String jsFileName = jsFileNames[i];
            List<File> files = lookupResource(jsFileName,hs, new HashSet<>());
            files.forEach( f -> {
                String absolutePath = f.getAbsolutePath();
                if ( f.getName().endsWith(".coffee") && ! done.contains(absolutePath) ) {
                    done.add(absolutePath);
                    if (FourK.DevMode)
                        System.out.println("   " + f.getName() + " size:" + f.length());
                    File jsName = new File( f.getParent(), f.getName().substring(0,f.getName().length()-".coffee".length())+".jsx");
                    try {
                        CSCompiler.getInstance().toJs(f,jsName);
                    } catch (ScriptException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    f = jsName;
                    byte[] bytes = new byte[(int) f.length()];
                    try (FileInputStream fileInputStream = new FileInputStream(f)) {
                        fileInputStream.read(bytes);
                        bout.write(bytes);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else
                if ( f.getName().endsWith(".js") && ! done.contains(absolutePath) ) {
                    done.add(absolutePath);
                    if (FourK.DevMode)
                        System.out.println("   "+f.getName()+" size:"+f.length());
                    byte[] bytes = new byte[(int) f.length()];
                    try (FileInputStream fileInputStream = new FileInputStream(f)) {
                        fileInputStream.read(bytes);
                        bout.write(bytes);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        byte[] bytes = bout.toByteArray();
        return bytes;
    }

    public File lookupSingleScript( String rawFileName, String ... jsFileNames ) {
        HashSet hs = new HashSet();
        HashSet<String> done = new HashSet<>();
        for (int i = 0; i < jsFileNames.length; i++) {
            String jsFileName = jsFileNames[i];
            List<File> files = lookupResource(jsFileName,hs, new HashSet<>());
            File res = files.stream().filter( f -> {
                String absolutePath = f.getAbsolutePath();
                if ( (f.getName().endsWith(".js") || f.getName().endsWith(".jsx") ) && ! done.contains(absolutePath) ) {
                    done.add(absolutePath);
                    if (FourK.DevMode)
                        System.out.println("   "+f.getName()+" size:"+f.length());
                    return f.getName().equals(rawFileName);
                }
                return false;
            }).findFirst().get();
            if (res!=null)
                return res;
        }
        return null;
    }

    /**
     * returns document.write of libs
     * @param jsFileNames
     * @return
     */
    public byte[] createScriptTags( String ... jsFileNames ) {
        // inefficient, however SPA's load once, so expect not too many requests
        ByteArrayOutputStream bout = new ByteArrayOutputStream(200000);
        PrintStream ps = new PrintStream(bout);
        HashSet hs = new HashSet();
        HashSet<String> done = new HashSet<>();
        for (int i = 0; i < jsFileNames.length; i++) {
            String jsFileName = jsFileNames[i];
            List<File> files = lookupResource(jsFileName,hs, new HashSet<>());
            files.forEach( f -> {
                String absolutePath = f.getAbsolutePath();
                if ( f.getName().endsWith(".coffee") && ! done.contains(absolutePath) ) {
                    done.add(absolutePath);
                    if (FourK.DevMode)
                        System.out.println("   " + f.getName() + " size:" + f.length());
                    File jsName = new File( f.getParent(), f.getName().substring(0,f.getName().length()-".coffee".length())+".jsx");
                    try {
                        CSCompiler.getInstance().toJs(f,jsName);
                    } catch (ScriptException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ps.println("document.write(\"<script src='jslookup/"+jsName.getName()+"'></script>\")");
                    if (FourK.DevMode)
                        System.out.println("document.write(\"<script src='jslookup/" + jsName.getName() + "'></script>\")");
                } else if ( f.getName().endsWith(".js") && ! done.contains(absolutePath) ) {
                    done.add(absolutePath);
                    if (FourK.DevMode)
                        System.out.println("   " + f.getName() + " size:" + f.length());
                    ps.println("document.write(\"<script src='jslookup/"+f.getName()+"'></script>\")");
                    if (FourK.DevMode)
                        System.out.println("document.write(\"<script src='jslookup/" + jsFileName + "'></script>\")");
                }
            });
        }
        ps.flush();
        byte[] bytes = bout.toByteArray();
        return bytes;
    }

    /**
     * same as mergeJS but concatenate .html files found
     * @param templateFileNames
     * @return
     */
    public byte[] mergeTemplateSnippets( String ... templateFileNames ) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(2000);
        HashSet hs = new HashSet();
        PrintStream pout = new PrintStream(bout);
        pout.println("document.write('\\");
        for (int i = 0; i < templateFileNames.length; i++) {
            String jsFileName = templateFileNames[i];
            List<File> files = lookupResource(jsFileName, hs, new HashSet<>());
            for (int j = 0; j < files.size(); j++) {
                File f = files.get(j);
                if ( f.getName().endsWith(".html") )
                {
                    try (FileReader fileInputStream = new FileReader(f)) {
                        BufferedReader in = new BufferedReader(fileInputStream);
                        while (in.ready()) {
                            String line = in.readLine();
                            line = line.replace("\'", "\\'");
                            pout.println(line+"\\");
                        }
                        in.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }
        pout.println("');");
        pout.flush();
        return bout.toByteArray();
    }

    public static void main( String arg[]) {
        ScriptComponentLoader loader = new ScriptComponentLoader().setResourcePath(
            ".",
            "/home/ruedi/IdeaProjects/abstractor/netty-kontraktor/src/main/webroot",
            "/home/ruedi/IdeaProjects/abstractor/src/main/javascript/js"
        );
//        System.out.println(loader.lookupResource("index.html"));
//        System.out.println(loader.lookupResource("index.html1"));
//        System.out.println(loader.lookupResource("kontraktor.js"));

//        System.out.println( new String(loader.mergeScripts(
//            "jquery-2.1.1.js", "knockout3.2.0.js", "kontraktor.js"
//        )));
        System.out.println( new String(loader.mergeTemplateSnippets(
            "login.tpl.html"
        )));
    }

}

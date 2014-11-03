package org.nustaq.generation.templatesample;

import org.nustaq.generation.template.TemplateExecutor;

/**
 * Created by ruedi on 26.05.14.
 *
 * Run from project root for correct pathes
 *
 */
public class TemplateSample {

    public static class TemplateContext {

        public String clazzName;
        public String getters[] = { "Test", "Other", "Example" };

        public TemplateContext(String clazzName) {
            this.clazzName = clazzName;
        }

    }

    public static void main( String arg[] ) {

        TemplateExecutor.Run("./src/main/resources/sample.jsp", new TemplateContext("GeneratedClass"));

    }
}

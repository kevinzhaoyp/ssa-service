package com.ssa.plugin.sqlApi.ext;

import com.jfinal.template.Directive;
import com.jfinal.template.Env;
import com.jfinal.template.io.Writer;
import com.jfinal.template.stat.Scope;

/**
 * enjoy template ext
 */
public class ObjDirective extends Directive {
    @Override
    public void exec(Env env, Scope scope, Writer writer) {

//        writer.write(scope.get("o").toString());
    }
}

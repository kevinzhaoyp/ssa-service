package com.ssa.plugin.apiEditor;

import com.ssa.plugin.annotation.SsaApiClass;
import com.ssa.plugin.annotation.SsaApiMethod;

@SsaApiClass
public class ApiManager {
    @SsaApiMethod
    public Object getApiName() {
        return "apiManager";
    }
}

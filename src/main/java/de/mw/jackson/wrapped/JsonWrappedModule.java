package de.mw.jackson.wrapped;

import com.fasterxml.jackson.databind.module.SimpleModule;

public class JsonWrappedModule extends SimpleModule {
    
    public JsonWrappedModule() {
        super("JsonWrappedModule");
    }
            
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        setSerializerModifier(new JsonWrappedBeanSerializerModifier());
    }

}

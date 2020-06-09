package de.mw.jackson.wrapped;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.VirtualBeanPropertyWriter;
import com.fasterxml.jackson.databind.util.Annotations;

class WrappingPropertyWriter extends VirtualBeanPropertyWriter {
    
    private BeanSerializer wrappedPropsSerializer;              
            
    public WrappingPropertyWriter(BeanPropertyDefinition propDef, 
                                  Annotations contextAnnotations, 
                                  JavaType declaredType,
                                  BeanSerializer wrappedPropsSerializer) {
        
        super(propDef, contextAnnotations, declaredType);
        this.wrappedPropsSerializer = wrappedPropsSerializer;
    }

    @Override
    public void serializeAsField(Object value, JsonGenerator jgen, SerializerProvider provider) throws Exception {
        jgen.writeFieldName(_name);
        serializeAsElement(value, jgen, provider);
    }

    @Override
    public void serializeAsElement(Object value, JsonGenerator jgen, SerializerProvider provider) throws Exception {
        wrappedPropsSerializer.serialize(value, jgen, provider);
    }

    @Override
    protected Object value(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        throw new IllegalStateException("Should not be called on this type");
    }

    @Override
    public VirtualBeanPropertyWriter withConfig(MapperConfig<?> config, AnnotatedClass declaringClass, BeanPropertyDefinition propDef, JavaType type) {
        throw new IllegalStateException("Should not be called on this type");
    }
    
}
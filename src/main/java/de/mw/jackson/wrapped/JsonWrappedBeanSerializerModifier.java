package de.mw.jackson.wrapped;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

/**
 * BeanSerializerModifier that detects the {@link JsonWrapped} annotation 
 * and modifies the original {@link BeanSerializer} accordingly.
 */
class JsonWrappedBeanSerializerModifier extends BeanSerializerModifier {
    
    @Override
    public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc, JsonSerializer<?> serializer) {
        if (serializer instanceof BeanSerializer) {
            WrappingBeanSerializerBuilder builder = new WrappingBeanSerializerBuilder((BeanSerializer) serializer);
            if (builder.needsWrapping(beanDesc)) {
                return builder.withWrappedProperties(config, beanDesc);
            }
        }
        
        return serializer;
    }
    
    

}
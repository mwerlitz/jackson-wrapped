package de.mw.jackson.wrapped;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.VirtualAnnotatedMember;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerBuilder;
import com.fasterxml.jackson.databind.util.SimpleBeanPropertyDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

class WrappingBeanSerializerBuilder extends BeanSerializer {
    
    WrappingBeanSerializerBuilder(BeanSerializer src) {
        super(src);
    }   
    
    boolean needsWrapping() {
        for (BeanPropertyWriter writer : _props) {
            if (writer.getAnnotation(JsonWrapped.class) != null) {
                return true;
            }
        }
        
        if (_filteredProps != null) {
            for (BeanPropertyWriter writer : _filteredProps) {
                if (writer.getAnnotation(JsonWrapped.class) != null) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    BeanSerializer withWrappedProperties(MapperConfig<?> config, BeanDescription beanDesc) {
        PropPair remainingProps = wrapProperties(_props, (_filteredProps == null ? new BeanPropertyWriter[0] : _filteredProps), config, beanDesc);
        return createBeanSerializer(remainingProps, config, beanDesc);
    }
    
    private BeanSerializer createBeanSerializer(PropPair props, MapperConfig<?> config, BeanDescription beanDesc) {
        BeanSerializerBuilder builder = new BeanSerializerBuilder(beanDesc);
        builder.setTypeId(_typeId);
        builder.setAnyGetter(_anyGetterWriter);
        builder.setFilterId(_propertyFilterId);
        builder.setObjectIdWriter(_objectIdWriter);
        
        return new BeanSerializer(_beanType, builder, props.props.toArray(new BeanPropertyWriter[props.props.size()]), props.fprops.toArray(new BeanPropertyWriter[props.fprops.size()]));
    }
    
    private PropPair wrapProperties(BeanPropertyWriter[] propsIn, BeanPropertyWriter[] fpropsIn, MapperConfig<?> config, BeanDescription beanDesc) {
        List<BeanPropertyWriter> propsOut  = new ArrayList<BeanPropertyWriter>(propsIn.length);
        List<BeanPropertyWriter> fpropsOut = new ArrayList<BeanPropertyWriter>(fpropsIn.length);
        
        Map<String, PropPair> wrappedProps = new LinkedHashMap<String, PropPair>();
        
        for (BeanPropertyWriter prop : propsIn) {                
            JsonWrapped an = prop.getAnnotation(JsonWrapped.class);
            if (validAnnotation(an)) {
            	String property = getProperty(an);
            	PropPair wrapped = wrappedProps.get(property);
            	if (wrapped == null) {
            		wrapped = new PropPair();
            		wrappedProps.put(property, wrapped);
            	}
                wrapped.props.add(prop);
            } else {
                propsOut.add(prop);
            }
        }
        
        for (BeanPropertyWriter prop : fpropsIn) {                
            JsonWrapped an = prop.getAnnotation(JsonWrapped.class);
            if (validAnnotation(an)) {
            	String property = getProperty(an);
            	PropPair wrapped = wrappedProps.get(property);
            	if (wrapped == null) {
            		wrapped = new PropPair();
            		wrappedProps.put(property, wrapped);
            	}
                wrapped.fprops.add(prop);
            } else {
                fpropsOut.add(prop);
            }
        }
        
        for (Entry<String, PropPair> entry : wrappedProps.entrySet()) {
        	propsOut.add(constructVirtualProperty(entry.getKey(), entry.getValue(), config, beanDesc));
        }
        
        PropPair remainingProps = new PropPair();
        remainingProps.props = propsOut;
        remainingProps.fprops = fpropsOut;
        return remainingProps;
    }
    
    private boolean validAnnotation(JsonWrapped an) {
    	return (an != null && 
    			an.value() != null && 
    			!an.value().trim().isEmpty());
    }
    
    private String getProperty(JsonWrapped an) {
    	return an.value().trim();
    }
    
    private BeanPropertyWriter constructVirtualProperty(String name, PropPair wrappedProps, MapperConfig<?> config, BeanDescription beanDesc) {
        // code party from com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector._constructVirtualProperty(Prop, MapperConfig<?>, AnnotatedClass)
        AnnotatedClass ac = beanDesc.getClassInfo();
        PropertyMetadata metadata = PropertyMetadata.STD_OPTIONAL;
        PropertyName propName = new PropertyName(name);
        JavaType type = config.constructType(Object.class);
        AnnotatedMember member = new VirtualAnnotatedMember(ac, ac.getRawType(), propName.getSimpleName(), type);
        SimpleBeanPropertyDefinition propDef = SimpleBeanPropertyDefinition.construct(config, member, propName, metadata, Include.NON_EMPTY);

        BeanSerializer wrappedPropsSerializer = createBeanSerializer(wrappedProps, config, beanDesc);
        return new WrappingPropertyWriter(propDef, ac.getAnnotations(), type, wrappedPropsSerializer);
    }
    
    private static class PropPair {
        
        private List<BeanPropertyWriter> props  = new ArrayList<BeanPropertyWriter>();
        private List<BeanPropertyWriter> fprops = new ArrayList<BeanPropertyWriter>();
        
    }
}
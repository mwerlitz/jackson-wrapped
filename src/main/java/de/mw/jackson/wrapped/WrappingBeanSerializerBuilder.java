package de.mw.jackson.wrapped;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.VirtualAnnotatedMember;
import com.fasterxml.jackson.databind.ser.AnyGetterWriter;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerBuilder;
import com.fasterxml.jackson.databind.ser.impl.FilteredBeanPropertyWriter;
import com.fasterxml.jackson.databind.util.SimpleBeanPropertyDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Helper class for creating wrapping virtual properties.
 * 
 * This works by analyzing the properties ({@link BeanPropertyWriter}) of the existing {@link BeanSerializer}.
 * If a property that should be wrapped is detected it will be grouped with others into a virtual property {@link WrappingPropertyWriter}
 * which is in fact a wrapper for a new {@link BeanSerializer} of a "virtual bean" (matching the original type). 
 * The existing properties ({@link BeanPropertyWriter}) will be moved to the "virtual bean".
 *
 * As a result a copy of the original {@link BeanSerializer} will created containing only the non-wrapped properties
 * and the virtual properties (BeanSerializer is immutable). 
 * 
 * Unfortunately it is not possible to access all data about the original {@link BeanSerializer} from outside.
 * Thus this class is a subclass of it. Do not use the builder instance for serialization.
 */
class WrappingBeanSerializerBuilder extends BeanSerializer {
    
    WrappingBeanSerializerBuilder(BeanSerializer src) {
        super(src);
    }   
    
    boolean needsWrapping(BeanDescription beanDesc) {
        // type level
        if (beanDesc.getClassInfo() != null && beanDesc.getClassInfo().getAnnotation(JsonWrapped.class) != null) {
            return true;
        }
        
        // property level - non filtered
        for (BeanPropertyWriter writer : _props) {
            if (writer != null && writer.getAnnotation(JsonWrapped.class) != null) {
                return true;
            }
        }
        
        // property level - filtered
        if (_filteredProps != null) {
            for (BeanPropertyWriter writer : _filteredProps) {
                if (writer != null && writer.getAnnotation(JsonWrapped.class) != null) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    BeanSerializer withWrappedProperties(MapperConfig<?> config, BeanDescription beanDesc) {
        Props remainingProps = wrapProperties(_props, (_filteredProps == null ? new BeanPropertyWriter[0] : _filteredProps), _anyGetterWriter, config, beanDesc);
        return createBeanSerializer(remainingProps, beanDesc);
    }
    
    private BeanSerializer createBeanSerializer(Props props, BeanDescription beanDesc) {
        BeanSerializerBuilder builder = new BeanSerializerBuilder(beanDesc);
        builder.setTypeId(_typeId);
        builder.setAnyGetter(props.anyGetterWriter);
        builder.setFilterId(_propertyFilterId);
        builder.setObjectIdWriter(_objectIdWriter);
        
        return new BeanSerializer(_beanType, builder, props.props.toArray(new BeanPropertyWriter[props.props.size()]), props.fprops.toArray(new BeanPropertyWriter[props.fprops.size()]));
    }
    
    private BeanSerializer createWrappingBeanSerializer(Props props, BeanDescription beanDesc) {
        BeanSerializerBuilder builder = new BeanSerializerBuilder(beanDesc);
        builder.setAnyGetter(props.anyGetterWriter);
        builder.setFilterId(_propertyFilterId);
        
        return new BeanSerializer(_beanType, builder, props.props.toArray(new BeanPropertyWriter[props.props.size()]), props.fprops.toArray(new BeanPropertyWriter[props.fprops.size()]));
    }
    
    private Props wrapProperties(BeanPropertyWriter[] propsIn, BeanPropertyWriter[] fpropsIn, AnyGetterWriter anyGetterWriter, MapperConfig<?> config, BeanDescription beanDesc) {
        List<BeanPropertyWriter>  propsOut = new ArrayList<BeanPropertyWriter>( propsIn.length);
        List<BeanPropertyWriter> fpropsOut = new ArrayList<BeanPropertyWriter>(fpropsIn.length);
        Map<String, Props> wrappedProps = new LinkedHashMap<String, Props>(); // key = virtual property, value = grouped wrapped properties
        Props remainingProps = new Props();
        
        // filter properties (BeanPropertyWriter) that should be wrapped
        // non-wrapped go into propsOut/fpropsOut
        // wrapped go into wrappedProps map
        filterAndGroupWrappedProperties( propsIn,  propsOut, wrappedProps, beanDesc, false); // non filtered
        filterAndGroupWrappedProperties(fpropsIn, fpropsOut, wrappedProps, beanDesc, true);  // filtered
        
        // wrap properties written by @JsonAnyGetter
        if (anyGetterWriter != null) {
            String virtualProperty = getVirtualPropertyFromProperty(beanDesc.findAnyGetter());
            if (virtualProperty != null) {
                Props wrapped = getOrCreateProps(wrappedProps, virtualProperty);
                wrapped.anyGetterWriter = anyGetterWriter;
            } else {
                remainingProps.anyGetterWriter = anyGetterWriter;
            }
        }
        
        // create wrapping virtual properties for each group and add them to the remaining non-props of the bean
        for (Entry<String, Props> entry : wrappedProps.entrySet()) {
            if (!entry.getValue().props.isEmpty()) {
                propsOut.add(constructVirtualProperty(entry.getKey(), entry.getValue(), config, beanDesc, false));
            }
            if (!entry.getValue().fprops.isEmpty()) {
                fpropsOut.add(constructVirtualProperty(entry.getKey(), entry.getValue(), config, beanDesc, true));
            }
        }
        
        remainingProps.props = propsOut;
        remainingProps.fprops = fpropsOut;
        return remainingProps;
    }
    
    private void filterAndGroupWrappedProperties(BeanPropertyWriter[] in, List<BeanPropertyWriter> out, Map<String, Props> wrappedProps, BeanDescription beanDesc, boolean filtered) {
        for (BeanPropertyWriter prop : in) {
            if (prop != null) {
                String virtualProperty = getVirtualProperty(prop, beanDesc.getClassInfo());
                if (virtualProperty != null) {
                    Props wrapped = getOrCreateProps(wrappedProps, virtualProperty);
                    if (filtered) {
                        wrapped.fprops.add(prop);
                        wrapped.views.addAll(Arrays.asList(prop.getViews())); // views are only interesting for filtered props
                    } else {
                        wrapped.props.add(prop);
                    }
                } else {
                    out.add(prop);
                }
            }
        }
    }
    
    private Props getOrCreateProps(Map<String, Props> wrappedProps, String virtualProperty) {
        Props wrapped = wrappedProps.get(virtualProperty);
        if (wrapped == null) {
            wrapped = new Props();
            wrappedProps.put(virtualProperty, wrapped);
        }
        return wrapped;
    }
    
    private String getVirtualProperty(BeanPropertyWriter prop, AnnotatedClass type) {
        String virtualProperty = getVirtualPropertyFromProperty(prop.getMember());
        if (virtualProperty == null) {
            virtualProperty = getVirtualPropertyFromType(type, prop.getName());
        }
        return virtualProperty;
    }
    
    private String getVirtualPropertyFromProperty(Annotated annotated) {
        JsonWrapped annotation = annotated.getAnnotation(JsonWrapped.class);
        if (annotation != null && annotation.value() != null && !annotation.value().trim().isEmpty()) {
            return annotation.value().trim();
        }
        return null;
    }
    
    private String getVirtualPropertyFromType(Annotated annotated, String propName) {
        String virtualProperty = getVirtualPropertyFromProperty(annotated);
        if (virtualProperty != null && Arrays.asList(annotated.getAnnotation(JsonWrapped.class).properties()).contains(propName)) {
            return virtualProperty;
        }
        return null;
    }
    
    private BeanPropertyWriter constructVirtualProperty(String name, Props wrappedProps, MapperConfig<?> config, BeanDescription beanDesc, boolean filtered) {
        // code party from com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector._constructVirtualProperty(Prop, MapperConfig<?>, AnnotatedClass)
        AnnotatedClass ac = beanDesc.getClassInfo();
        PropertyMetadata metadata = PropertyMetadata.STD_OPTIONAL;
        PropertyName propName = new PropertyName(name);
        JavaType type = config.constructType(Object.class);
        AnnotatedMember member = new VirtualAnnotatedMember(ac, ac.getRawType(), propName.getSimpleName(), type);
        SimpleBeanPropertyDefinition propDef = SimpleBeanPropertyDefinition.construct(config, member, propName, metadata, Include.NON_EMPTY);

        BeanSerializer wrappedPropsSerializer = createWrappingBeanSerializer(wrappedProps, beanDesc);
        
        BeanPropertyWriter writer = new WrappingPropertyWriter(propDef, ac.getAnnotations(), type, wrappedPropsSerializer);
        if (filtered && !wrappedProps.views.isEmpty()) { // filter property by view, if required
            writer = FilteredBeanPropertyWriter.constructViewBased(writer, wrappedProps.views.toArray(new Class<?>[wrappedProps.views.size()]));
        }
        
        return writer;
    }
    
    private static class Props {
        
        private List<BeanPropertyWriter>  props = new ArrayList<BeanPropertyWriter>();
        private List<BeanPropertyWriter> fprops = new ArrayList<BeanPropertyWriter>();
        
        private Set<Class<?>> views = new HashSet<Class<?>>();
        private AnyGetterWriter anyGetterWriter;
    }
}
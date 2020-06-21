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
import java.util.Collections;
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
        
        // anyGetter
        if (beanDesc.findAnyGetter() != null && beanDesc.findAnyGetter().getAnnotation(JsonWrapped.class) != null) {
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
        PropInfo remainingProps = wrapProperties(_props, (_filteredProps == null ? new BeanPropertyWriter[0] : _filteredProps), _anyGetterWriter, config, beanDesc);
        return createBeanSerializer(remainingProps, beanDesc);
    }
    
    private BeanSerializer createBeanSerializer(PropInfo propInfo, BeanDescription beanDesc) {
        BeanSerializerBuilder builder = new BeanSerializerBuilder(beanDesc);
        builder.setTypeId(_typeId);
        builder.setAnyGetter(propInfo.anyGetterWriter);
        builder.setFilterId(_propertyFilterId);
        builder.setObjectIdWriter(_objectIdWriter);
        
        return new BeanSerializer(_beanType, builder, propInfo.props.toArray(new BeanPropertyWriter[propInfo.props.size()]), propInfo.fprops.toArray(new BeanPropertyWriter[propInfo.fprops.size()]));
    }
    
    private BeanSerializer createWrappingBeanSerializer(PropInfo propInfo, BeanDescription beanDesc) {
        BeanSerializerBuilder builder = new BeanSerializerBuilder(beanDesc);
        builder.setAnyGetter(propInfo.anyGetterWriter);
        builder.setFilterId(_propertyFilterId);
        
        return new BeanSerializer(_beanType, builder, propInfo.props.toArray(new BeanPropertyWriter[propInfo.props.size()]), propInfo.fprops.toArray(new BeanPropertyWriter[propInfo.fprops.size()]));
    }
    
    private PropInfo wrapProperties(BeanPropertyWriter[] propsIn, BeanPropertyWriter[] fpropsIn, AnyGetterWriter anyGetterWriter, MapperConfig<?> config, BeanDescription beanDesc) {
        List<BeanPropertyWriter>  propsOut = new ArrayList<BeanPropertyWriter>( propsIn.length);
        List<BeanPropertyWriter> fpropsOut = new ArrayList<BeanPropertyWriter>(fpropsIn.length);
        Map<String, PropInfo> wrappedProps = new LinkedHashMap<String, PropInfo>(); // key = virtual property, value = grouped wrapped properties
        PropInfo remainingProps = new PropInfo();
        
        // filter properties (BeanPropertyWriter) that should be wrapped
        // non-wrapped go into propsOut/fpropsOut
        // wrapped go into wrappedProps map
        filterAndGroupWrappedProperties( propsIn,  propsOut, wrappedProps, beanDesc, false); // non filtered
        filterAndGroupWrappedProperties(fpropsIn, fpropsOut, wrappedProps, beanDesc, true);  // filtered
        
        // wrap properties written by @JsonAnyGetter
        if (anyGetterWriter != null) {
            String virtualProperty = getVirtualPropertyNameFromAnnotation(beanDesc.findAnyGetter());
            if (virtualProperty != null) {
                PropInfo wrapped = getOrCreatePropInfo(wrappedProps, virtualProperty);
                wrapped.anyGetterWriter = anyGetterWriter;
            } else {
                remainingProps.anyGetterWriter = anyGetterWriter;
            }
        }
        
        // create a wrapping virtual property for each group and add them to the remaining normal props of the bean
        for (Entry<String, PropInfo> entry : wrappedProps.entrySet()) {
            BeanPropertyWriter virtualProperty = constructVirtualProperty(entry.getKey(), entry.getValue(), config, beanDesc);
            if (entry.getValue().views.isEmpty()) {
                propsOut.add(virtualProperty);
            } else {
                fpropsOut.add(virtualProperty);
            }
        }
        
        remainingProps.props = propsOut;
        remainingProps.fprops = fpropsOut;
        return remainingProps;
    }
    
    private void filterAndGroupWrappedProperties(BeanPropertyWriter[] in, List<BeanPropertyWriter> out, Map<String, PropInfo> wrappedProps, BeanDescription beanDesc, boolean filteredInProps) {
        for (BeanPropertyWriter prop : in) {
            if (prop != null) {
                String virtualPropertyName = getVirtualPropertyName(prop, beanDesc.getClassInfo());
                if (virtualPropertyName != null) {
                    List<Class<?>> virtualPropertyViews = getVirtualPropertyViews(prop, beanDesc.getClassInfo());
                    PropInfo wrapped = getOrCreatePropInfo(wrappedProps, virtualPropertyName);
                    
                    if (!virtualPropertyViews.isEmpty()) {
                        if (!wrapped.virtualPropertyViews) { // if JsonWrapped defines views, then override exising ones from props
                            wrapped.virtualPropertyViews = true; // and lock them
                            wrapped.views.clear();
                        }
                        wrapped.views.addAll(virtualPropertyViews);
                    }
                    
                    if (filteredInProps) {
                        wrapped.fprops.add(prop);
                        if (!wrapped.virtualPropertyViews) { // do not override views from JsonWrapped
                            wrapped.views.addAll(Arrays.asList(prop.getViews())); // views are only interesting for filtered props
                        }
                    } else {
                        wrapped.props.add(prop);
                    }
                } else {
                    out.add(prop);
                }
            }
        }
    }
    
    private PropInfo getOrCreatePropInfo(Map<String, PropInfo> wrappedProps, String virtualProperty) {
        PropInfo wrapped = wrappedProps.get(virtualProperty);
        if (wrapped == null) {
            wrapped = new PropInfo();
            wrappedProps.put(virtualProperty, wrapped);
        }
        return wrapped;
    }
    
    private String getVirtualPropertyName(BeanPropertyWriter prop, AnnotatedClass type) {
        String virtualProperty = getVirtualPropertyNameFromAnnotation(prop.getMember()); // from property level
        if (virtualProperty == null) {
            virtualProperty = getVirtualPropertyNameFromType(type, prop.getName()); // from class level
        }
        return virtualProperty;
    }
    
    private String getVirtualPropertyNameFromAnnotation(Annotated annotated) {
        JsonWrapped annotation = annotated.getAnnotation(JsonWrapped.class);
        if (annotation != null && annotation.value() != null && !annotation.value().trim().isEmpty()) {
            return annotation.value().trim();
        }
        return null;
    }
    
    private String getVirtualPropertyNameFromType(Annotated typeAnnotation, String propName) {
        String virtualProperty = getVirtualPropertyNameFromAnnotation(typeAnnotation);
        if (virtualProperty != null && Arrays.asList(typeAnnotation.getAnnotation(JsonWrapped.class).properties()).contains(propName)) {
            return virtualProperty;
        }
        return null;
    }
    
    private List<Class<?>> getVirtualPropertyViews(BeanPropertyWriter prop, AnnotatedClass type) {
        List<Class<?>> virtualPropertyViews = getVirtualPropertyViewsFromAnnotation(prop.getMember()); // from property level
        if (virtualPropertyViews.isEmpty()) {
            virtualPropertyViews = getVirtualPropertyViewsFromType(type, prop.getName()); // from class level
        }
        return virtualPropertyViews;
    }
    
    private List<Class<?>> getVirtualPropertyViewsFromAnnotation(Annotated annotated) {
        JsonWrapped annotation = annotated.getAnnotation(JsonWrapped.class);
        if (annotation != null && annotation.views() != null && annotation.views().length > 0) {
            return Arrays.asList(annotation.views());
        }
        return Collections.emptyList();
    }
    
    private List<Class<?>> getVirtualPropertyViewsFromType(Annotated typeAnnotated, String propName) {
        String virtualProperty = getVirtualPropertyNameFromAnnotation(typeAnnotated);
        if (virtualProperty != null && Arrays.asList(typeAnnotated.getAnnotation(JsonWrapped.class).properties()).contains(propName)) {
            return getVirtualPropertyViewsFromAnnotation(typeAnnotated);
        }
        return Collections.emptyList();
    }
    
    private BeanPropertyWriter constructVirtualProperty(String name, PropInfo wrappedProps, MapperConfig<?> config, BeanDescription beanDesc) {
        // code party from com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector._constructVirtualProperty(Prop, MapperConfig<?>, AnnotatedClass)
        AnnotatedClass ac = beanDesc.getClassInfo();
        PropertyMetadata metadata = PropertyMetadata.STD_OPTIONAL;
        PropertyName propName = new PropertyName(name);
        JavaType type = config.constructType(Object.class);
        AnnotatedMember member = new VirtualAnnotatedMember(ac, ac.getRawType(), propName.getSimpleName(), type);
        SimpleBeanPropertyDefinition propDef = SimpleBeanPropertyDefinition.construct(config, member, propName, metadata, Include.NON_EMPTY);

        BeanSerializer wrappedPropsSerializer = createWrappingBeanSerializer(wrappedProps, beanDesc);
        
        BeanPropertyWriter writer = new WrappingPropertyWriter(propDef, ac.getAnnotations(), type, wrappedPropsSerializer);
        if (!wrappedProps.views.isEmpty()) { // filter complete property by view, if required
            writer = FilteredBeanPropertyWriter.constructViewBased(writer, wrappedProps.views.toArray(new Class<?>[wrappedProps.views.size()]));
        }
        
        return writer;
    }
    
    private static class PropInfo {
        
        private List<BeanPropertyWriter>  props = new ArrayList<BeanPropertyWriter>();
        private List<BeanPropertyWriter> fprops = new ArrayList<BeanPropertyWriter>();
        
        private Set<Class<?>> views = new HashSet<Class<?>>();
        private boolean virtualPropertyViews = false;
        private AnyGetterWriter anyGetterWriter;
    }
}
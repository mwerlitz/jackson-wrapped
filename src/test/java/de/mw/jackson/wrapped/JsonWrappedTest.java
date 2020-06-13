package de.mw.jackson.wrapped;


import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;


@SuppressWarnings("unused")
public class JsonWrappedTest {

    private ObjectMapper mapper;
    
    private static interface DefaultView {}
    private static interface View extends DefaultView {}
    private static interface View2 extends DefaultView {}
    
    @Before
    public void setup() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JsonWrappedModule());
    }
    
    @Test
    public void jsonWrapped_warps_propertyField() throws JsonProcessingException {
        class FieldClass {
            @JsonProperty
            int x = 42;
            @JsonWrapped("wrapped")
            @JsonProperty
            int y = 4711;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_ignores_unserializedPropertyField() throws JsonProcessingException {
        class FieldClass {
            @JsonProperty
            int x = 42;
            @JsonWrapped("wrapped")
            int y = 4711;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42}", result);
    }
    
    @Test
    public void jsonWrapped_warps_publicPropertyField() throws JsonProcessingException {
        class FieldClass {
            @JsonProperty
            int x = 42;
            @JsonWrapped("wrapped")
            public int y = 4711;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_warps_property_atFieldLevel() throws JsonProcessingException {
        class PropertyClass {
            int x = 42;
            @JsonWrapped("wrapped")
            int y = 4711;
            
            public int getX() {return x;}
            public int getY() {return y;}
        }
        
        String result = mapper.writeValueAsString(new PropertyClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_warps_property_atGetterLevel() throws JsonProcessingException {
        class PropertyClass {
            int x = 42;
            int y = 4711;
            
            public int getX() {return x;}
            
            @JsonWrapped("wrapped")
            public int getY() {return y;}
        }
        
        String result = mapper.writeValueAsString(new PropertyClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_warps_combined_multipleProperties() throws JsonProcessingException {
        class FieldClass {
            public int x = 42;
            @JsonWrapped("wrapped")
            public int y = 4711;
            @JsonWrapped("wrapped")
            public int z = 10;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711,\"z\":10}}", result);
    }
    
    @Test
    public void jsonWrapped_warps_grouped_multipleProperties() throws JsonProcessingException {
        class FieldClass {
            public int x = 42;
            @JsonWrapped("wrapped")
            public int y = 4711;
            @JsonWrapped("wrapped")
            public int z = 10;
            @JsonWrapped("foo")
            public int a = 1;
            @JsonWrapped("foo")
            public int b = 2;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711,\"z\":10},\"foo\":{\"a\":1,\"b\":2}}", result);
    }
    
    @Test
    public void jsonWrapped_ignores_propertiesNotIncludedInView() throws JsonProcessingException {
        @JsonView(DefaultView.class)
        class FieldClass {
            public int x = 42;
            
            @JsonView(View.class)
            @JsonWrapped("wrapped")
            public int y = 4711;
        }
        
        String result = mapper.writerWithView(DefaultView.class)
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42}", result);
    }
    
    @Test
    public void jsonWrapped_wraps_propertiesIncludedInView() throws JsonProcessingException {
        @JsonView(DefaultView.class)
        class FieldClass {
            public int x = 42;
            
            @JsonView(View.class)
            @JsonWrapped("wrapped")
            public int y = 4711;
        }
        
        String result = mapper.writerWithView(View.class)
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_wraps_propertiesIncludedInMultipleViews() throws JsonProcessingException {
        @JsonView(DefaultView.class)
        class FieldClass {
            public int x = 42;
            
            @JsonView({View.class, View2.class})
            @JsonWrapped("wrapped")
            public int y = 4711;
            
            @JsonView(View2.class)
            @JsonWrapped("wrapped")
            public int z = 10;
        }
        
        String result = mapper.writerWithView(View2.class)
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711,\"z\":10}}", result);
    }
    
    @Test
    public void jsonWrapped_ommits_propertiesNotIncludedInView() throws JsonProcessingException {
        @JsonView(DefaultView.class)
        class FieldClass {
            public int x = 42;
            
            @JsonView(View.class)
            @JsonWrapped("wrapped")
            public int y = 4711;
            
            @JsonView(DefaultView.class)
            @JsonWrapped("wrapped")
            public int z = 10;
        }
        
        String result = mapper.writerWithView(DefaultView.class)
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"z\":10}}", result);
    }
    
    @Test
    public void jsonWrapped_virtualProperty_isConsideredByFilter() throws JsonProcessingException {
        @JsonFilter("filter")
        class FieldClass {
            public int x = 42;
            
            @JsonWrapped("wrapped")
            public int y = 4711;
        }
        
        String result = mapper.setFilterProvider(new SimpleFilterProvider().addFilter("filter", SimpleBeanPropertyFilter.serializeAllExcept("wrapped")))
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42}", result);
    }
    
    @Test
    public void jsonWrapped_warps_propertiesIncludedByClassFilter() throws JsonProcessingException {
        @JsonFilter("filter")
        class FieldClass {
            public int x = 42;
            
            @JsonWrapped("wrapped")
            public int y = 4711;
        }
        
        String result = mapper.setFilterProvider(new SimpleFilterProvider().addFilter("filter", SimpleBeanPropertyFilter.serializeAllExcept("x")))
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_ommits_propertiesExcludedByClassFilter() throws JsonProcessingException {
        @JsonFilter("filter")
        class FieldClass {
            public int x = 42;
            
            @JsonWrapped("wrapped")
            public int y = 4711;
            
            @JsonWrapped("wrapped")
            public int z = 10;
        }
        
        String result = mapper.setFilterProvider(new SimpleFilterProvider().addFilter("filter", SimpleBeanPropertyFilter.serializeAllExcept("x", "z")))
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"wrapped\":{\"y\":4711}}", result);
    }
    
    
    // TODO: anygetter
    // TODO: propertyorder
}

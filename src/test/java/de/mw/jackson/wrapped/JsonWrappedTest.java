package de.mw.jackson.wrapped;


import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;


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
            @JsonProperty("yy")
            int y = 4711;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"yy\":4711}}", result);
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
    public void jsonWrapped_warps_renamedproperty_atGetterLevel() throws JsonProcessingException {
        class PropertyClass {
            int x = 42;
            int y = 4711;
            
            public int getX() {return x;}
            
            @JsonWrapped("wrapped")
            @JsonProperty("yy")
            public int getY() {return y;}
        }
        
        String result = mapper.writeValueAsString(new PropertyClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"yy\":4711}}", result);
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
    
    @Test
    public void jsonWrapped_wraps_nestedBean() throws JsonProcessingException {
        class NestedFieldClass {
            public int y = 4711;
        };
        class FieldClass {
            public int x = 42;
            
            @JsonWrapped("wrapped")
            public NestedFieldClass nested = new NestedFieldClass();
        };
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"nested\":{\"y\":4711}}}", result);
    }
    
    @Test
    public void jsonWrapped_wraps_propertiesOfNestedBean() throws JsonProcessingException {
        class NestedFieldClass {
            public int x = 42;
            
            @JsonWrapped("wrapped")
            public int y = 4711;
        };
        class FieldClass {
            public NestedFieldClass nested = new NestedFieldClass();
        };
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"nested\":{\"x\":42,\"wrapped\":{\"y\":4711}}}", result);
    }
    
    public void jsonWrapped_wraps_propertiesOfanygetter() throws JsonProcessingException {
        class FieldClass {
            public int x = 42;
            
            private Map<String, Object> anygetter = new LinkedHashMap<String, Object>();
            
            @JsonAnyGetter
            @JsonWrapped("wrapped")
            public Map<String, Object> getAny() {
                return anygetter;
            }
        }
        FieldClass value = new FieldClass();
        value.anygetter.put("y", 4711);
        
        String result = mapper.writeValueAsString(value);
        
        assertEquals("{\"wrapped\":{\"y\":4711}}", result);
    }
    
    
    
    //
    // type level tests
    //
    
    
    
    @Test
    public void jsonWrapped_atTypeLevel_warps_propertyField() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = "yy")
        class FieldClass {
            @JsonProperty
            int x = 42;
            
            @JsonProperty("yy")
            int y = 4711;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"yy\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_ignores_unserializedPropertyField() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = "y")
        class FieldClass {
            @JsonProperty
            int x = 42;
            int y = 4711;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_warps_publicPropertyField() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = "y")
        class FieldClass {
            @JsonProperty
            int x = 42;
            public int y = 4711;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_warps_property() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = "y")
        class PropertyClass {
            int x = 42;
            int y = 4711;
            
            public int getX() {return x;}
            public int getY() {return y;}
        }
        
        String result = mapper.writeValueAsString(new PropertyClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_warps_renamedproperty_atGetterLevel() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = "yy")
        class PropertyClass {
            int x = 42;
            int y = 4711;
            
            public int getX() {return x;}
            
            @JsonProperty("yy")
            public int getY() {return y;}
        }
        
        String result = mapper.writeValueAsString(new PropertyClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"yy\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_warps_combined_multipleProperties() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = {"y", "z"})
        class FieldClass {
            public int x = 42;
            public int y = 4711;
            public int z = 10;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711,\"z\":10}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_warps_mixed_multipleProperties() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = {"y", "z"})
        class FieldClass {
            public int x = 42;
            public int y = 4711;
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
    public void jsonWrapped_atTypeLevel_andPropertyLevel_warps_and_combines_multipleProperties() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = {"y", "z"})
        class FieldClass {
            public int x = 42;
            public int y = 4711;
            public int z = 10;
            @JsonWrapped("wrapped")
            public int a = 1;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711,\"z\":10,\"a\":1}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_hasLowerPriorityThan_atPropertyLevel() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = {"y", "z"})
        class FieldClass {
            public int x = 42;
            public int y = 4711;
            @JsonWrapped("foo")
            public int z = 10;
        }
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711},\"foo\":{\"z\":10}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_ignores_propertiesNotIncludedInView() throws JsonProcessingException {
        @JsonView(DefaultView.class)
        @JsonWrapped(value = "wrapped", properties = "y")
        class FieldClass {
            public int x = 42;
            
            @JsonView(View.class)
            public int y = 4711;
        }
        
        String result = mapper.writerWithView(DefaultView.class)
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_wraps_propertiesIncludedInView() throws JsonProcessingException {
        @JsonView(DefaultView.class)
        @JsonWrapped(value = "wrapped", properties = "y")
        class FieldClass {
            public int x = 42;
            
            @JsonView(View.class)
            public int y = 4711;
        }
        
        String result = mapper.writerWithView(View.class)
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_wraps_propertiesIncludedInMultipleViews() throws JsonProcessingException {
        @JsonView(DefaultView.class)
        @JsonWrapped(value = "wrapped", properties = {"y", "z"})
        class FieldClass {
            public int x = 42;
            
            @JsonView({View.class, View2.class})
            public int y = 4711;
            
            @JsonView(View2.class)
            public int z = 10;
        }
        
        String result = mapper.writerWithView(View2.class)
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"y\":4711,\"z\":10}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_ommits_propertiesNotIncludedInView() throws JsonProcessingException {
        @JsonView(DefaultView.class)
        @JsonWrapped(value = "wrapped", properties = {"y", "z"})
        class FieldClass {
            public int x = 42;
            
            @JsonView(View.class)
            public int y = 4711;
            
            @JsonView(DefaultView.class)
            public int z = 10;
        }
        
        String result = mapper.writerWithView(DefaultView.class)
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"z\":10}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_virtualProperty_isConsideredByFilter() throws JsonProcessingException {
        @JsonFilter("filter")
        @JsonWrapped(value = "wrapped", properties = "y")
        class FieldClass {
            public int x = 42;
            public int y = 4711;
        }
        
        String result = mapper.setFilterProvider(new SimpleFilterProvider().addFilter("filter", SimpleBeanPropertyFilter.serializeAllExcept("wrapped")))
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_warps_propertiesIncludedByClassFilter() throws JsonProcessingException {
        @JsonFilter("filter")
        @JsonWrapped(value = "wrapped", properties = "y")
        class FieldClass {
            public int x = 42;
            public int y = 4711;
        }
        
        String result = mapper.setFilterProvider(new SimpleFilterProvider().addFilter("filter", SimpleBeanPropertyFilter.serializeAllExcept("x")))
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_ommits_propertiesExcludedByClassFilter() throws JsonProcessingException {
        @JsonFilter("filter")
        @JsonWrapped(value = "wrapped", properties = {"y", "z"})
        class FieldClass {
            public int x = 42;
            public int y = 4711;
            public int z = 10;
        }
        
        String result = mapper.setFilterProvider(new SimpleFilterProvider().addFilter("filter", SimpleBeanPropertyFilter.serializeAllExcept("x", "z")))
                              .writeValueAsString(new FieldClass());
        
        assertEquals("{\"wrapped\":{\"y\":4711}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_wraps_nestedBean() throws JsonProcessingException {
        class NestedFieldClass {
            public int y = 4711;
        };
        @JsonWrapped(value = "wrapped", properties = "nested")
        class FieldClass {
            public int x = 42;
            public NestedFieldClass nested = new NestedFieldClass();
        };
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"x\":42,\"wrapped\":{\"nested\":{\"y\":4711}}}", result);
    }
    
    @Test
    public void jsonWrapped_atTypeLevel_wraps_propertiesOfNestedBean() throws JsonProcessingException {
        @JsonWrapped(value = "wrapped", properties = "y")
        class NestedFieldClass {
            public int x = 42;
            public int y = 4711;
        };
        class FieldClass {
            public NestedFieldClass nested = new NestedFieldClass();
        };
        
        String result = mapper.writeValueAsString(new FieldClass());
        
        assertEquals("{\"nested\":{\"x\":42,\"wrapped\":{\"y\":4711}}}", result);
    }
}

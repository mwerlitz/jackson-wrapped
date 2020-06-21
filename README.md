# @JsonWrapped for Jackson
A conterpart for `@JsonUnwrapped` for serialization.

Annotation used to indicate that a property should be serialized wrapped in a virtual object; that is, if it would be serialized it is instead included as a property of the virtual object.

In other words, it does create a virtual property of a JSON object and moves the original property into it.

No need for artificial wrapping objects or custom serializer quirks. 
## Usage (Example from @JsonUnwrapped)

For example, consider case of POJO like:

    public class Parent {
        public int age;
        public String first, last;
    }
which would normally be serialized as follows:

    {
        "age": 18,
        "first": "Joey",
        "last": "Sixpack"
    }
can be changed to this:

    {
        "age": 18,
        "name": {
          "first": "Joey",
          "last": "Sixpack"
        }
    }
by changing Parent class to:

    public class Parent {
        public int age;
        @JsonWrapped("name")
        public String first, last;
    }
or

    @JsonWrapped(value="name", properties={"first","last"})
    public class Parent {
        public int age;
        public String first, last;
    }
## Configuration
Simply register the annotation with a module:

    new ObjectMapper().registerModule(new JsonWrappedModule());

## Features

 - `@JsonWrapped` annotations can be placed at property (field or method) level and type (class) level 
 - `@JsonWrapped` annotations at property and type (class) level can be combined, configuration on properties has precedence
 - multiple `@JsonWrapped` annotations with different names for the virtual property can be used, resulting in multiple virtual objects:

        @JsonWrapped(value="name", properties={"first","last})
        public class Parent {
            public int age;
            @JsonWrapped("name")
            public String first, last;
            @JsonWrapped("address")
            public String street, city;
        }
    would be serialized as
    
        {
            "age" : 18,
            "name": {
                "first": "Joey",
                "last": "Sixpack"
            },
            "address": {
                "street": "Sunset boulevard",
                "city": "Heaven"
            }
        }
- respects the standard annotations like `@JsonProperty`, `@JsonIgnore`, ...
- respects views via `@JsonView` on properties
- supports views on the created virtual property via `@JsonWrapped(value="myVirtualProp",views={View.class})`, views of multiple annotations with the same name are combined, configuration on properties has precedence
- supports filtering of properties by `@JsonFilter` and the created virtual properties
- supports wrapping of properties produced by `@AnyGetter` 


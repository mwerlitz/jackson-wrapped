package de.mw.jackson.wrapped;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to wrap selected properties in virtual property (wrapper object).
 * Virtual properties will be appended to the existing bean properties.
 * Wrapped properties will be removed from the existing bean properties.
 * 
 * Configurations on type level have lower priority than on property level (field or method).
 * 
 * Limitation: virtual properties will no be configurable via {@link JsonPropertyOrder}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
public @interface JsonWrapped {
    
    /**
     * Name of the virtual property
     */
    String value();
    
    /**
     * When specified on type level, the names of the wrapped properties
     */
    String[] properties() default {};
    
}
package com.anjunar.json.mapper.annotations;

import com.anjunar.json.mapper.converter.JacksonJsonConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UseConverter {

    Class<? extends JacksonJsonConverter> value() default JacksonJsonConverter.class;

}

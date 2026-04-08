package com.anjunar.json.mapper.macros

import com.anjunar.json.mapper.schema.{DefaultRule, VisibilityRule}
import com.anjunar.json.mapper.schema.property.Property
import _root_.reflect.macros.PropertySupport

import scala.collection.mutable

object PropertyMacrosHelper {

  inline def describeProperties[E]: mutable.LinkedHashMap[String, Property[E, Any]] =
    val propertiesWithAccessors = PropertySupport.extractPropertiesWithAccessors[E]
    val properties = mutable.LinkedHashMap[String, Property[E, Any]]()
    
    propertiesWithAccessors.foreach { propertyWithAccessor =>
      val property = new Property[E, Any](
        propertyWithAccessor.accessor, 
        propertyWithAccessor.descriptor,
        classOf[DefaultRule[E]],
      )
      properties.put(propertyWithAccessor.descriptor.name, property)
    }
    
    properties

}

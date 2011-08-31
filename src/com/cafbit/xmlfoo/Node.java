/*
 * Copyright 2011 David Simmons
 * http://cafbit.com/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * 
 */
package com.cafbit.xmlfoo;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import com.cafbit.xmlfoo.annotations.Attribute;
import com.cafbit.xmlfoo.annotations.Discriminator;
import com.cafbit.xmlfoo.annotations.ElementTag;
import com.cafbit.xmlfoo.annotations.LameCrypt;
import com.cafbit.xmlfoo.annotations.Singleton;
import com.cafbit.xmlfoo.annotations.Tag;

class Node implements Comparable<Node> {
    
    private static final String DEFAULT_ELEMENT_TAG = "element";

    private Field field = null;
    private Node parentNode = null;
    public  Node childNode = null;
    private Type parameterType = null;
    private String elementTag = null;

    public String tag;
    public Class<?> type;
    public Type genericType;
    public boolean isAttribute = false;
    public boolean isPrimitive = false;
    public boolean isPrimitiveOrBoxed = false;
    public boolean isSingleton = false;
    public boolean isLameCrypt = false;
    
    public enum Context {
        ROOT,
        FIELD,
        ELEMENT // collection element, that is.  (as opposed to an XML element...)
    };
    public Node.Context context;
    
    public enum Category {
        // primitive types, boxed types, and strings
        SCALAR,
        // collections and arrays
        COLLECTION,
        // everything else
        OBJECT
    };
    public Node.Category category;
    
    // construct a field node
    public Node(Field field) throws XmlFooException {
        initField(field);
    }
    
    // construct a collection element node
    public Node(Node parentNode, Type type) throws XmlFooException {
        this.parentNode = parentNode;
        initElement(type);
    }
    
    // construct a root node
    public Node(String baseTag, String elementTag, Class<?> type, Type parameterType) throws XmlFooException {
        initRoot(baseTag, elementTag, type, parameterType);
    }
    
    private void initField(Field field) throws XmlFooException {
        this.context = Context.FIELD;
        
        // process field annotations
        
        if (field.isAnnotationPresent(Tag.class)) {
            this.tag = field.getAnnotation(Tag.class).value();
        } else {
            // TODO
            this.tag = field.getName().toLowerCase();
        }
        if (field.isAnnotationPresent(Attribute.class)) {
            this.isAttribute = true;
            String attributeName = field.getAnnotation(Attribute.class).value();
            if ((attributeName != null) && (attributeName.length() > 0)) {
                this.tag = attributeName;
            }
        }
        if (field.isAnnotationPresent(Discriminator.class)) {
            this.isAttribute = true;
            this.tag = "class";
        }
        if (field.isAnnotationPresent(Singleton.class)) {
            this.isSingleton = true;
        }
        if (field.isAnnotationPresent(LameCrypt.class)) {
            this.isLameCrypt = true;
        }

        // extract field type
        
        this.field = field;
        this.type = field.getType();
        this.genericType = field.getGenericType();

        // interpret type
        init();
    }
    
    private void initElement(Type genericType) throws XmlFooException {
        this.context = Context.ELEMENT;
        
        // process annotations
        if (this.parentNode.field != null) {
            Field field = this.parentNode.field;
            if (field.isAnnotationPresent(ElementTag.class)) {
                this.tag = field.getAnnotation(ElementTag.class).value();
            } else {
                this.tag = DEFAULT_ELEMENT_TAG;
            }
        } else {
            this.tag = DEFAULT_ELEMENT_TAG;
        }

        // extract element type
        this.type = getRawType(genericType);
        this.genericType = genericType;
        
        // interpret type
        init();
    }
    
    private void initRoot(String baseTag, String elementTag, Class<?> cls, Type parameterType) throws XmlFooException {
        this.context = Context.ROOT;
        //this.genericType = genericType;
        //this.type = getRawType(genericType);
        this.genericType = cls;
        this.type = cls;
        this.parameterType = parameterType;
        this.elementTag = elementTag;
        this.tag = baseTag;
        
        init();
    }

    private void init() throws XmlFooException {
        if (type.isPrimitive()) {
            isPrimitive = true;
        }
        if (isPrimitive || isBoxedType(type)) {
            isPrimitiveOrBoxed = true;
        }
        
        // categorize this node's type
        if (isScalarType(type)) {
            this.category = Category.SCALAR;
        } else if (Collection.class.isAssignableFrom(type)) {
            this.category = Category.COLLECTION;
            if ((parameterType == null) && (genericType instanceof ParameterizedType)) {
                ParameterizedType ptype = (ParameterizedType)genericType;
                Type[] ptypes = ptype.getActualTypeArguments();
                if (ptypes.length != 1) {
                    throw new XmlFooException("Collections must have exactly one type parameter.");
                }
                this.parameterType = ptypes[0];
                if (! (this.parameterType instanceof Class<?>)) {
                    throw new XmlFooException("Collections must be parameterized with a simple class type.");
                }
            }
            if (parameterType != null) {
                // create a sub-node for the parameterized element type
                this.childNode = new Node(this, parameterType);
                if (elementTag != null) {
                    childNode.tag = elementTag;
                }
            } else {
                throw new XmlFooException("Collection type must be parameterized with a simple type: "+type);
            }
        } else if (type.isArray()) {
            this.category = Category.COLLECTION;
            // create a sub-node for the array type
            this.childNode = new Node(this, type.getComponentType());
            if (elementTag != null) {
                childNode.tag = elementTag;
            }
        } else {
            this.category = Category.OBJECT;
        }
    }
    
    private static Class<?> getRawType(Type type) throws XmlFooException {
        if (type instanceof Class<?>) {
            return (Class<?>)type;
        } else if (type instanceof ParameterizedType) {
            // recurse
            return getRawType(((ParameterizedType)type).getRawType());
        } else {
            throw new XmlFooException("cannot determine raw type");
        }
    }

    public void setField(Object object, Object value) throws IllegalArgumentException, IllegalAccessException, XmlFooException {
        if (field == null) {
            throw new XmlFooException("Attempt to set field value on a non-field node \""+this+"\".");
        }
        if ((value == null) && field.getType().isPrimitive()) {
            throw new XmlFooException("Attempt to assign null to a primitive field.");
        }
        field.set(object, value);
    }
    
    private static boolean isScalarType(Class<?> type) {
        if (type.isPrimitive() || type.isAssignableFrom(String.class) || type.isEnum() || isBoxedType(type)) {
            return true;
        } else {
            return false;
        }
    }
    
    private static boolean isBoxedType(Class<?> type) {
        if (    type.equals(Byte.class) ||
                type.equals(Short.class) ||
                type.equals(Integer.class) ||
                type.equals(Long.class) ||
                type.equals(Float.class) ||
                type.equals(Double.class) ||
                type.equals(Boolean.class) ||
                type.equals(Character.class) ) {
            return true;
        } else {
            return false;
        }
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (parentNode != null) {
            sb.append(parentNode.toString());
            sb.append(" -> ");
        }
        
        switch (this.context) {
        case ROOT:
            sb.append("Root("+this.tag+")");
            break;
        case FIELD:
            sb.append("Field("+type.getSimpleName()+" "+field.getName()+")");
            break;
        case ELEMENT:
            sb.append("Element("+type.getSimpleName()+")");
            break;
        }
        return sb.toString();
    }

    @Override
    public int compareTo(Node other) {
        return this.tag.compareTo(other.tag);
    }

}

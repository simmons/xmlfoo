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

package com.cafbit.xmlfoo;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.cafbit.xmlfoo.annotations.SingletonCode;

public class XmlFoo {
    
    // this map holds a set of candidate classes for
    // each specified non-concrete class.  these will
    // be resolved via a discriminator string.
    Map<Class<?>, Map<String,Class<?>>> discriminatorClassMap =
        new HashMap<Class<?>, Map<String,Class<?>>>();
    
    // this map holds a set of candidate singleton objects with
    // their corresponding string codes.
    Map<String, Object> singletonMap =
        new HashMap<String, Object>();
    
    public void addDiscriminatorClass(Class<?> baseClass, String discriminator, Class<?> concreteClass) {
        // look up the candidate map for this base class
        Map<String,Class<?>> classMap = discriminatorClassMap.get(baseClass);
        if (classMap == null) {
            classMap = new HashMap<String, Class<?>>();
            discriminatorClassMap.put(baseClass, classMap);
        }
        classMap.put(discriminator, concreteClass);
    }
    
    public void addSingleton(Object object) {
        SingletonCode singletonCode = object.getClass().getAnnotation(SingletonCode.class);
        if (singletonCode != null) {
            singletonMap.put(singletonCode.value(), object);
        }
    }
    
    //// deserialize
    
    public Object deserialize(String xml, Class<?> cls) throws XmlFooException {
        XmlFooDeserializer deserializer = new XmlFooDeserializer(this);
        return deserializer.deserialize(xml, cls);
    }
    
    public Object deserialize(String xml, Class<?> cls, Type parameterType) throws XmlFooException {
        XmlFooDeserializer deserializer = new XmlFooDeserializer(this);
        return deserializer.deserialize(xml, cls, parameterType);
    }
    
    //// serialize
    
    public String serialize(String baseTag, Object object) throws XmlFooException {
        XmlFooSerializer serializer = new XmlFooSerializer(this);
        return serializer.serialize(baseTag, object);
    }
    
    public String serialize(String baseTag, String elementTag, Object object, Type parameterType) throws XmlFooException {
        XmlFooSerializer serializer = new XmlFooSerializer(this);
        return serializer.serialize(baseTag, elementTag, object, parameterType);
    }
    
    //// LameCrypt
    
    String lameEncrypt(String plaintext) {
        return DES.lameEncrypt(plaintext, null);
    }
    
    String lameDecrypt(String ciphertext) {
        return DES.lameDecrypt(ciphertext, null);
    }
}

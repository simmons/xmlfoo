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

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.xmlpull.v1.XmlSerializer;

import com.cafbit.xmlfoo.annotations.SingletonCode;

import android.util.Xml;

public class XmlFooSerializer {
    
    private XmlFoo xmlFoo;
    
    public XmlFooSerializer(XmlFoo xmlFoo) {
        this.xmlFoo = xmlFoo;
    }
    
    //// serialize
    
    public String serialize(String baseTag, Object object) throws XmlFooException {
        try {
            return serializeToXmlDocument(baseTag, null, object, null);
        } catch (Exception e) {
            if (e instanceof XmlFooException) {
                throw (XmlFooException)e;
            } else {
                throw new XmlFooException(e);
            }
        }
    }
    
    public String serialize(String baseTag, String elementTag, Object object, Type parameter) throws XmlFooException {
        try {
            return serializeToXmlDocument(baseTag, elementTag, object, parameter);
        } catch (Exception e) {
            if (e instanceof XmlFooException) {
                throw (XmlFooException)e;
            } else {
                throw new XmlFooException(e);
            }
        }
    }
    
    private String serializeToXmlDocument(String baseTag, String elementTag, Object object, Type parameter) throws Exception {
        XmlSerializer xs = new PrettyPrintXmlSerializer(Xml.newSerializer());
        StringWriter writer = new StringWriter();

        xs.setOutput(writer);
        xs.startDocument("UTF-8", null);
        serializeValueAsElement(xs, new Node(baseTag, elementTag, object.getClass(), parameter), object);
        xs.endDocument();

        return writer.toString();
    }
    
    private void serializeValueAsElement(XmlSerializer xs, Node node, Object value) throws Exception {
        xs.startTag("", node.tag);
        if (value == null) {
            xs.attribute("", "null", "true");
        } else if (node.isSingleton) {
            SingletonCode singletonCode = value.getClass().getAnnotation(SingletonCode.class);
            if (singletonCode == null) {
                throw new XmlFooException("@Singleton on field, but no @SingletonCode on the referenced class!");
            }
            xs.text(singletonCode.value());
        } else if (node.isPrimitive || (value instanceof String)) {
            if (node.isLameCrypt) {
                xs.text(xmlFoo.lameEncrypt(value.toString()));
            } else {
                xs.text(value.toString());
            }
        } else if (value instanceof Collection<?>) {
            for (Object o : ((Collection<?>)value)) {
                serializeValueAsElement(xs, node.childNode, o);
            }
        } else if (node.type.isArray()) {
            for (Object o : ((Object[])value)) {
                //serializeValueAsElement(xs, new Node(node.collectionParameterTag, o), o);
                serializeValueAsElement(xs, node.childNode, o);
            }
        } else if (node.type.isEnum()) {
            xs.text(((Enum<?>)value).name());
        } else {
            serializeObject(xs, value);
        }
        xs.endTag("", node.tag);
    }
    
    private void serializeValueAsAttribute(XmlSerializer xs, Node node, Object value) throws Exception {
        if (value == null) {
            // a null attribute means it is completely omitted.
            return;
        }
        String text;
        if (node.isSingleton) {
            Class<?> vclass = value.getClass();
            SingletonCode singletonCode = value.getClass().getAnnotation(SingletonCode.class);
            if (singletonCode == null) {
                throw new XmlFooException("@Singleton on field, but no @SingletonCode on the referenced class!");
            }
            text = singletonCode.value();
        } else if (node.isPrimitive || (value instanceof String)) {
            if (node.isLameCrypt) {
                text = xmlFoo.lameEncrypt(value.toString());
            } else {
                text = value.toString();
            }
        } else if (node.type.isEnum()) {
            text = ((Enum<?>)value).name();
        } else if (value instanceof Collection<?>) {
            throw new XmlFooException("A collection cannot be used as an attribute value.");
        } else if (node.type.isArray()) {
            throw new XmlFooException("An array cannot be used as an attribute value.");
        } else {
            throw new XmlFooException("An object cannot be used as an attribute value.");
        }
        xs.attribute("", node.tag, text);
    }
    
    private void serializeObject(XmlSerializer xs, Object object) throws Exception {
        Field[] fields = object.getClass().getFields();

        // maybe instead use LinkedHashMap, and use the Field[] ordering?
        Map<Node, Object> attributeNodes = new TreeMap<Node,Object>();
        Map<Node, Object> elementNodes = new TreeMap<Node,Object>();
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            Object value = field.get(object);
            Node node = new Node(field);
            if (node.isAttribute) {
                attributeNodes.put(node, value);
            } else {
                elementNodes.put(node, value);
            }
        }
        
        for (Map.Entry<Node, Object> entry : attributeNodes.entrySet()) {
            serializeValueAsAttribute(xs, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Node, Object> entry : elementNodes.entrySet()) {
            serializeValueAsElement(xs, entry.getKey(), entry.getValue());
        }
    }
}

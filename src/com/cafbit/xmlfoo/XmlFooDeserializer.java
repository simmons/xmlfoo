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

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Xml;

public class XmlFooDeserializer {
    
    private XmlFoo xmlFoo;
    
    public XmlFooDeserializer(XmlFoo xmlFoo) {
        this.xmlFoo = xmlFoo;
    }
    
    public Object deserialize(String xml, Class<?> cls) throws XmlFooException {
        try {
            return deserializeFromXmlDocument(xml, cls, null);
        } catch (Exception e) {
            if (e instanceof XmlFooException) {
                throw (XmlFooException)e;
            } else {
                throw new XmlFooException(e);
            }
        }
    }

    public Object deserialize(String xml, Class<?> cls, Type parameterType) throws XmlFooException {
        try {
            return deserializeFromXmlDocument(xml, cls, parameterType);
        } catch (Exception e) {
            if (e instanceof XmlFooException) {
                throw (XmlFooException)e;
            } else {
                throw new XmlFooException(e);
            }
        }
    }

    private Object deserializeFromXmlDocument(String xml, Class<?> cls, Type parameterType) throws Exception {
        XmlPullParser xpp = Xml.newPullParser();

        xpp.setInput(new StringReader(xml));
        int eventType = xpp.getEventType();
        
        Object object = null;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (object != null) {
                    throw new XmlFooException("More than one root element not supported!");
                }
                Node node = new Node(xpp.getName(), null, cls, parameterType);
                if (node.category == Node.Category.OBJECT) {
                    object = deserializeObject(xpp, node);
                } else if (node.category == Node.Category.COLLECTION) {
                    object = deserializeCollection(xpp, node);
                }
            }
            eventType = xpp.next();
        }

        return object;
    }
    
    private void skipToEndTag(XmlPullParser xpp) throws XmlPullParserException, IOException {
        int level = 0;
        while (true) {
            int eventType = xpp.next();
            if (eventType == XmlPullParser.START_TAG) {
                level++;
            } else if (eventType == XmlPullParser.END_TAG) {
                if (level == 0) {
                    return;
                } else {
                    level--;
                }
            } else if (eventType == XmlPullParser.END_DOCUMENT) {
                return;
            }
        }
    }
    
    private Object deserializeObject(XmlPullParser xpp, Node node) throws Exception {
        //Class<?> cls = node.type;
        
        // process attributes
        boolean isNull = false;
        String discriminator = null;
        Map<String,String> attributes = new HashMap<String,String>();
        for (int i=0; i<xpp.getAttributeCount(); i++) {
            String name = xpp.getAttributeName(i);
            String value = xpp.getAttributeValue(i);
            attributes.put(name, value);
            
            if (name.equals("null")) {
                if ((! value.equals("false")) && (! value.equals("0"))) {
                    isNull = true;
                }
            } else if (name.equals("class")) {
                discriminator = value;
            }
        }
        if (isNull) {
            // easy peasy!
            skipToEndTag(xpp);
            return null;
        }

        // determine which class to instantiate
        Class<?> cls = node.type;
        if (discriminator != null) {
            Map<String,Class<?>> classMap = xmlFoo.discriminatorClassMap.get(node.type);
            if (classMap == null) {
                throw new XmlFooException("cannot match discriminator \""+discriminator+"\" to concrete class.");
            }
            cls = classMap.get(discriminator);
            if (cls == null) {
                throw new XmlFooException("cannot match discriminator \""+discriminator+"\" to concrete class.");               
            }
        }
        
        // if this is a basic Collection interface, replace it with
        // a reasonable concrete class.
        // (we only support List for now.)
        if (cls.equals(List.class)) {
            cls = ArrayList.class;
        }
        
        // determine if we can instantiate this class
        if (Modifier.isAbstract(cls.getModifiers()) || cls.isInterface()) {
            throw new XmlFooException("cannot instantiate class "+node.type.getName());
        }
        
        Object object = cls.newInstance();

        // build the node map
        Map<String,Node> nodeMap = new HashMap<String,Node>();
        for (Field field : cls.getFields()) {
            Node n = new Node(field);
            nodeMap.put(n.tag, n);
        }
        
        // consume attributes...
        for (Entry<String,String> entry : attributes.entrySet()) {
            Node n = nodeMap.get(entry.getKey().toLowerCase());
            if ((n != null) && n.isAttribute) {
                if (n.isSingleton) {
                    n.setField(object, xmlFoo.singletonMap.get(entry.getValue()));
                } else if (n.category == Node.Category.SCALAR) {
                    Object scalar = deserializeScalarValue(n, entry.getValue());
                    n.setField(object, scalar);
                } else {
                    throw new XmlFooException("attributes must be of scalar value.");
                }
            }
        }
        
        // consume tags
        while (true) {
            int eventType = xpp.next();
            if (eventType == XmlPullParser.START_TAG) {
                Node n = nodeMap.get(xpp.getName().toLowerCase());
                if (n != null && (! n.isAttribute)) {
                    n.setField(object, deserializeItem(xpp,n));
                } else {
                    skipToEndTag(xpp);
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                break;
            }
        }
        
        return object;
    }
    
    private Object deserializeItem(XmlPullParser xpp, Node node) throws Exception {
        Object childObject = null;
        
        switch (node.category) {
        case SCALAR:
            childObject = deserializeScalarElement(xpp, node);
            break;
        case COLLECTION:
            childObject = deserializeCollection(xpp, node);
            break;
        case OBJECT:
            childObject = deserializeObject(xpp, node);
            break;
        }
        return childObject;
    }
    
    private Object deserializeCollection(XmlPullParser xpp, Node node) throws Exception {
        List<Object> list = new ArrayList<Object>();
        
        while (true) {
            int eventType = xpp.next();
            if (eventType == XmlPullParser.TEXT) {
                if (! xpp.isWhitespace()) {
                    throw new XmlFooException("Non-whitespace text found at the collection level.  Expected elements.");
                }
            } else if (eventType == XmlPullParser.START_TAG) {
                Object object = deserializeItem(xpp, node.childNode);
                list.add(object);
            } else if (eventType == XmlPullParser.END_TAG) {
                // return the populated collection or array
                if (node.type.isArray()) {
                    Object array = Array.newInstance(node.type.getComponentType(), list.size());
                    for (int i=0; i<list.size(); i++) {
                        Array.set(array, i, list.get(i));
                    }
                    return array;
                } else {
                    return list;
                }
            }
        }
        
    }
    
    private Object deserializeScalarElement(XmlPullParser xpp, Node node) throws XmlPullParserException, IOException, XmlFooException {
        while (true) {
            int eventType = xpp.next();
            if (eventType == XmlPullParser.TEXT) {
                if (! xpp.isWhitespace()) {
                    if (node.category == Node.Category.SCALAR) {
                        Object object = deserializeScalarValue(node, xpp.getText());
                        skipToEndTag(xpp);
                        return object;
                    } else {
                        throw new XmlFooException("Trying to assign a scalar value \""+xpp.getText()+"\" to a non-scalar node \""+node+"\".");
                    }
                }
            } else if (eventType == XmlPullParser.START_TAG) {
                throw new XmlFooException("Trying to assign a non-scalar value \""+xpp.getText()+"\" to a scalar node \""+node+"\".");
            } else if (eventType == XmlPullParser.END_TAG) {
                // my (premature!) end tag
                return null;
            }
        }
    }
    
    private Object deserializeScalarValue(Node node, String text) throws XmlFooException {
        if (node.type.isEnum()) {
            return Enum.valueOf(((Class<Enum>)(node.type)), text);
        } if (node.type.isAssignableFrom(String.class)) {
            if (node.isLameCrypt) {
                return xmlFoo.lameDecrypt(text);
            } else {
                return text;
            }
        } else if (node.isPrimitiveOrBoxed) {
            if (node.type.equals(Byte.class) || (node.type.equals(byte.class))) {
                return Byte.parseByte(text);
            } else if (node.type.equals(Short.class) || (node.type.equals(short.class))) {
                return Short.parseShort(text);
            } else if (node.type.equals(Integer.class) || (node.type.equals(int.class))) {
                return Integer.parseInt(text);
            } else if (node.type.equals(Long.class) || (node.type.equals(long.class))) {
                return Long.parseLong(text);
            } else if (node.type.equals(Float.class) || (node.type.equals(float.class))) {
                return Float.parseFloat(text);
            } else if (node.type.equals(Double.class) || (node.type.equals(double.class))) {
                return Double.parseDouble(text);
            } else if (node.type.equals(Boolean.class) || (node.type.equals(boolean.class))) {
                if (text.equalsIgnoreCase("true") || text.equals("1")) {
                    return true;
                } else {
                    return false;
                }
            } else if (node.type.equals(Character.class) || (node.type.equals(char.class))) {
                if (text.length() == 0) {
                    // this will result in an NPE, but I don't think
                    // there's any other reasonable way to handle this
                    // oddball case.
                    return null;
                } else {
                    return text.charAt(0);
                }
            } else {
                // this never happens.
                return null;
            }
        } else {
            throw new XmlFooException("Trying to assign a non-scalar node \""+node+"\" a scalar value \""+text+"\".");
        }
    }
}

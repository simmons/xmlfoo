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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import org.xmlpull.v1.XmlSerializer;

class PrettyPrintXmlSerializer implements XmlSerializer {
    
    private static final String INDENTATION = "    ";
    private static final int MAX_DEPTH = 80;
    
    private int depthOfFlatness = MAX_DEPTH;
    private XmlSerializer xs;
    private int lastDepth = 0;
    
    public PrettyPrintXmlSerializer(XmlSerializer xs) {
        this.xs = xs;
    }
    
    private void indent(int offset) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        for (int i=0; i<(xs.getDepth()+offset); i++) {
            sb.append(INDENTATION);
        }
        xs.ignorableWhitespace(sb.toString());      
    }
    
    // methods with prettyprinting logic

    @Override
    public XmlSerializer startTag(String namespace, String name)
            throws IOException, IllegalArgumentException,
            IllegalStateException {
        if (xs.getDepth() < depthOfFlatness) {
            indent(0);
        }
        return xs.startTag(namespace, name);
    }

    @Override
    public XmlSerializer endTag(String namespace, String name)
            throws IOException, IllegalArgumentException,
            IllegalStateException {
        int depth = xs.getDepth();
        
        if (depth < depthOfFlatness) {
            if (lastDepth != depth)
                indent(-1);
        }
        XmlSerializer xsret = xs.endTag(namespace, name);
        
        if (xs.getDepth() < depthOfFlatness) {
            depthOfFlatness = MAX_DEPTH;
        }
        
        lastDepth = depth;
        
        return xsret;
    }

    @Override
    public XmlSerializer text(String text) throws IOException,
            IllegalArgumentException, IllegalStateException {
        depthOfFlatness = xs.getDepth();
        return xs.text(text);
    }

    @Override
    public XmlSerializer text(char[] buf, int start, int len)
            throws IOException, IllegalArgumentException,
            IllegalStateException {
        depthOfFlatness = xs.getDepth();
        return xs.text(buf, start, len);
    }
    
    // simple wrapping methods

    @Override
    public XmlSerializer attribute(String namespace, String name,
            String value) throws IOException, IllegalArgumentException,
            IllegalStateException {
        return xs.attribute(namespace, name, value);
    }

    @Override
    public void cdsect(String text) throws IOException,
            IllegalArgumentException, IllegalStateException {
        xs.cdsect(text);
    }

    @Override
    public void comment(String text) throws IOException,
            IllegalArgumentException, IllegalStateException {
        xs.comment(text);
    }

    @Override
    public void docdecl(String text) throws IOException,
            IllegalArgumentException, IllegalStateException {
        xs.docdecl(text);
    }

    @Override
    public void endDocument() throws IOException, IllegalArgumentException,
            IllegalStateException {
        xs.endDocument();
    }

    @Override
    public void entityRef(String text) throws IOException,
            IllegalArgumentException, IllegalStateException {
        xs.entityRef(text);
    }

    @Override
    public void flush() throws IOException {
        xs.flush();
    }

    @Override
    public int getDepth() {
        return xs.getDepth();
    }

    @Override
    public boolean getFeature(String name) {
        return xs.getFeature(name);
    }

    @Override
    public String getName() {
        return xs.getName();
    }

    @Override
    public String getNamespace() {
        return xs.getNamespace();
    }

    @Override
    public String getPrefix(String namespace, boolean generatePrefix)
            throws IllegalArgumentException {
        return xs.getPrefix(namespace, generatePrefix);
    }

    @Override
    public Object getProperty(String name) {
        return xs.getProperty(name);
    }

    @Override
    public void ignorableWhitespace(String text) throws IOException,
            IllegalArgumentException, IllegalStateException {
        xs.ignorableWhitespace(text);
    }

    @Override
    public void processingInstruction(String text) throws IOException,
            IllegalArgumentException, IllegalStateException {
        xs.processingInstruction(text);
    }

    @Override
    public void setFeature(String name, boolean state)
            throws IllegalArgumentException, IllegalStateException {
        xs.setFeature(name, state);
    }

    @Override
    public void setOutput(Writer writer) throws IOException,
            IllegalArgumentException, IllegalStateException {
        xs.setOutput(writer);
    }

    @Override
    public void setOutput(OutputStream os, String encoding)
            throws IOException, IllegalArgumentException,
            IllegalStateException {
        xs.setOutput(os, encoding);
    }

    @Override
    public void setPrefix(String prefix, String namespace)
            throws IOException, IllegalArgumentException,
            IllegalStateException {
        xs.setPrefix(prefix, namespace);
    }

    @Override
    public void setProperty(String name, Object value)
            throws IllegalArgumentException, IllegalStateException {
        xs.setProperty(name, value);
    }

    @Override
    public void startDocument(String encoding, Boolean standalone)
            throws IOException, IllegalArgumentException,
            IllegalStateException {
        xs.startDocument(encoding, standalone);
    }
    
}
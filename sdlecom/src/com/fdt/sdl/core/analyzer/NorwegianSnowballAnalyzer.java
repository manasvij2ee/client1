package com.fdt.sdl.core.analyzer;

import org.apache.lucene.analysis.*;
import java.io.*;

import org.apache.lucene.analysis.snowball.*;


public final class NorwegianSnowballAnalyzer extends Analyzer{
    Analyzer a = new SnowballAnalyzer("Norwegian");
    public NorwegianSnowballAnalyzer(){}

    public TokenStream tokenStream(String fieldName, Reader aReader){
        return a.tokenStream(fieldName, aReader);
    }
}

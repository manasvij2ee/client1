package com.fdt.sdl.core.analyzer;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;


public final class FinnishSnowballAnalyzer extends Analyzer{
    Analyzer a = new SnowballAnalyzer("Finnish");
    public FinnishSnowballAnalyzer(){}

    public TokenStream tokenStream(String fieldName, Reader aReader){
        return a.tokenStream(fieldName, aReader);
    }
}

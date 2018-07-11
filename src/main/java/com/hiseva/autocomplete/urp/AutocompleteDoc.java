package com.hiseva.autocomplete.urp;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class AutocompleteDoc {
    static final class Phrase {
        private final String phrase;
        private final List<String> types;
        private final AtomicLong frequency;

         Phrase(String phrase, List<String> types, long frequency) {
            this.phrase = phrase;
            this.types = types;
            this.frequency = new AtomicLong(frequency);
        }
    }

    private final int version;
    private final String id;
    private final ConcurrentHashMap<String, Phrase> phrases = new ConcurrentHashMap<>();

    public AutocompleteDoc(int version, String id) {
        this.version = version;
        this.id = id;
    }

    public void add(AutocompleteDoc other) {
        if (other.version != this.version) { throw new IllegalStateException(); }
        // do other checks..
        for (Map.Entry<String, Phrase> e : other.phrases.entrySet()) {
            this.incOrInsert(e.getKey(), e.getValue());
        }
    }

    /**
     * Either inserts the following phrase or increments the existing phrase we have cached.
     *
     * @return Our new frequency value
     */
    public long incOrInsert(String key, Phrase phrase) {
        //initialize it with 0 values
        phrases.putIfAbsent(key, new Phrase(phrase.phrase, phrase.type, 0L));

        Phrase prev = phrases.get(key);
        if (! (prev.type.equals(phrase.type))) {
            throw new IllegalStateException("Prev type " + prev.type + " != new type " + phrase.type);
        }
        return prev.frequency.addAndGet(phrase.frequency.get());
    }

    public SolrInputDocument toSolrInputDoc() {
        SolrInputDocument doc = new SolrInputDocument();

        return doc;
    }

}

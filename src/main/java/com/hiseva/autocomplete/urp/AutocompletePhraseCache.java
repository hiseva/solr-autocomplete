package com.hiseva.autocomplete.urp;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AutocompletePhraseCache {
    private static final Logger LOG = LoggerFactory.getLogger(AutocompletePhraseCache.class);

    private SolrClient solrAC;

    // lock guards references, not contents
    private final ReadWriteLock phrasesGuard = new ReentrantReadWriteLock();
    private ConcurrentHashMap<String, AutocompleteDoc> phrases_live;
    private ConcurrentHashMap<String, AutocompleteDoc> phrases_committing;

    private final ScheduledExecutorService cacheCleaner = Executors.newScheduledThreadPool(1);

    AutocompletePhraseCache(SolrClient solrAC:) {
        this.solrAC = solrAC;
        this.phrases_live = new ConcurrentHashMap<>();
        cacheCleaner.schedule(new Runnable() {
            @Override
            public void run() {
                commitAll();
            }
        }, 1, TimeUnit.MINUTES);
    }

    private void commitAll() {
        if (phrases_committing != null) {
            throw new RuntimeException("Something went wrong");
        }
        // set references so updates can still happen while we flush
        Lock wl = phrasesGuard.writeLock();
        wl.lock();
        phrases_committing = phrases_live;
        phrases_live = new ConcurrentHashMap<>();
        wl.unlock();

        // flush to solr and clear phrases_committing
        ArrayList<SolrInputDocument> phraseDocs = new ArrayList<>();
        for (String phrase: phrases_committing.keySet()) {
            AutocompleteDoc phraseSolr = fetchSolrPhrase(phrase);
            phrases_committing.get(phrase).add(phraseSolr);
            phraseDocs.add(phrases_committing.get(phrase).toSolrInputDoc());
        }

        try {
            solrAC.add(phraseDocs);
            solrAC.commit(true, true);
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (Throwable thr) {
            LOG.error("Error while updating the document", thr);
        }

        // blank out phrases_committing to save time not looking it up
        wl.lock();
        phrases_committing = null;
        wl.unlock();
    }

    // returns new count for this phrase
    // TODO need to fix api to account for full AutocompleteDocs
    public long addPhrase(String docId, String phraseKey, AutocompleteDoc.Phrase phrase) {
        Lock rl = phrasesGuard.readLock();
        rl.lock();
        try {
            // try to get prev value from committing set
            AutocompleteDoc prev = null;
            if (phrases_committing != null) {
                prev = phrases_committing.get(docId);
            }

            if (prev != null) {
                // carry value forward from committing set to add to retrieved (stale) result
                this.phrases_live.putIfAbsent(docId, prev);
            } else {
                // intialize a new blank value
                this.phrases_live.putIfAbsent(docId, new AutocompleteDoc(0, docId));
            }
            // actually do our update
            prev = this.phrases_live.get(docId);
            return prev.incOrInsert(phraseKey, phrase);
        } finally {
            rl.unlock();
        }
    }

    private AutocompleteDoc fetchSolrPhrase(String phrase) {

    }

    private SolrInputDocument fetchExistingOrCreateNewSolrDoc(String id) throws SolrServerException, IOException {
        Map<String, String> p = new HashMap<String, String>();
        p.put("q", ID + ":\"" + ClientUtils.escapeQueryChars(id) + "\"");

        SolrParams params = new MapSolrParams(p);

        QueryResponse res = solrAC.query(params);

        if (res.getResults().size() == 0) {
            return new SolrInputDocument();
        } else if (res.getResults().size() == 1) {
            SolrDocument doc = res.getResults().get(0);
            SolrInputDocument tmp = new SolrInputDocument();
            tmp.addField(FREQUENCY, doc.getFieldValue(FREQUENCY));
            return tmp;
        } else {
            throw new IllegalStateException("Query with params : " + p + " returned more than 1 hit!");
        }
    }
}

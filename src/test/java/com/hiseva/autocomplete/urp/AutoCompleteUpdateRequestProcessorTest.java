package com.hiseva.autocomplete.urp;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.junit.*;
import org.junit.Assert;

import java.io.IOException;

import static com.hiseva.autocomplete.urp.AutocompleteUpdateRequestProcessor.*;

public class AutoCompleteUpdateRequestProcessorTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws IOException {
        //h.getCore().getSearcher().get().close();
    }

    @Test
    public void testAddCount() {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("frequency", 2.0F);
        addCount(doc,"frequency",900);
        Assert.assertEquals(3.0F, (float) doc.getFieldValue("frequency"), 0.000001F);
    }
}

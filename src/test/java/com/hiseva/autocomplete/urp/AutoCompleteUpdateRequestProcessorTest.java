package com.hiseva.autocomplete.urp;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.junit.*;
import org.junit.Assert;

import java.io.IOException;
import java.util.*;

import static com.hiseva.autocomplete.urp.AutocompleteUpdateRequestProcessor.*;
import static com.hiseva.autocomplete.urp.AutocompleteUpdateRequestProcessorFactory.getSolrACSchema;

public class AutoCompleteUpdateRequestProcessorTest extends SolrTestCaseJ4 {

    private static SolrClient solrAC;

    @BeforeClass
    public static void beforeTests() throws Exception {
        initCore("solrconfig.xml", "schema.xml", "solr");
        solrAC = new EmbeddedSolrServer(h.getCore());
    }

    @Test
    public void testGetSolrACSchema() throws IOException, SolrServerException {
        Map<String, Map<String, Object>> schema = getSolrACSchema(solrAC);

        Assert.assertEquals("string", (String) schema.get("id").get("type"));
        Assert.assertTrue((boolean) schema.get("type").get("multiValued"));
    }

    @Test
    public void testAddCount() {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("frequency", 2.0F);
        addCount(doc,"frequency",900);
        Assert.assertEquals(3.0F, (float) doc.getFieldValue("frequency"), 0.000001F);
    }

    @Test
    public void testAddField() throws IOException, SolrServerException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "test-1");
        doc.addField("type", "title");
        Map<String, Map<String, Object>> schema = getSolrACSchema(solrAC);

        Collection<Object> types = new HashSet<>();
        types.add("category");
        types.add("title");

        Collection<Object> ids = new HashSet<>();
        ids.add("test-2");

        addField(doc, schema, "id", ids);
        assertEquals("[test-2]", doc.getFieldValues("id").toString());

        addField(doc, schema, "type", types);
        assertEquals("[title, category]", doc.getFieldValues("type").toString());
    }
}

package com.hiseva.autocomplete.urp;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.SolrIndexSearcher;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hiseva.autocomplete.urp.AutocompleteUpdateRequestProcessorFactory.getSolrACSchema;

public class AutoCompleteUpdateRequestProcessorFactoryTest extends SolrTestCaseJ4 {

    private static SolrClient solrAC;

    @BeforeClass
    public static void beforeTests() throws Exception {
        initCore("solrconfig.xml", "schema.xml", "solr");
        solrAC = new EmbeddedSolrServer(h.getCore());
    }

    @Test
    public void testGetSolrACSchema() {
        Map<String, Map<String, Object>> schema = getSolrACSchema(solrAC);

        Assert.assertEquals("string", (String) schema.get("id").get("type"));
        Assert.assertTrue((boolean) schema.get("type").get("multiValued"));
    }
}

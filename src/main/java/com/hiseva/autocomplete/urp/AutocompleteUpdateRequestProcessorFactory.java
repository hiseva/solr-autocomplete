package com.hiseva.autocomplete.urp;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.validation.Schema;
import java.util.*;

import static org.apache.solr.client.solrj.request.schema.SchemaRequest.*;

public class AutocompleteUpdateRequestProcessorFactory extends UpdateRequestProcessorFactory implements SolrCoreAware {
    private static final Logger LOG = LoggerFactory.getLogger(AutocompleteUpdateRequestProcessorFactory.class);
    private String solrAC;
    private SolrClient solrACServer;
    private String separator;
    private SolrCore core;

    private List<String> fields = new ArrayList<String>();
    private List<Integer> fieldWeights = new ArrayList();
    private List<String> copyAsIsFields = new ArrayList<String>();
    private List<String> idFields = new ArrayList<String>();

    @Override
    @SuppressWarnings("rawtypes")
    public void init(NamedList args) {
        super.init(args);

        solrAC = (String) args.get("solrAC");

        if (solrAC.startsWith("http:")) {

            // Used when AC core is deployed on separate Solr
            this.solrACServer = new HttpSolrClient.Builder(solrAC).build();
        }

        this.separator = (String) args.get("separator");

        String fieldsStr = (String) args.get("fields");
        String fieldWeightsStr = (String) args.get("fieldWeights");
        String copyAsIsFieldsStr = (String) args.get("copyAsIsFields");
        String idFieldsStr = (String) args.get("idFields");

        if (fieldsStr == null) {
            throw new RuntimeException(
                    "Can't initialize AutocompleteUpdateRequestProcessor unless fields are specified");
        }

        StringTokenizer tok = new StringTokenizer(fieldsStr, ",");
        while (tok.hasMoreTokens()) {
            fields.add(tok.nextToken().trim());
        }

        if (fieldWeightsStr != null) {
            String [] fs = fieldWeightsStr.split(",");
            for (String f : fs) {
                fieldWeights.add(Integer.parseInt(f.trim()));
            }
        }

        if (copyAsIsFieldsStr != null) {
          String [] fs = copyAsIsFieldsStr.split(",");
          for (String f : fs) {
            copyAsIsFields.add(f.trim());
          }
        }

        if (idFieldsStr != null) {
            String [] fs = idFieldsStr.split(",");
            for (String f : fs) {
                idFields.add(f.trim());
            }
        }
    }

    @Override
    public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp,
            UpdateRequestProcessor nextURP) {
        if (this.solrACServer == null) {
            // Used with embedded Solr AC core; when AC core is deployed on same Solr and 'main index'
            this.solrACServer = new EmbeddedSolrServer(core.getCoreContainer(), solrAC);
        }

        Map<String, Map<String, Object>> schemaFields = getSolrACSchema(this.solrACServer);

        return new AutocompleteUpdateRequestProcessor(solrACServer, schemaFields, fields, fieldWeights, copyAsIsFields, idFields, separator, nextURP);
    }

    @Override
    public void inform(SolrCore core) {
        this.core = core;
    }

    static Map<String, Map<String, Object>> getSolrACSchema (SolrClient solrClient) {
        SchemaRequest.Fields schemaRequestFields = new SchemaRequest.Fields();
        SchemaResponse.FieldsResponse schemaFields = new SchemaResponse.FieldsResponse();
        try {
            schemaFields = schemaRequestFields.process(solrClient);
        } catch (Throwable thr) {
            LOG.error("error getting schema", thr);
        }

        List<Map<String, Object>> fieldsList = schemaFields.getFields();

        Map<String, Map<String, Object>> fieldsMap = new HashMap<>();
        for (Map<String, Object> f: fieldsList) {
            if (f.containsKey("name") && f.get("name") != null) {
                fieldsMap.put((String) f.remove("name"), f);
            }
        }

        return fieldsMap;
    }
}

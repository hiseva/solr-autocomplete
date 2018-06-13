/*
 *    Copyright (c) 2007-2009 Sematext International
 *    All Rights Reserved
 *
 *    THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF Sematext International
 *    The copyright notice above does not evidence any actual or intended
 *    publication of such source code.
 */
package com.hiseva.autocomplete.urp;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.Math;

public class AutocompleteUpdateRequestProcessor extends UpdateRequestProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(AutocompleteUpdateRequestProcessor.class);

    static final String ID = "id";
    static final String PHRASE = "phrase";
    static final String TYPE = "type";
    static final String FREQUENCY = "frequency";

    private SolrClient solrAC;
    private List<String> fields;
    private List<String> copyAsIsFields;
    private List<String> idFields;
    private String separator;

    public AutocompleteUpdateRequestProcessor(SolrClient solrAC, List<String> fields, List<String> copyAsIsFields, List<String> idFields, String separator, UpdateRequestProcessor next) {
        super(next);
        this.solrAC = solrAC;
        this.fields = fields;
        this.copyAsIsFields = copyAsIsFields;
        this.idFields = idFields;
        this.separator = separator;
    }

    @Override
    public void processAdd(AddUpdateCommand cmd) throws IOException {
        SolrInputField [] copyAsIsFieldsValues = null;
        
        if (copyAsIsFields.size() > 0) {
          copyAsIsFieldsValues = new SolrInputField[copyAsIsFields.size()];
        }
      
        SolrInputDocument doc = cmd.getSolrInputDocument();
        
        // first extract all fields which should be copied as-is
        // and get id field values
        Set<String> idField = new HashSet<>();
        for (String name: idFields) {
            idField.add(name);
        }
        ArrayList<String> idFieldValues = new ArrayList<>();
        int index = 0;
        for (String fieldName : copyAsIsFields) {
          SolrInputField field = doc.getField(fieldName);
          
          if (field != null) {
            copyAsIsFieldsValues[index++] = field;
            if (idField.contains(fieldName)) {
              idFieldValues.add(field.getValue().toString());
            }
          }
        }

        String idPrefix = String.join("-", idFieldValues);

        try {
          Map<String, Map<String, Object>> uniquePhrases = new HashMap<>();

          for (String fieldName : fields) {
  
              boolean arrayField = fieldName.startsWith("[") && fieldName.endsWith("]");
              boolean tokenizeField = fieldName.startsWith("{") && fieldName.endsWith("}");
  
              SolrInputField field = null;
              if (arrayField || tokenizeField) {
                  field = doc.getField(fieldName.substring(1, fieldName.length() - 1));
              } else {
                  field = doc.getField(fieldName);
              }
  
              if (field != null && field.getValue() != null) {
  
                  String phrase = field.getValue().toString();
  
                  if (arrayField || tokenizeField) {
  
                      String[] phrases = null;

                      if (arrayField) {
                          phrases = phrase.split(separator);
                      } else {
                          phrases = phrase.split(" ");
                      }

                      for (String value : phrases) {
                          String decoratedPhrase = decoratePhrase(value.trim(), doc);
                          addPhrase(uniquePhrases, decoratedPhrase, fieldName.substring(1, fieldName.length() - 1));
                      }
                  } else {
                      String decoratedPhrase = decoratePhrase(phrase.trim(), doc);
                      addPhrase(uniquePhrases, decoratedPhrase, fieldName);
                  }
              }
          }

          for (String p : uniquePhrases.keySet()) {
            String id = idPrefix != null ? idPrefix + "-" + p : p;
            SolrInputDocument document = fetchExistingOrCreateNewSolrDoc(id);
            addField(document, ID, id);
            addField(document, PHRASE, p);
            addField(document, TYPE, (String) uniquePhrases.get(p).get("type"));
            addCount(document, FREQUENCY, (int) uniquePhrases.get(p).get("count"));
            addCopyAsIsFields(document, copyAsIsFieldsValues);
            try {
                solrAC.add(document);
            } catch (SolrServerException e) {
                e.printStackTrace();
            }
          }
          // not done any more, since users should be able to configure it as they want
          // solrAC.commit();
        } catch (SolrServerException e) {
          LOG.error("Error while updating the document", e);
        } catch (Throwable thr) {
          if (thr.getMessage().contains("version conflict")) {
            LOG.warn(thr.getMessage());
          } else {
            LOG.error("Error while updating the document", thr);
          }
        }

        super.processAdd(cmd);
    }

    private void addPhrase(Map<String, Map<String, Object>> uniquePhrases, String phrase, String type) {
        if (phrase != null && !phrase.equals("")) {
            Map<String, Object> d = new HashMap<>();
            if (uniquePhrases.containsKey(phrase)) {
                d = uniquePhrases.get(phrase);
                d.put("count", 1 + (int) d.get("count"));
            } else {
                d.put("type", type);
                d.put("count", 1);
            }
            uniquePhrases.put(phrase, d);
        }
    }

    /**
     * Can be overriden by subclasses, for instance, if AC phrase should not be just copied from
     * some phrase field but decorated before adding it to AC doc. Examples for decoration:
     * - phrase should have a prefix made from value in field authorName
     * - phrase should not contain any special characters
     * - ...
     * 
     * This method is invoked once for each value found in "phrase" field from source (main index)
     * document.
     * 
     * @param phraseFieldValue .
     * @param mainIndexDoc .
     * @return .
     */
    protected String decoratePhrase(String phraseFieldValue, SolrInputDocument mainIndexDoc) {
        if (phraseFieldValue.matches(".*\\p{Alpha}+.*")) {
            String resultString = phraseFieldValue.toLowerCase();
            //\p{Punct}: One of !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~
            //chars that need escaping \.[]{}()<>*+-=?^$|
            resultString = resultString.replaceAll("[\\s\\!\"\\(\\)\\*,;\\<\\=\\>\\?\\[\\]\\^\\\\`\\{\\|\\}~]+", " ");
            resultString = resultString.replaceAll(" +", " ").trim();
            if (resultString.matches("^[#\\$%&'@\\./\\+_\\-:].*")) {
                resultString = resultString.substring(1, resultString.length());
            }
            if (resultString.matches(".*[#\\$%&'@\\./\\+_\\-:]$")) {
                resultString = resultString.substring(0, resultString.length() - 1);
            }
            return resultString.replaceAll("\\s+", " ").trim();
        } else {
            return null;
        }
    }
    
    private void addField(SolrInputDocument doc, String name, String value) {
      // find if such field already exists
      if (doc.get(name) == null) {
        doc.addField(name, value);
      } else {
        SolrInputField f = doc.get(name);
        if (f.getValue() == null) {
          f.setValue(value);
        }
      }
    }

    private void addField(SolrInputDocument doc, String name, Collection<Object> values) {
      // find if such field already exists
      if (doc.get(name) == null) {
        if (values != null) {
          for (Object value : values) {
            doc.addField(name, value);
          }
        }
      } else {
        // for some fields we can't allow multiple values, like ID field phrase, so we have to perform this check
        SolrInputField f = doc.get(name);
        
        for (Object value : values) {
          boolean valueExists = false;
          
          for (Object existingValue : f.getValues()) {
            if (existingValue == null && value == null) {
              valueExists = true;
              break;
            }
            if (existingValue != null && value != null && existingValue.equals(value)) {
              valueExists = true;
              break;
            }
          }
          
          if (!valueExists) {
            f.addValue(value);
          }
        }
      }
    }

    private void addCount(SolrInputDocument doc, String name, Integer value) {
        // find if such field already exists
        if (doc.get(name) == null) {
            doc.addField(name, Math.log10(value));
        } else {
            SolrInputField f = doc.get(name);

            if (f.getValue() == null){
                f.setValue(Math.log10(value));
            } else {
                f.setValue(Math.log10(Math.pow(10, (float) f.getValue()) + (float) value));
            }
        }
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
        
        for (String fieldName : doc.getFieldNames()) {
          tmp.addField(fieldName, doc.getFieldValue(fieldName));
        }
        return tmp;
      } else {
        throw new IllegalStateException("Query with params : " + p + " returned more than 1 hit!");
      }
    }

    private void addCopyAsIsFields(SolrInputDocument doc, SolrInputField[] copyAsIsFieldsValues) {
      if (copyAsIsFieldsValues != null) {
        for (SolrInputField f : copyAsIsFieldsValues) {
          if (f != null) {
            Collection<Object> values = f.getValues();
            
            if (values != null && values.size() > 0) {
              addField(doc, f.getName(), values);
            }
          }
        }
      }
    }
}

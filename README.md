[![Build Status](https://travis-ci.org/sematext/solr-autocomplete.svg?branch=master)](https://travis-ci.org/sematext/solr-autocomplete)

# Solr AutoComplete

This project is forked from https://github.com/sematext/solr-autocomplete

Please check the sematext project for basic setup and usage instructions.

We updated the realtime `UpdateRequestProcessor` to support the following functions:

* weighted frequency boost for suggested phrases
* composite id for partitioning phrase by user for personalized and secure suggestions
* basic text cleaning

Performance improvement and bug fixes:

* batched instead of individual phrase updates
* version conflict bug fix

## Installation Instrcutions

Please first refer to the sematext solr-autocomplete project for installation instructions. The steps below are specific to setting up functions added by the current project.


### Solr libs

Build the package with

```
mvn clean package
```

And copy `target/seva-AutoComplete-xxx.jar` to your `$SOLR_HOME/server/solr-webapp/webapp/WEB-INF/lib/` directory.

Build and copy `st-ReSearcher-core-xxx.jar` to the same directory as in the sematext instruction.


### Autocomplete Solr schema

Compared to the original sematext autocomplete solr schema at `solr/collection1/conf/managed-schema`, changes required by the new functions include:

* updating the unique key from `phrase` to `id`

```
<uniqueKey>id</uniqueKey>
```

* new fields in the schema

```
<field name="id" type="string" indexed="true" required="true" stored="true"/>
<field name="frequency" type="float" indexed="true" required="false" stored="true"/>
```

### Main Solr core solrconfig.xml

To index phrase for autocomplete as part of the main indexing process, edit your main Solr core solrconfig.xml to include this update request processor.

```xml
  <updateRequestProcessorChain>
    <processor class="com.hiseva.autocomplete.urp.AutocompleteUpdateRequestProcessorFactory">
      <str name="separator">,</str>

      <!-- Use only when AC core is deployed in a separate Solr instance
      <str name="solrAC">http://localhost:8080/solr/autocomplete</str>
      -->

      <!--Use with embedded Solr AC core; when AC core is deployed in same Solr and 'main
          index' -->
      <str name="solrAC">main_ac</str>

      <!--Fields which will be added, [ ] fields will be treated as array fields which will
          be tokenized by 'separator', { } fields will be tokenized by white space -->
      <str name="fields">title,[attribution],{tags},category</str>
      <!-- Corresponding weights the above fields for frequency boost. Optional -->
      <str name="fieldWeights">100,1,1,1</str>
      <!-- Fields that will be copied over from the main index. Optional -->
      <str name="copyAsIsFields">userid,genre</str>
      <!-- A subset of copyAsIsFields used for partitioning phrase -->
      <str name="idFields">userid</str>
    </processor>
    <processor class="solr.RunUpdateProcessorFactory" />
    <processor class="solr.LogUpdateProcessorFactory" />
  </updateRequestProcessorChain>
```

Set up autoCommit with a relatively small commit interval for more accurate frequency representation.

```xml
    <autoCommit>
      <maxTime>30000</maxTime>  <!-- hard commit every 30 seconds -->
      <openSearcher>false</openSearcher>
    </autoCommit>
    <autoSoftCommit>
      <maxTime>5000</maxTime>  <!-- soft commit every 5 seconds -->
    </autoSoftCommit>
```


## Usage

Note: add `fl=phrase` tot the requests to reduce the size of the payload.

### Weighted frequency boost for suggested phrases

Setup:

In the `updateRequestProcessorChain` added to the main Solr core solrconfig.xml, 

```xml
      <!--Fields which will be added, [ ] fields will be treated as array fields which will
          be tokenized by 'separator', { } fields will be tokenized by white space -->
      <str name="fields">title,[attribution],{tags},category</str>
      <!-- Corresponding weights the above fields for frequency boost. Optional -->
      <str name="fieldWeights">100,1,1,1</str>
```
Here the field `title` is weighted more heavily than the rest of the fields. Please note that frequencies are log-transformed before boosting, so a weight of 100 will 10x the value in the final boost. To turn on frequency boost, add `bf=frequency` to the autocomplete solr request. 

```
http://local.hiseva.com:8983/solr/main_ac/select?q=ma&qt=dismax_ac&bf=frequency
```

### Personalized and secure suggestions

Suggestions can be filtered by userid, so users get autocomplete suggestions based their own data. It's personalized, and there's no data leak across users.

```
http://local.hiseva.com:8983/solr/main_ac/select?q=ma&qt=dismax_ac&bf=frequency&fq=userid:xxx-xxx-xxx
```

### Other advanced features

Other advanced features from the sematext solr-autocomplete project are still available. For example, add `ac_spellcheck=true` for fuzzy autocomplete.

```
http://local.hiseva.com:8983/solr/main_ac/select?q=ma&qt=dismax_ac&bf=frequency&ac_spellcheck=true&fq=userid:xxx-xxx-xxx
```

/*
 *    Copyright (c) Sematext International
 *    All Rights Reserved
 *
 *    THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF Sematext International
 *    The copyright notice above does not evidence any
 *    actual or intended publication of such source code.
 */
package com.hiseva.autocomplete.urp;

import com.sematext.autocomplete.solr.AutoCompleteSearchComponent;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.CommonParams;
import org.junit.*;

import java.io.IOException;

public class AutoCompleteUpdateRequestProcessorTest extends SolrTestCaseJ4 {
  
  @Before
  public void setUp() throws Exception {
  }
  
  @After
  public void tearDown() throws IOException {
    //h.getCore().getSearcher().get().close();
  }

  @Test
  public void runTest() {
    //run a single unit test
  }
}

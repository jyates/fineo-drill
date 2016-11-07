/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.store.ischema;

import org.apache.drill.PlanTestBase;
import org.apache.drill.QueryTestUtil;
import org.apache.drill.common.config.DrillConfig;
import org.apache.drill.exec.ExecConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.typesafe.config.ConfigValueFactory.fromMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Translation should occur for a given user (or regex of a user) to the specified class
 */
public class TestInfoSchemaTranslation extends PlanTestBase {

  @BeforeClass
  public static void setupUserFilters() {
    final Properties props = cloneDefaultTestConfigProperties();

    // log the output
    props.put(QueryTestUtil.TEST_QUERY_PRINTING_SILENT, "false");
    DrillConfig conf = DrillConfig.create(props);

    // use this translator
    Map<String, String> translator = new HashMap<>();
    translator.put("@class", TranslatorForTesting.class.getName());

    conf = new DrillConfig(conf.withValue(ExecConstants.ISCHEMA_TRANSLATE_RULES_KEY,
      fromMap(translator)), false);

    updateTestCluster(1, conf);
  }

  @Test
  public void testSchemataRename() throws Exception {
    final String query = "SELECT * FROM INFORMATION_SCHEMA.`SCHEMATA`";
    List<Map<String, String>> rows = runQuery(query);
    assertEquals("Wrong number of rows returned! Got rows: " + rows, 9, rows.size());
    CollectingRecordListener.verifyNextRow(0, rows, CollectingRecordListener
      .schemaRow("anonymous", "ischema", "anonymous", "anonymous", false));
    for (int i = 0; i < 5; i++) {
      CollectingRecordListener.verifyNextRow(i + 1, rows, CollectingRecordListener
        .schemaRow("anonymous", "file", "anonymous", "anonymous", false));
    }
    CollectingRecordListener.verifyNextRow(6, rows, CollectingRecordListener
      .schemaRow("anonymous", "file", "anonymous", "anonymous", false));
    CollectingRecordListener.verifyNextRow(7, rows, CollectingRecordListener
      .schemaRow("anonymous", "file", "anonymous", "anonymous", true));
    CollectingRecordListener.verifyNextRow(8, rows,
      CollectingRecordListener
        .schemaRow("anonymous", "system-tables", "anonymous", "anonymous", false));
    assertTrue(rows.isEmpty());
  }

  @Test
  public void testCatalogRename() throws Exception {
    final String query = "SELECT * FROM INFORMATION_SCHEMA.`CATALOGS`";
    List<Map<String, String>> rows = runQuery(query);
    assertEquals("Wrong number of rows returned! Got rows: " + rows, 1, rows.size());
    Map<String, String> row = new HashMap<>();
    row.put(CollectingRecordListener.optionalVarchar("CATALOG_NAME"), "anonymous");
    row.put(CollectingRecordListener.optionalVarchar("CATALOG_DESCRIPTION"), "The internal metadata used by Drill");
    row.put(CollectingRecordListener.optionalVarchar("CATALOG_CONNECT"), "");
    CollectingRecordListener.verifyNextRow(0, rows, row);
  }

  private List<Map<String, String>> runQuery(String query) throws Exception {
    return CollectingRecordListener.runQuery(client, query);
  }

  /**
   * Simple translator that just uses the execution user name in place of nearly every field
   */
  public static class TranslatorForTesting extends InfoSchemaTranslator {

    @Override
    protected Records.Catalog handleCatalog(Records.Catalog input) {
      return new Records.Catalog(this.user, input.CATALOG_DESCRIPTION, input.CATALOG_CONNECT);
    }

    @Override
    protected Records.Schema handleSchema(Records.Schema input) {
      return new Records.Schema(this.user, this.user, this.user, input.TYPE,
        input.IS_MUTABLE.equals("YES") ? true : false);
    }

    @Override
    protected Records.Table handleTable(Records.Table input) {
      return new Records.Table(this.user, this.user, this.user, input.TABLE_TYPE);
    }

    @Override
    protected Records.View handleView(Records.View input) {
      return new Records.View(this.user, this.user, this.user, input.VIEW_DEFINITION);
    }

    @Override
    protected Records.Column handleColumn(Records.Column input) {
      return new Records.Column(this.user, this.user, this.user, input);
    }
  }
}

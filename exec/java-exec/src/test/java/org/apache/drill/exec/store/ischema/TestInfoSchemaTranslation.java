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
import org.apache.drill.TestBuilder;
import org.apache.drill.common.config.DrillConfig;
import org.apache.drill.exec.ExecConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.typesafe.config.ConfigValueFactory.fromMap;

/**
 * Translation should occur for a given user (or regex of a user) to the specified class
 */
public class TestInfoSchemaTranslation extends PlanTestBase {

  public static final String ANON = "anonymous";

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
    TestBuilder tb = testBuilder()
      .sqlQuery("SELECT * FROM INFORMATION_SCHEMA.`SCHEMATA`")
      .baselineColumns("CATALOG_NAME",
        "TYPE",
        "SCHEMA_NAME",
        "SCHEMA_OWNER",
        "IS_MUTABLE")
      .ordered()
      .baselineValues(ANON, "ischema", ANON, ANON, "NO");
    for (int i = 0; i < 5; i++) {
      tb = tb.baselineValues(ANON, "file", ANON, ANON, "NO");
    }
    tb.baselineValues(ANON, "file", ANON, ANON, "NO")
      .baselineValues(ANON, "file", ANON, ANON, "YES")
      .baselineValues(ANON, "system-tables", ANON, ANON, "NO")
      .build().run();
  }

  @Test
  public void testCatalogRename() throws Exception {
    testBuilder()
      .sqlQuery("SELECT * FROM INFORMATION_SCHEMA.`CATALOGS`")
      .baselineColumns("CATALOG_NAME",
        "CATALOG_DESCRIPTION",
        "CATALOG_CONNECT"
      )
      .ordered()
      .baselineValues(
        ANON,
        "The internal metadata used by Drill",
        "")
      .build().run();
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

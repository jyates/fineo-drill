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
import org.apache.drill.exec.client.DrillClient;
import org.apache.drill.exec.proto.UserBitShared;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.typesafe.config.ConfigValueFactory.fromMap;
import static org.apache.drill.exec.store.ischema.CollectingRecordListener.schemaRow;
import static org.apache.drill.exec.store.ischema.CollectingRecordListener.verifyNextRow;
import static org.junit.Assert.assertEquals;

public class TestInfoSchemaUserFilterAndTranslator extends PlanTestBase {

  public static final String ANON = "anonymous";

  @BeforeClass
  public static void setupUserFilters() {
    final Properties props = cloneDefaultTestConfigProperties();

    // log the output
    props.put(QueryTestUtil.TEST_QUERY_PRINTING_SILENT, "false");
    DrillConfig conf = DrillConfig.create(props);

    // use the simple user translator from the other test
    Map<String, String> translator = new HashMap<>();
    translator.put("@class", TestInfoSchemaTranslation.TranslatorForTesting.class.getName());
    conf = new DrillConfig(conf.withValue(ExecConstants.ISCHEMA_TRANSLATE_RULES_KEY,
      fromMap(translator)), false);

    // anonymous user can only see a subset of the tables
    Map<String, Map<String, Collection<String>>> filters = new HashMap<>();
    Map<String, Collection<String>> userSchemaMap = new HashMap<>();
    userSchemaMap.put("anon.*", newArrayList("INFORMATION_SCHEMA"));
    userSchemaMap.put("root", newArrayList("dfs.${name}"));
    filters.put("schemas", userSchemaMap);
    newHashMap();

    Map<String, Collection<String>> userMap = new HashMap<>();
    userMap.put("anonymous", newArrayList("CATALOGS"));
    filters.put("tables", userMap);

    conf = new DrillConfig(conf.withValue(ExecConstants.PER_USER_ISCHEMA_FILTER_RULES_KEY, fromMap
      (filters)), false);

    updateTestCluster(1, conf);
  }

  @Test
  public void testAnonymousUserOnlySeesTheirOwnInformationSchema() throws Exception {
    List<Map<String, String>> rows =
      CollectingRecordListener.runQuery(client, "SELECT * FROM INFORMATION_SCHEMA.`SCHEMATA`");
    // verify rows
    assertEquals("Wrong number of rows returned!Got rows: " + rows, 1, rows.size());
    verifyNextRow(0, rows, schemaRow(ANON, "ischema", ANON, ANON, false));
  }

  @Test
  public void testSpecifyingUserNameFilterAndTranslate() throws Exception {
    // connect as the 'root' user
    Properties props = new Properties();
    props.put("user", "root");
    DrillClient client = null;
    try {
      client = QueryTestUtil.createClient(config, serviceSet, 2, props);

      // and attempt to read the dfs.root schema
      final String query = "SELECT * FROM INFORMATION_SCHEMA.`SCHEMATA`";
      List<Map<String, String>> rows = CollectingRecordListener.runQuery(client, query);
      assertEquals("Wrong number of rows! Got rows: " + rows, 1, rows.size());
      verifyNextRow(0, rows, schemaRow("root", "file", "root", "root", false));
    } finally {
      client.close();
    }
  }
}

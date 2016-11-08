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
import java.util.Map;
import java.util.Properties;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.typesafe.config.ConfigValueFactory.fromMap;
import static org.junit.Assert.assertEquals;

public class TestInfoSchemaUserFilterConfigPushdown extends PlanTestBase {

  @BeforeClass
  public static void setupUserFilters() {
    final Properties props = cloneDefaultTestConfigProperties();

    // log the output
    props.put(QueryTestUtil.TEST_QUERY_PRINTING_SILENT, "false");
    DrillConfig conf = DrillConfig.create(props);


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

  /*
  Default results from:
  -> SELECT * FROM INFORMATION_SCHEMA.`CATALOGS`
  CATALOG_NAME    CATALOG_DESCRIPTION    CATALOG_CONNECT
  DRILL    The internal metadata used by Drill

  -> SELECT * FROM INFORMATION_SCHEMA.`SCHEMATA`:
  CATALOG_NAME    SCHEMA_NAME    SCHEMA_OWNER    TYPE    IS_MUTABLE
  DRILL    INFORMATION_SCHEMA    <owner>    ischema    NO
  DRILL    cp.default    <owner>    file    NO
  DRILL    dfs.default    <owner>    file    NO
  DRILL    dfs.root    <owner>    file    NO
  DRILL    dfs.tmp    <owner>    file    NO
  DRILL    dfs_test.default    <owner>    file    NO
  DRILL    dfs_test.home    <owner>    file    NO
  DRILL    dfs_test.tmp    <owner>    file    YES
  DRILL    sys    <owner>    system-tables    NO

    -> SELECT * FROM INFORMATION_SCHEMA.`TABLES`:
  TABLE_CATALOG    TABLE_SCHEMA    TABLE_NAME    TABLE_TYPE
  DRILL    INFORMATION_SCHEMA    CATALOGS    TABLE
  DRILL    INFORMATION_SCHEMA    COLUMNS    TABLE
  DRILL    INFORMATION_SCHEMA    SCHEMATA    TABLE
  DRILL    INFORMATION_SCHEMA    TABLES    TABLE
  DRILL    INFORMATION_SCHEMA    VIEWS    TABLE
  DRILL    sys    boot    TABLE
  DRILL    sys    drillbits    TABLE
  DRILL    sys    memory    TABLE
  DRILL    sys    options    TABLE
  DRILL    sys    threads    TABLE
  DRILL    sys    version    TABLE
   */

  @Test
  public void testAnonymousUserPatternMatchingAndOnlyInformationSchema() throws Exception {
    testBuilder()
      .sqlQuery("SELECT * FROM INFORMATION_SCHEMA.`SCHEMATA`")
      .baselineColumns("CATALOG_NAME",
        "TYPE",
        "SCHEMA_NAME",
        "SCHEMA_OWNER",
        "IS_MUTABLE")
      .ordered()
      .baselineValues("DRILL", "ischema", "INFORMATION_SCHEMA", "<owner>", "NO")
      .build().run();
  }

  @Test
  public void testAnonymousUserCanOnlySeeCatalogTable() throws Exception {
    testBuilder()
      .sqlQuery("SELECT * FROM INFORMATION_SCHEMA.`TABLES`")
      .baselineColumns("TABLE_CATALOG",
        "TABLE_SCHEMA",
        "TABLE_NAME",
        "TABLE_TYPE")
      .ordered()
      .baselineValues("DRILL", "INFORMATION_SCHEMA", "CATALOGS", "TABLE")
      .build().run();
  }

  /**
   * Specify the user, which creates a new client. For that, rather than hacking the TestBuilder,
   * we just use a simple builder that translates results into string rows.
   */
  @Test
  public void testMatchingUserNameInLike() throws Exception {
    // connect as the 'root' user
    Properties props = new Properties();
    props.put("user", "root");
    DrillClient client = null;
    try {
      client = QueryTestUtil.createClient(config, serviceSet, 2, props);

      // and attempt to read the dfs.root schema
      final String query = "SELECT * FROM INFORMATION_SCHEMA.`SCHEMATA`";
      assertEquals(1, QueryTestUtil.testRunAndPrint(client, UserBitShared.QueryType.SQL, query));
    } finally {
      client.close();
    }
  }
}

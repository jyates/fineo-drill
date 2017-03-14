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
package org.apache.drill.exec.store.dfs.strategy;

import org.apache.drill.BaseTestQuery;
import org.apache.drill.TestBuilder;
import org.apache.drill.common.util.TestTools;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static java.lang.String.format;

/**
 * Test that we can rename a certain number of directories up to a certain depth
 */
public class TestFixedDirectoryStrategy extends BaseTestQuery {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();
  static final String WORKING_PATH = TestTools.getWorkingPath();
  static final String TEST_RES_PATH = WORKING_PATH + "/src/test/resources";

  @Test
  public void testSimpleRead() throws Exception {
    expectOrdersFrom1994MultiLevel(testBuilder()
      .sqlQuery(format("select yr, quarter, o_custkey, o_orderkey from dfs_test"
                       + ".fixed.`%s/multilevel/json` where yr=1994 and quarter='Q1'",
        TEST_RES_PATH))
      .unOrdered()
      .baselineColumns("yr", "quarter", "o_custkey", "o_orderkey"));
  }

  @Test
  public void testReadMixed() throws Exception {
    String query = String.format("select yr, quarter, o_custkey, o_orderkey from dfs_test"
                                 + ".fixed.`%s/multilevel/jsonFileMixDir` where yr=1995 and "
                                 + "quarter is null", TEST_RES_PATH);
    testBuilder()
      .sqlQuery(query)
      .unOrdered()
      .baselineColumns("yr", "quarter", "o_orderkey", "o_custkey")
      .baselineValues("1995", null, 10032l, 1301l)
      .baselineValues("1995", null, 10166l, 1079l)
      .baselineValues("1995", null, 10448l, 1498l)
      .baselineValues("1995", null, 10449l, 958l)
      .baselineValues("1995", null, 10483l, 349l)
      .go();
  }

  @Test
  public void testUnspecifiedSubDirectory() throws Exception {
    String query = String.format("select yr, dir1, o_custkey, o_orderkey from dfs_test"
                                 + ".fixed1.`%s/multilevel/json` where yr=1994 and "
                                 + "dir1='Q1'", TEST_RES_PATH);
    expectOrdersFrom1994MultiLevel(testBuilder()
      .sqlQuery(query)
      .unOrdered()
      .baselineColumns("yr", "dir1", "o_custkey", "o_orderkey"));
  }

  /**
   * Don't allow access to the actual partition/subdirectory information in the Q[n] directory.
   * dir1 should never be accessible, even though its not a named directory because we specified
   * nosubdirs. Results will come back across both sub-directories
   * @throws Exception
   */
  @Test
  public void testNoSubDirectoryAllowed() throws Exception {
    String query = String.format("select yr, dir1, o_custkey, o_orderkey from dfs_test"
                                 + ".fixedNoSubdir.`%s/multilevel/jsonSingleDir` where yr=1994",
      TEST_RES_PATH);
    testBuilder()
      .sqlQuery(query)
      .unOrdered()
      .baselineColumns("yr", "dir1", "o_custkey", "o_orderkey")
      .baselineValues("1994", null, 1292l, 66l)
      .baselineValues("1994", null, 890l, 99l)
      .baselineValues("1994", null, 1180l, 290l)
      .baselineValues("1994", null, 1411l, 291l)
      .baselineValues("1994", null, 392l, 323l)
      .baselineValues("1994", null, 1066l, 352l)
      .baselineValues("1994", null, 1270l, 389l)
      .baselineValues("1994", null, 547l, 417l)
      .baselineValues("1994", null, 793l, 673l)
      .baselineValues("1994", null, 553l, 833l)
      .go();
  }

  private void expectOrdersFrom1994MultiLevel(TestBuilder builder) throws Exception {
    builder
      .baselineValues("1994", "Q1", 1292l, 66l)
      .baselineValues("1994", "Q1", 890l, 99l)
      .baselineValues("1994", "Q1", 1180l, 290l)
      .baselineValues("1994", "Q1", 1411l, 291l)
      .baselineValues("1994", "Q1", 392l, 323l)
      .baselineValues("1994", "Q1", 1066l, 352l)
      .baselineValues("1994", "Q1", 1270l, 389l)
      .baselineValues("1994", "Q1", 547l, 417l)
      .baselineValues("1994", "Q1", 793l, 673l)
      .baselineValues("1994", "Q1", 553l, 833l)
      .go();
  }
}

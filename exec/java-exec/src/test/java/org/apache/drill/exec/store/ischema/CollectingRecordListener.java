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

import com.google.common.base.Stopwatch;
import io.netty.buffer.DrillBuf;
import org.apache.drill.QueryTestUtil;
import org.apache.drill.common.DrillAutoCloseables;
import org.apache.drill.common.config.DrillConfig;
import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.common.util.DrillStringUtils;
import org.apache.drill.exec.client.DrillClient;
import org.apache.drill.exec.exception.SchemaChangeException;
import org.apache.drill.exec.memory.BufferAllocator;
import org.apache.drill.exec.memory.RootAllocatorFactory;
import org.apache.drill.exec.proto.UserBitShared;
import org.apache.drill.exec.record.MaterializedField;
import org.apache.drill.exec.record.RecordBatchLoader;
import org.apache.drill.exec.record.VectorAccessible;
import org.apache.drill.exec.record.VectorWrapper;
import org.apache.drill.exec.rpc.ConnectionThrottle;
import org.apache.drill.exec.rpc.user.AwaitableUserResultsListener;
import org.apache.drill.exec.rpc.user.QueryDataBatch;
import org.apache.drill.exec.rpc.user.UserResultsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Listener that collects the results of the execution and makes it accessible as a list of rows
 */
public class CollectingRecordListener implements UserResultsListener {
  private static final Logger LOG = LoggerFactory.getLogger(CollectingRecordListener.class);
  private List<Map<String, String>> results = new ArrayList<>();

  private final AtomicInteger count = new AtomicInteger();
  private final Stopwatch w = Stopwatch.createUnstarted();
  private final RecordBatchLoader loader;
  private final BufferAllocator allocator;

  public CollectingRecordListener(DrillConfig config) {
    this.allocator = RootAllocatorFactory.newRoot(config);
    this.loader = new RecordBatchLoader(allocator);
  }

  @Override
  public void queryIdArrived(UserBitShared.QueryId queryId) {
    w.start();
  }

  @Override
  public void submissionFailed(UserException ex) {
    System.out.println("Exception (no rows returned): " + ex + ".  Returned in " + w.elapsed(
      TimeUnit.MILLISECONDS)
                       + "ms.");
  }

  @Override
  public void queryCompleted(UserBitShared.QueryResult.QueryState state) {
    DrillAutoCloseables.closeNoChecked(allocator);
    System.out.println(
      "Total rows returned : " + count.get() + ".  Returned in " + w.elapsed(TimeUnit.MILLISECONDS)
      + "ms.");
  }

  @Override
  public void dataArrived(QueryDataBatch result, ConnectionThrottle throttle) {
    final UserBitShared.QueryData header = result.getHeader();
    final DrillBuf data = result.getData();

    if (data != null) {
      count.addAndGet(header.getRowCount());
      try {
        loader.load(header.getDef(), data);
        // TODO:  Clean:  DRILL-2933:  That load(...) no longer throws
        // SchemaChangeException, so check/clean catch clause below.
      } catch (SchemaChangeException e) {
        submissionFailed(UserException.systemError(e).build(LOG));
      }

      showVectorAccessibleContent(loader);
      loader.clear();
    }

    result.release();
  }

  private void showVectorAccessibleContent(VectorAccessible va) {
    List<String> columns = new ArrayList<>();
    for (VectorWrapper<?> vw : va) {
      MaterializedField field = vw.getValueVector().getField();
      columns.add(field.getPath() + "<" + field.getType().getMinorType() +
                  "(" + field.getType().getMode() + ")" + ">");
    }

    int rows = va.getRecordCount();
    System.out.println(rows + " row(s):");
    for (int row = 0; row < rows; row++) {
      Map<String, String> nextRow = new HashMap<>();
      results.add(nextRow);
      int colIndex = 0;
      for (VectorWrapper<?> vw : va) {
        Object o = vw.getValueVector().getAccessor().getObject(row);
        String cellString;
        if (o instanceof byte[]) {
          cellString = DrillStringUtils.toBinaryString((byte[]) o);
        } else {
          cellString = DrillStringUtils.escapeNewLines(String.valueOf(o));
        }
        nextRow.put(columns.get(colIndex++), cellString);
      }
    }

    for (VectorWrapper<?> vw : va) {
      vw.clear();
    }
  }

  public List<Map<String, String>> getResults() {
    return results;
  }

  public static List<Map<String, String>> runQuery(DrillClient client, String query) throws
    Exception {
    query = QueryTestUtil.normalizeQuery(query);
    DrillConfig config = client.getConfig();
    CollectingRecordListener listener = new CollectingRecordListener(config);
    AwaitableUserResultsListener wrapper = new AwaitableUserResultsListener(listener);
    client.runQuery(UserBitShared.QueryType.SQL, query, wrapper);
    wrapper.await();

    return listener.getResults();
  }

  static String optionalVarchar(String key) {
    return close(optional(varchar(key)));
  }

  private static String varchar(String key) {
    return key + "<" + "VARCHAR";
  }

  private static String optional(String key) {
    return key + "(OPTIONAL)";
  }

  private static String close(String key) {
    return key + ">";
  }

  static Map<String, String> schemaRow(String catalog, String type, String schemaName,
    String SchemaOwner, boolean mutable) {
    Map<String, String> row = new HashMap<>();
    row.put(optionalVarchar("CATALOG_NAME"), catalog);
    row.put(optionalVarchar("TYPE"), type);
    row.put(optionalVarchar("SCHEMA_NAME"), schemaName);
    row.put(optionalVarchar("SCHEMA_OWNER"), SchemaOwner);
    row.put(optionalVarchar("IS_MUTABLE"), mutable ? "YES" : "NO");
    return row;
  }

  static void verifyNextRow(int i, List<Map<String, String>> actualRows,
    Map<String, String> expectedRow) {
    assertFalse(i + ") Expected another row, but none present!", actualRows.isEmpty());
    Map<String, String> actual = actualRows.remove(0);
    assertEquals(i + ") Mismatch!", expectedRow, actual);
  }
}


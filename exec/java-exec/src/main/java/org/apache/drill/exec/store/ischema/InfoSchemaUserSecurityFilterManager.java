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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_NAME;
import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_SCHEMA;

/**
 * Manage the filter for per-user visibility limitations
 */
public class InfoSchemaUserSecurityFilterManager {

  private final Map<String, Multimap<String, String>> userMap;

  public InfoSchemaUserSecurityFilterManager(InfoSchemaConfig config) {
    // build the filter map per user
    this.userMap = new HashMap<>();
    addUserFilter(userMap, SHRD_COL_TABLE_SCHEMA, config.getTableSchemaFilters());
    addUserFilter(userMap, SHRD_COL_TABLE_NAME, config.getTableFilters());
  }

  private void addUserFilter(Map<String, Multimap<String, String>> userMap, String
    columnName, Map<String, Collection<String>> filters) {
    for (Map.Entry<String, Collection<String>> e : filters.entrySet()) {
      Multimap<String, String> umap = userMap.get(e.getKey());
      if (umap == null) {
        umap = ArrayListMultimap.create();
        userMap.put(e.getKey(), umap);
      }
      for (String schemaKey : e.getValue()) {
        umap.put(columnName, schemaKey);
      }
    }
  }

  public InfoSchemaFilter buildFilter(String userName, SelectedTable table) {
    InfoSchemaFilter filter = null;
    Multimap<String, String> filterSpec = userMap.get(userName);
    if (filterSpec != null) {
      filter = buildFilter(filterSpec, table);
    }
    return filter;
  }

  private InfoSchemaFilter buildFilter(Multimap<String, String> filterSpec, SelectedTable table) {
    List<InfoSchemaFilter.ExprNode> nodes = new ArrayList<>();
    for (Map.Entry<String, String> e : filterSpec.entries()) {
      switch (table) {
        // tables support both table name and schema name
        case TABLES:
          optionallyAdd(SHRD_COL_TABLE_NAME, e, nodes);
          // schemata only includes the table schema check
        case SCHEMATA:
          optionallyAdd(SHRD_COL_TABLE_SCHEMA, e, nodes);
          break;
        // everything else doesn't check anything
        default:
          continue;
      }
    }
    return new InfoSchemaFilter(new InfoSchemaFilter.FunctionExprNode("booleanor", nodes));
  }

  private void optionallyAdd(String key, Map.Entry<String, String> e,
    List<InfoSchemaFilter.ExprNode> nodes) {
    if (!e.getKey().equals(key)) {
      return;
    }
    nodes.add(new InfoSchemaFilter.FunctionExprNode("like",
      newArrayList(new InfoSchemaFilter.FieldExprNode(key),
        new InfoSchemaFilter.ConstantExprNode(e.getValue()))));
  }
}

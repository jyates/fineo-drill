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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.newArrayList;

/**
 * Filters for the schema reading by various external users.
 * <p>
 * These have some slightly non-intuitive rules about how things are combined.
 * They are:
 * <ol>
 * <li>Users are matched with a standard Java regex pattern matching</li>
 * <li>Table names are matching with SQL evaluated patterns (e.g. ".*" in Java is "%" in SQL)</li>
 * <li>Multiple conditions for the same user name specification are AND'd together
 * <ul>
 * <li>The user name specification much match exactly</li>
 * </ul>
 * </li>
 * <li>Multiple matching conditions for the same user name are OR'd together
 * <ul>
 * <li>For instance, ".*" and "auser" would match "auser" and their conditions would be OR'ed
 * together</li>
 * </ul></li>
 * <li>Use can use replacements in the condition. Supported replacements are:
 * <ul>
 * <li>${name} = the name of the current user making the query</li>
 * </ul>
 * Only one pass is made over conditions (no nested replacements are supported).
 * </li>
 * </ol>
 * </p>
 */
@JsonTypeName("info-schema-user-filter")
public class InfoSchemaUserFilters {

  private Map<String, Map<String, Collection<String>>> userFilterMap = new HashMap<>();

  @JsonCreator
  public InfoSchemaUserFilters(
    @JsonProperty("userFilterMap") Map<String, Map<String, Collection<String>>> filters) {
    this.userFilterMap = filters;
  }

  public InfoSchemaUserFilters() {
  }

  @JsonProperty("userFilterMap")
  public Map<String, Map<String, Collection<String>>> getUserFilterMap() {
    return userFilterMap;
  }

  public void add(String key, Map<String, Collection<String>> userWithValueOrSet) {
    // short circuit if no value configured
    if (userWithValueOrSet == null) {
      return;
    }
    for (Map.Entry<String, Collection<String>> userWithValueOr : userWithValueOrSet.entrySet()) {
      Map<String, Collection<String>> col = userFilterMap.get(userWithValueOr.getKey());
      if (col == null) {
        col = new HashMap<>();
        userFilterMap.put(userWithValueOr.getKey(), col);
      }
      Collection<String> update = userWithValueOr.getValue();
      Collection<String> values = col.get(key);
      if (values == null) {
        values = new ArrayList<>(update.size());
        col.put(key, values);
      }
      values.addAll(update);
    }
  }

  @JsonIgnore
  public InfoSchemaFilter getFilter(String user) {
    // match the username map as a set of regexes. Fields are additive as AND
    List<InfoSchemaFilter.ExprNode> nodes = new ArrayList<>();
    for (String name : userFilterMap.keySet()) {
      Matcher matcher = Pattern.compile(name).matcher(user);
      if (matcher.matches()) {
        Map<String, Collection<String>> filtermap = userFilterMap.get(name);
        InfoSchemaFilter.ExprNode node = buildConstraintFilter(matcher, filtermap);
        if (node != null) {
          nodes.add(node);
        }
      }
    }

    // has to pass one of the applied sets of filters per matching user regex.
    // For example,
    //  -  .* -> "table1"
    //  - auser -> "table2"
    // Will allow 'auser' to see both table1 and table2, but any other user can only see table1
    return nodes.isEmpty() ? null : new InfoSchemaFilter(Or(nodes));
  }

  /**
   * Build the filter for a given user's set of constraints (key -> [patterns]). Only one of the
   * patterns must match the incoming field for each key, so they are logically combined with OR.
   * However, all the keys must apply, so they are ANDed together.
   *
   * @param match
   * @param userMap user constraints
   * @return filter matching the user's configured constraints
   */
  private InfoSchemaFilter.ExprNode buildConstraintFilter(Matcher match, Map<String,
    Collection<String>> userMap) {
    // build a set of ORs for each constraint
    List<InfoSchemaFilter.ExprNode> nodes = new ArrayList<>();
    for (Map.Entry<String, Collection<String>> keyConstraints : userMap.entrySet()) {
      List<InfoSchemaFilter.ExprNode> keyNodes = new ArrayList<>();
      for (String constraint : keyConstraints.getValue()) {
        constraint = constraint.replace("${name}", match.group());
        keyNodes.add(new InfoSchemaFilter.FunctionExprNode("like",
          newArrayList(new InfoSchemaFilter.FieldExprNode(keyConstraints.getKey()),
            new InfoSchemaFilter.ConstantExprNode(constraint))));
      }
      if (keyNodes.size() < 2) {
        nodes.addAll(keyNodes);
      } else {
        nodes.add(Or(keyNodes));
      }
    }
    return And(nodes);
  }

  private static InfoSchemaFilter.ExprNode And(List<InfoSchemaFilter.ExprNode> nodes) {
    return logicalCombination("booleanand", nodes);
  }

  private static InfoSchemaFilter.ExprNode Or(List<InfoSchemaFilter.ExprNode> nodes) {
    return logicalCombination("booleanor", nodes);
  }

  private static InfoSchemaFilter.ExprNode logicalCombination(String comb, List<InfoSchemaFilter
    .ExprNode> nodes) {
    return new InfoSchemaFilter.FunctionExprNode(comb, nodes);
  }
}

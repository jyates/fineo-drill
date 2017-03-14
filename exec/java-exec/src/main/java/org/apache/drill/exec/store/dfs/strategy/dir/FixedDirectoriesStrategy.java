/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.store.dfs.strategy.dir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.logical.DirectoryColumnMatcher;
import org.apache.drill.exec.server.options.OptionManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Each directory in the hierarchy is given a fixed column name. Values of the columns are not
 * modified. For example, a directory layout like:
 * <pre>
 *  /[parent]
 *    /first
 *      /child1
 *    /second
 *      /child2
 * </pre>
 * When searching within [parent] with hierarchy names of: [top, child] will result in:
 * <table>
 * <tr>
 * <th>top</th><th>child</th>
 * </tr>
 * <tr><td>first</td><td>child1</td></tr>
 * <tr><td>second</td><td>child2</td></tr>
 * </table>
 * <p>
 * <tt>subdirs</tt> indicates that we do expect subdirectories below the specified
 * directories. In that case, we use the standard partition labeling mechanics of a prefix with a
 * number. Conversely, if <tt>subdirs</tt> is false, these fields will be expected to be part fo
 * the underlying file and not considered a partition column.
 * </p>
 */
@JsonTypeName(FixedDirectoriesStrategy.NAME)
@JsonIgnoreProperties({"partitionDesignator", "matcher", "columnIndex", "columnName", "delegate"})
public class FixedDirectoriesStrategy extends DirectoryStrategyBase {
  public static final String NAME = "fixed";
  private final LegacyStrategy delegate;
  private final String[] hierarchy;
  private final Map<String, Integer> partitionLookup;
  private final boolean subdirs;

  public FixedDirectoriesStrategy(@JsonProperty("hierarchy") String[] hierarchy,
    @JsonProperty("subdirs") boolean subDirs) {
    this.hierarchy = hierarchy;
    this.subdirs = subDirs;
    this.delegate = new LegacyStrategy();
    this.partitionLookup = new HashMap<>(hierarchy.length);
    for (int i = 0; i < hierarchy.length; i++) {
      partitionLookup.put(hierarchy[i], i);
    }
  }

  @Override
  public void init(OptionManager options) {
    delegate.init(options);
  }

  @Override
  public DirectoryColumnMatcher getMatcher() {
    return new DelegateMatcher(this);
  }

  @Override
  public Integer getColumnIndex(SchemaPath column) {
    return getColumnIndex(column.getAsUnescapedPath());
  }

  @Override
  public String getColumnName(int partitionIndex) {
    if (partitionIndex < hierarchy.length) {
      return hierarchy[partitionIndex];
    }
    // it must be outside the known files
    if (!subdirs) {
      return null;
    }
    return delegate.getColumnName(partitionIndex + hierarchy.length -1);
  }

  @Override
  public String getColumnValue(String partitionValue, int partitionIndex) {
    return delegate.getColumnValue(partitionValue, partitionIndex);
  }

  @Override
  public Integer getColumnIndex(String partitionName) {
    // lookup in the map. save the time to iterate the original list since we seem to do that a
    // lot and the little bit of memory overhead seems worth it. #prematureOptimization
    Integer index = this.partitionLookup.get(partitionName);
    if(index != null){
      return index;
    }

    if (!subdirs) {
      return null;
    }
    return this.delegate.getColumnIndex(partitionName);
  }

  @JsonProperty("hierarchy")
  public String[] getHierarchy() {
    return hierarchy;
  }

  @JsonProperty("subdirs")
  public boolean getSubdirs() {
    return subdirs;
  }
}

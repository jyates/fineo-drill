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
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.logical.DirectoryColumnMatcher;
import org.apache.drill.exec.ExecConstants;
import org.apache.drill.exec.server.options.OptionManager;
import org.apache.drill.exec.server.options.OptionValue;

import java.util.regex.Pattern;

/**
 * Partition columns are merely designated as [label][N]. For example, the default label for the
 * first partition would be: <code>dir0</code>. If you had a custom designator of <tt>pfx</tt>
 * then that column would be <code>pfx0</code>. The second partition would be <code>dir1</code>
 * or <code>pfx1</code>, respectively, and so on down to the maximum allowed depth.
 */
@JsonTypeName(LegacyStrategy.NAME)
@JsonIgnoreProperties({"partitionDesignator", "matcher", "columnIndex", "columnName"})
public class LegacyStrategy extends DirectoryStrategyBase {
  public static final String NAME = "legacy";
  private String partitionDesignator;

  @Override
  public void init(OptionManager options) {
    // TODO Remove null check after DRILL-2097 is resolved. That JIRA refers to test cases that
    // do not initialize options; so labelValue = null.
    final OptionValue labelValue =
      options.getOption(ExecConstants.FILESYSTEM_PARTITION_COLUMN_LABEL);
    this.partitionDesignator = labelValue == null ? "dir" : labelValue.string_val;
  }

  @Override
  public DirectoryColumnMatcher getMatcher() {
    final Pattern pattern = Pattern.compile(String.format("%s[0-9]+", partitionDesignator));
    return new PatternMatcher(pattern);
  }

  @Override
  public Integer getColumnIndex(SchemaPath column) {
    return getColumnIndex(column.getAsUnescapedPath().toString());
  }

  @Override
  public String getColumnName(int partitionIndex) {
    return this.partitionDesignator + partitionIndex;
  }

  @Override
  public String getColumnValue(String partitionValue, int partitionIndex) {
    // no changes
    return partitionValue;
  }

  @Override
  public Integer getColumnIndex(String partitionName) {
    if(!partitionName.contains(partitionDesignator)){
      return null;
    }
    return Integer.parseInt(partitionName.substring(partitionDesignator.length()));
  }
}

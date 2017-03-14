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
package org.apache.drill.common.logical;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.drill.common.expression.SchemaPath;

/**
 * Strategy for partitioning a directory structure and accessing the those partitions.
 * <p>
 * Should be JSON serializable
 * </p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property="type")
public interface DirectoryStrategy {

  DirectoryColumnMatcher getMatcher();

  Integer getColumnIndex(SchemaPath column);

  String getColumnName(int partitionIndex);

  /**
   * Get the value of the partition column from the actual name of the directory (partition) and
   * the position in the index hierarchy.
   * @param partitionValue value of the directory name
   * @param partitionIndex location in the partition hierarchy
   * @return value of the partition column to return to the client
   */
  String getColumnValue(String partitionValue, int partitionIndex);

  /**
   * Get the index of the partition based on the name.
   * @param partitionName
   * @return index of the partition, or <tt>null</tt> if this is not a valid partition
   */
  Integer getColumnIndex(String partitionName);
}

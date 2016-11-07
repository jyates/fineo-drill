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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.drill.exec.physical.base.AbstractSubScan;
import org.apache.drill.exec.proto.UserBitShared.CoreOperatorType;

public class InfoSchemaSubScan extends AbstractSubScan {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InfoSchemaSubScan.class);

  private final SelectedTable table;
  private final InfoSchemaFilter filter;
  private final InfoSchemaUserFilters userFilter;
  private InfoSchemaTranslator translator;

  @JsonCreator
  public InfoSchemaSubScan(@JsonProperty("table") SelectedTable table,
    @JsonProperty("filter") InfoSchemaFilter filter,
    @JsonProperty("userFilter") InfoSchemaUserFilters userFilter,
    @JsonProperty("translator") InfoSchemaTranslator translator) {
    super(null);
    this.table = table;
    this.filter = filter;
    this.userFilter = userFilter;
    this.translator = translator;
  }

  @JsonProperty("table")
  public SelectedTable getTable() {
    return table;
  }

  @JsonProperty("filter")
  public InfoSchemaFilter getFilter() {
    return filter;
  }

  @JsonProperty("userFilter")
  public InfoSchemaUserFilters getUserFilter() {
    return userFilter;
  }

  @Override
  public int getOperatorType() {
    return CoreOperatorType.INFO_SCHEMA_SUB_SCAN_VALUE;
  }

  public InfoSchemaTranslator getTranslator() {
    return translator;
  }
}

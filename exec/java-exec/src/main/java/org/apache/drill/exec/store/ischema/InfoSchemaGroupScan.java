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
package org.apache.drill.exec.store.ischema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.exec.physical.PhysicalOperatorSetupException;
import org.apache.drill.exec.physical.base.AbstractGroupScan;
import org.apache.drill.exec.physical.base.GroupScan;
import org.apache.drill.exec.physical.base.PhysicalOperator;
import org.apache.drill.exec.physical.base.ScanStats;
import org.apache.drill.exec.physical.base.ScanStats.GroupScanProperty;
import org.apache.drill.exec.physical.base.SubScan;
import org.apache.drill.exec.proto.CoordinationProtos.DrillbitEndpoint;

import java.util.List;

@JsonTypeName("info-schema")
public class InfoSchemaGroupScan extends AbstractGroupScan{
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InfoSchemaGroupScan.class);

  private final SelectedTable table;
  private final InfoSchemaFilter filter;
  private final InfoSchemaUserFilters userFilter;
  private final InfoSchemaTranslator translator;

  private boolean isFilterPushedDown = false;

  public InfoSchemaGroupScan(SelectedTable table, InfoSchemaUserFilters userFilters, InfoSchemaTranslator translator) {
    this(table, null, userFilters, translator);
  }

  @JsonCreator
  public InfoSchemaGroupScan(@JsonProperty("table") SelectedTable table,
    @JsonProperty("filter") InfoSchemaFilter filter,
    @JsonProperty("userFilter") InfoSchemaUserFilters userFilter,
    @JsonProperty("translator") InfoSchemaTranslator translator) {
    super((String)null);
    this.table = table;
    this.filter = filter;
    this.userFilter = userFilter;
    this.translator = translator;
  }

  private InfoSchemaGroupScan(SelectedTable table, InfoSchemaGroupScan that) {
    super(that);
    this.table = that.table;
    this.filter = that.filter;
    this.isFilterPushedDown = that.isFilterPushedDown;
    this.userFilter = that.userFilter;
    this.translator = that.translator;
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
  public void applyAssignments(List<DrillbitEndpoint> endpoints) throws PhysicalOperatorSetupException {
    Preconditions.checkArgument(endpoints.size() == 1);
  }

  @Override
  public SubScan getSpecificScan(int minorFragmentId) throws ExecutionSetupException {
    Preconditions.checkArgument(minorFragmentId == 0);
    return new InfoSchemaSubScan(table, filter, userFilter, translator);
  }

  public ScanStats getScanStats(){
    if (filter == null) {
      return ScanStats.TRIVIAL_TABLE;
    } else {
      // If the filter is available, return stats that is lower in cost than TRIVIAL_TABLE cost so that
      // Scan with Filter is chosen.
      return new ScanStats(GroupScanProperty.NO_EXACT_ROW_COUNT, 10, 1, 0);
    }
  }

  @Override
  public int getMaxParallelizationWidth() {
    return 1;
  }

  @Override
  public PhysicalOperator getNewWithChildren(List<PhysicalOperator> children) throws ExecutionSetupException {
    return new InfoSchemaGroupScan(table, this);
  }

  @Override
  public String getDigest() {
    return this.table.toString() + ", filter=" + filter;
  }

  @Override
  public GroupScan clone(List<SchemaPath> columns) {
    InfoSchemaGroupScan  newScan = new InfoSchemaGroupScan(table, this);
    return newScan;
  }

  public void setFilterPushedDown(boolean status) {
    this.isFilterPushedDown = status;
  }

  @JsonIgnore
  public boolean isFilterPushedDown() {
    return isFilterPushedDown;
  }

  public InfoSchemaTranslator getTranslator() {
    return translator;
  }
}

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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.schema.Schema.TableType;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Table;
import org.apache.drill.exec.planner.logical.DrillViewInfoProvider;
import org.apache.drill.exec.store.AbstractSchema;
import org.apache.drill.exec.store.RecordReader;
import org.apache.drill.exec.store.ischema.InfoSchemaFilter.Result;
import org.apache.drill.exec.store.pojo.PojoRecordReader;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.IS_CATALOG_NAME;
import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.SCHS_COL_SCHEMA_NAME;
import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_NAME;
import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_SCHEMA;

/**
 * Generates records for POJO RecordReader by scanning the given schema.
 */
public abstract class RecordGenerator<T> {
  protected InfoSchemaFilter filter;
  private InfoSchemaTranslator translator;

  public void setInfoSchemaFilter(InfoSchemaFilter filter) {
    this.filter = filter;
  }

  public boolean visitSchema(String schemaName, SchemaPlus schema) {
    return true;
  }

  public boolean visitTable(String schemaName, String tableName, Table table) {
    return true;
  }

  public boolean visitField(String schemaName, String tableName, RelDataTypeField field) {
    return true;
  }

  protected boolean shouldVisitSchema(String schemaName, SchemaPlus schema) {
    try {
      // if the schema path is null or empty (try for root schema)
      if (schemaName == null || schemaName.isEmpty()) {
        return false;
      }

      AbstractSchema drillSchema = schema.unwrap(AbstractSchema.class);
      if (!drillSchema.showInInformationSchema()) {
        return false;
      }

      Map<String, String> recordValues =
          ImmutableMap.of(SHRD_COL_TABLE_SCHEMA, schemaName,
                          SCHS_COL_SCHEMA_NAME, schemaName);
      recordValues = translate(recordValues);
      if (filter != null && filter.evaluate(recordValues) == Result.FALSE) {
        // If the filter evaluates to false then we don't need to visit the schema.
        // For other two results (TRUE, INCONCLUSIVE) continue to visit the schema.
        return false;
      }
    } catch(ClassCastException e) {
      // ignore and return true as this is not a Drill schema
    }
    return true;
  }

  protected boolean shouldVisitTable(String schemaName, String tableName) {
    Map<String, String> recordValues =
        ImmutableMap.of( SHRD_COL_TABLE_SCHEMA, schemaName,
                         SCHS_COL_SCHEMA_NAME, schemaName,
                         SHRD_COL_TABLE_NAME, tableName);
    recordValues = translate(recordValues);
    if (filter != null && filter.evaluate(recordValues) == Result.FALSE) {
      return false;
    }

    return true;
  }

  private Map<String, String> translate(Map<String, String> recordValues){
    return translator == null? recordValues :
                   (Map<String, String>) translator.apply(recordValues);
  }

  public abstract RecordReader getRecordReader(Function<T, T> transform);

  public void scanSchema(SchemaPlus root) {
    scanSchema(root.getName(), root);
  }

  /**
   * Recursively scans the given schema, invoking the visitor as appropriate.
   * @param  schemaPath  the path to the given schema, so far
   * @param  schema  the given schema
   */
  private void scanSchema(String schemaPath, SchemaPlus schema) {

    // Recursively scan any subschema.
    for (String name: schema.getSubSchemaNames()) {
      scanSchema(schemaPath +
          (schemaPath == "" ? "" : ".") + // If we have an empty schema path, then don't insert a leading dot.
          name, schema.getSubSchema(name));
    }

    // Visit this schema and if requested ...
    if (shouldVisitSchema(schemaPath, schema) && visitSchema(schemaPath, schema)) {
      // ... do for each of the schema's tables.
      for (String tableName: schema.getTableNames()) {
        Table table = schema.getTable(tableName);

        if (table == null) {
          // Schema may return NULL for table if the query user doesn't have permissions to load the table. Ignore such
          // tables as INFO SCHEMA is about showing tables which the use has access to query.
          continue;
        }

        // Visit the table, and if requested ...
        if (shouldVisitTable(schemaPath, tableName) && visitTable(schemaPath,  tableName, table)) {
          // ... do for each of the table's fields.
          RelDataType tableRow = table.getRowType(new JavaTypeFactoryImpl());
          for (RelDataTypeField field: tableRow.getFieldList()) {
            visitField(schemaPath,  tableName, field);
          }
        }
      }
    }
  }

  private static <T> Iterator<T> wrap(Iterator<T> iter, @Nullable Function<T, T> func){
    if(func == null){
      return iter;
    }
    return Iterators.transform(iter, func);
  }

  public void setTranslator(InfoSchemaTranslator translator) {
    this.translator = translator;
  }

  public static class Catalogs extends RecordGenerator<Records.Catalog> {
    @Override
    public RecordReader getRecordReader(Function<Records.Catalog, Records.Catalog> transform) {
      Records.Catalog catalogRecord =
          new Records.Catalog(IS_CATALOG_NAME,
                              "The internal metadata used by Drill", "");
      Iterator<Records.Catalog> iter = ImmutableList.of(catalogRecord).iterator();
      return new PojoRecordReader<>(Records.Catalog.class, wrap(iter, transform));

    }

    @Override
    protected boolean shouldVisitSchema(String schemaName, SchemaPlus schema) {
      return false;
    }

    @Override
    protected boolean shouldVisitTable(String schemaName, String tableName) {
      return false;
    }
  }

  public static class Schemata extends RecordGenerator<Records.Schema> {
    List<Records.Schema> records = Lists.newArrayList();

    @Override
    public  RecordReader getRecordReader(Function<Records.Schema, Records.Schema> transform) {
      return new PojoRecordReader<>(Records.Schema.class, wrap(records.iterator(), transform));
    }

    @Override
    public boolean visitSchema(String schemaName, SchemaPlus schema) {
      AbstractSchema as = schema.unwrap(AbstractSchema.class);
      records.add(new Records.Schema(IS_CATALOG_NAME, schemaName, "<owner>",
                                     as.getTypeName(), as.isMutable()));
      return false;
    }
  }

  public static class Tables extends RecordGenerator<Records.Table> {
    List<Records.Table> records = Lists.newArrayList();

    @Override
    public RecordReader getRecordReader(Function<Records.Table, Records.Table> transform) {
      return new PojoRecordReader<>(Records.Table.class, wrap(records.iterator(), transform));
    }

    @Override
    public boolean visitTable(String schemaName, String tableName, Table table) {
      Preconditions.checkNotNull(table, "Error. Table %s.%s provided is null.", schemaName, tableName);

      // skip over unknown table types
      if (table.getJdbcTableType() != null) {
        records.add(new Records.Table(IS_CATALOG_NAME, schemaName, tableName,
            table.getJdbcTableType().toString()));
      }

      return false;
    }
  }

  public static class Views extends RecordGenerator<Records.View>{
    List<Records.View> records = Lists.newArrayList();

    @Override
    public RecordReader getRecordReader(Function<Records.View, Records.View> transform) {
      return new PojoRecordReader<>(Records.View.class, wrap(records.iterator(), transform));
    }

    @Override
    public boolean visitTable(String schemaName, String tableName, Table table) {
      if (table.getJdbcTableType() == TableType.VIEW) {
        records.add(new Records.View(IS_CATALOG_NAME, schemaName, tableName,
                    ((DrillViewInfoProvider) table).getViewSql()));
      }
      return false;
    }
  }

  public static class Columns extends RecordGenerator<Records.Column> {
    List<Records.Column> records = Lists.newArrayList();

    @Override
    public RecordReader getRecordReader(Function<Records.Column, Records.Column> transform) {
      return new PojoRecordReader<>(Records.Column.class, wrap(records.iterator(), transform));
    }

    @Override
    public boolean visitField(String schemaName, String tableName, RelDataTypeField field) {
      records.add(new Records.Column(IS_CATALOG_NAME, schemaName, tableName, field));
      return false;
    }
  }
}

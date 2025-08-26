/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.adapter.csv;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.file.CsvDynamicEnumerator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.rel.type.DynamicRecordTypeImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.util.Source;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * CSV table with dynamic schema that supports runtime field resolution.
 *
 * <p>Fields are created dynamically when accessed. Non-existent fields
 * return NULL instead of throwing exceptions. This enables schema-less
 * CSV processing where column structure is not predefined.
 *
 * <p>Usage:
 * <pre>
 * SELECT id, name, optional_field FROM csv_table
 * -- id: returns actual value if column exists
 * -- name: returns actual value if column exists
 * -- optional_field: returns NULL if column doesn't exist
 * </pre>
 */
public class CsvDynamicTable extends AbstractTable implements ScannableTable {
  private final Source source;
  private final DynamicRecordTypeImpl rowType;

  /** Creates a CsvDynamicTable. */
  public CsvDynamicTable(Source source) {
    this.source = source;
    this.rowType = new DynamicRecordTypeImpl(
        new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT));
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    return rowType;
  }

  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) {
    return new AbstractEnumerable<@Nullable Object[]>() {
      @Override public Enumerator<@Nullable Object[]> enumerator() {
        return new CsvDynamicEnumerator(source, rowType, root.getTypeFactory());
      }
    };
  }

  @Override public String toString() {
    return "CsvDynamicTable";
  }
}

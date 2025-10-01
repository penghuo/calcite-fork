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



import static org.apache.calcite.sql.SqlCollation.IMPLICIT;

import java.util.Objects;

import org.apache.calcite.adapter.file.CsvEnumerator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeFamily;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeImpl;
import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.util.Source;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for table that reads CSV files.
 */
public abstract class CsvTable extends AbstractTable {
  protected final Source source;
  protected final @Nullable RelProtoDataType protoRowType;
  private @Nullable RelDataType rowType;
  private @Nullable List<RelDataType> fieldTypes;

  /** Creates a CsvTable. */
  CsvTable(Source source, @Nullable RelProtoDataType protoRowType) {
    this.source = source;
    this.protoRowType = protoRowType;
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
//    RelTupleType relTupleType = new RelTupleType();
//    RelDataType tupleType =
//        typeFactory.createTypeWithCharsetAndCollation(relTupleType,
//            typeFactory.getDefaultCharset(),
//            IMPLICIT);
    RelDataType tupleType = typeFactory.createJavaType(CsvTranslatableTable.Tuple.class);
    return typeFactory.builder().add("_TUPLE", tupleType).build();
  }

  /** Returns the field types of this CSV table. */
  public List<RelDataType> getFieldTypes(RelDataTypeFactory typeFactory) {
    if (fieldTypes == null) {
      fieldTypes = new ArrayList<>();
      CsvEnumerator.deduceRowType((JavaTypeFactory) typeFactory, source,
          fieldTypes, isStream());
    }
    return fieldTypes;
  }

  /** Returns whether the table represents a stream. */
  protected boolean isStream() {
    return false;
  }

  /** Various degrees of table "intelligence". */
  public enum Flavor {
    SCANNABLE, FILTERABLE, TRANSLATABLE, DYNAMIC
  }

  public static class RelTupleType extends RelDataTypeImpl {
    @Override
    public RelDataTypeFamily getFamily() {
      return SqlTypeFamily.CHARACTER;
    }

    @Override
    protected void generateTypeString(StringBuilder sb, boolean withDetail) {
      sb.append("[");
      sb.append("tuple");
      sb.append("]");
    }
  }
}

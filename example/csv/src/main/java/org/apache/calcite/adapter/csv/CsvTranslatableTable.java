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


import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.file.CsvEnumerator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.QueryableTable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.calcite.util.Source;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Table based on a CSV file.
 */
public class CsvTranslatableTable extends CsvTable
    implements QueryableTable, TranslatableTable {
  /** Creates a CsvTable. */
  CsvTranslatableTable(Source source, @Nullable RelProtoDataType protoRowType) {
    super(source, protoRowType);
  }

  @Override public String toString() {
    return "CsvTranslatableTable";
  }

  /** Returns an enumerable over a given projection of the fields. */
  @SuppressWarnings("unused") // called from generated code
  public Enumerable<Tuple> project(final DataContext root,
      final int[] fields) {
    final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root);
    return new AbstractEnumerable<Tuple>() {
      @Override public Enumerator<Tuple> enumerator() {
        return new MockEnumerator();
      }
    };
  }

  @Override public Expression getExpression(SchemaPlus schema, String tableName,
      Class clazz) {
    return Schemas.tableExpression(schema, getElementType(), tableName, clazz);
  }

  @Override public Type getElementType() {
    return Object[].class;
  }

  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
      SchemaPlus schema, String tableName) {
    throw new UnsupportedOperationException();
  }

  @Override public RelNode toRel(
      RelOptTable.ToRelContext context,
      RelOptTable relOptTable) {
    // Request all fields.
    final int fieldCount = relOptTable.getRowType().getFieldCount();
    final int[] fields = CsvEnumerator.identityList(fieldCount);
    return new CsvTableScan(context.getCluster(), relOptTable, this, fields);
  }


  public static class Tuple {
    private final Map<String, Object> data;

    public Tuple(Map<String, Object> data) {
      this.data = data;
    }

    public Object resolve(String fieldName) {
      return data.getOrDefault(fieldName, null);
    }

    @Override
    public String toString() {
      return "Tuple{" +
          "data=" + data +
          '}';
    }
  }

  /**
   * Static UDF function to resolve field values from a Tuple.
   * This function can be called from SQL as: resolve(_TUPLE, 'fieldName')
   *
   * @param tupleObj The Tuple object to resolve from (passed as Object)
   * @param fieldName The field name to resolve
   * @return The field value, or null if not found
   */
  public static Object resolve(Object tupleObj, String fieldName) {
    if (tupleObj == null) {
      return null;
    }
    if (tupleObj instanceof Tuple) {
      return ((Tuple) tupleObj).resolve(fieldName);
    }
    return null;
  }

  public static class MockEnumerator<Tuple>
      implements Enumerator<CsvTranslatableTable.Tuple> {

    private final List<CsvTranslatableTable.Tuple> list;
    {
      list = new ArrayList<>();
      list.add(new CsvTranslatableTable.Tuple(ImmutableMap.of("v", "1")));
      list.add(new CsvTranslatableTable.Tuple(ImmutableMap.of("v", "2")));
    }

    private final Iterator<CsvTranslatableTable.Tuple> iterator = list.iterator();
    private boolean hasNext = false;
    private CsvTranslatableTable.Tuple current;

    @Override
    public CsvTranslatableTable.Tuple current() {
      return current;
    }

    @Override
    public boolean moveNext() {
      if (iterator.hasNext()) {
        current = iterator.next();
        return true;
      }
      return false;
    }

    @Override
    public void reset() {
      // do nothing
    }

    @Override
    public void close() {
      // do nothing
    }
  }
}

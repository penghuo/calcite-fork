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
package org.apache.calcite.adapter.file;

import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.rel.type.DynamicRecordTypeImpl;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.util.Source;

import au.com.bytecode.opencsv.CSVReader;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enumerator that reads from a CSV file with dynamic field resolution.
 *
 * <p>This enumerator supports runtime field access where columns are resolved
 * based on the actual CSV header and the fields requested by the query.
 * Missing fields return NULL values instead of causing errors.
 */
public class CsvDynamicEnumerator implements Enumerator<@Nullable Object[]> {
  private final CSVReader reader;
  private final DynamicRecordTypeImpl rowType;
  private final RelDataTypeFactory typeFactory;
  private final AtomicBoolean cancelFlag;

  private @Nullable String[] headers;
  private @Nullable Map<String, Integer> headerIndexMap;
  private @Nullable Object[] current;

  public CsvDynamicEnumerator(Source source, DynamicRecordTypeImpl rowType,
      RelDataTypeFactory typeFactory) {
    this.rowType = rowType;
    this.typeFactory = typeFactory;
    this.cancelFlag = new AtomicBoolean(false);

    try {
      this.reader = openCsv(source);
      this.headers = reader.readNext(); // Read header row

      if (headers != null) {
        this.headerIndexMap = new HashMap<>(headers.length);
        for (int i = 0; i < headers.length; i++) {
          headerIndexMap.put(headers[i], i);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Error opening CSV file: " + source, e);
    }
  }

  private static CSVReader openCsv(Source source) throws IOException {
    return new CSVReader(source.reader());
  }

  @Override public @Nullable Object[] current() {
    return current;
  }

  @Override public boolean moveNext() {
    if (cancelFlag.get()) {
      return false;
    }

    try {
      final String[] strings = reader.readNext();
      if (strings == null) {
        current = null;
        reader.close();
        return false;
      }

      // Get the current field list from the dynamic row type
       List<RelDataTypeField> fields = rowType.getFieldList();

      // Create result array based on the fields requested by the query
      current = new Object[fields.size()];

      for (int i = 0; i < fields.size(); i++) {
        RelDataTypeField field = fields.get(i);
        String fieldName = field.getName();

        // Look up the field in the CSV headers
        Integer headerIndex = headerIndexMap != null ? headerIndexMap.get(fieldName) : null;

        if (headerIndex != null && headerIndex < strings.length) {
          // Field exists in CSV, return the actual value
          String value = strings[headerIndex];
          current[i] = convertValue(value);
        } else {
          // Field doesn't exist in CSV, return NULL
          current[i] = null;
        }
      }

      return true;
    } catch (IOException e) {
      throw new RuntimeException("Error reading CSV data", e);
    }
  }

  /**
   * Convert string value from CSV to appropriate Java type.
   * For dynamic tables, we keep it simple and return the string value,
   * letting Calcite handle type coercion as needed.
   */
  private @Nullable Object convertValue(@Nullable String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }

    // For dynamic tables, return the string value and let Calcite handle
    // type conversion through CAST operations in SQL
    return value;
  }

  @Override public void reset() {
    throw new UnsupportedOperationException("CSV reset not supported");
  }

  @Override public void close() {
    cancelFlag.set(true);
    try {
      if (reader != null) {
        reader.close();
      }
    } catch (IOException e) {
      throw new RuntimeException("Error closing CSV reader", e);
    }
  }
}

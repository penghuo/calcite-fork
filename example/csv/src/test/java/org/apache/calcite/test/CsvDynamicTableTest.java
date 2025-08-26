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
package org.apache.calcite.test;

import org.apache.calcite.adapter.csv.CsvSchemaFactory;

import com.google.common.collect.ImmutableMap;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for CSV Dynamic Tables.
 */
class CsvDynamicTableTest {

  @Test void testDynamicSchemaLess() throws SQLException, IOException {
    // Create a temporary CSV file for testing
    File tempDir = createTempDirectory();
    File csvFile = new File(tempDir, "test.csv");

    try (PrintWriter writer = new PrintWriter(csvFile)) {
      writer.println("id,name,value"); // header
      writer.println("1,John,100");
      writer.println("2,Jane,200");
      writer.println("3,Bob,300");
    }

    Connection connection = createConnection(tempDir, "DYNAMIC");

    try {
      Statement statement = connection.createStatement();

      // Test 1: Access existing columns
      ResultSet resultSet = statement.executeQuery(
          "SELECT id, name, id1 FROM test");

      assertThat(resultSet.next(), is(true));
      assertThat(resultSet.getString("id"), is("1"));
      assertThat(resultSet.getString("name"), is("John"));
      assertThat(resultSet.getString("value"), is("100"));

      assertThat(resultSet.next(), is(true));
      assertThat(resultSet.getString("id"), is("2"));
      assertThat(resultSet.getString("name"), is("Jane"));
      assertThat(resultSet.getString("value"), is("200"));

      resultSet.close();

      // Test 2: Access mix of existing and non-existing columns
      resultSet = statement.executeQuery(
          "SELECT id, nonexistent_column, name FROM test");

      assertThat(resultSet.next(), is(true));
      assertThat(resultSet.getString("id"), is("1"));
      assertThat(resultSet.getString("nonexistent_column"), nullValue());
      assertThat(resultSet.getString("name"), is("John"));

      resultSet.close();

      // Test 3: Access only non-existent columns (should return NULLs)
      resultSet = statement.executeQuery(
          "SELECT missing1, missing2 FROM test");

      assertThat(resultSet.next(), is(true));
      assertThat(resultSet.getString("missing1"), nullValue());
      assertThat(resultSet.getString("missing2"), nullValue());

      resultSet.close();
      statement.close();

    } finally {
      connection.close();
      // Clean up
      csvFile.delete();
      tempDir.delete();
    }
  }

  @Test void testDynamicWithTypeCasting() throws SQLException, IOException {
    File tempDir = createTempDirectory();
    File csvFile = new File(tempDir, "numbers.csv");

    try (PrintWriter writer = new PrintWriter(csvFile)) {
      writer.println("id,price,description");
      writer.println("1,19.99,Widget");
      writer.println("2,25.50,Gadget");
    }

    Connection connection = createConnection(tempDir, "DYNAMIC");

    try {
      Statement statement = connection.createStatement();

      // Test type conversion with CAST
      ResultSet resultSet = statement.executeQuery(
          "SELECT CAST(id AS INTEGER) as id_int, "
          + "CAST(price AS DECIMAL(10,2)) as price_decimal, "
          + "description, "
          + "CAST(missing_field AS INTEGER) as missing_int "
          + "FROM numbers");

      assertThat(resultSet.next(), is(true));
      assertThat(resultSet.getInt("id_int"), is(1));
      assertThat(resultSet.getBigDecimal("price_decimal").toString(), is("19.99"));
      assertThat(resultSet.getString("description"), is("Widget"));
      assertThat(resultSet.getObject("missing_int"), nullValue());

      resultSet.close();
      statement.close();

    } finally {
      connection.close();
      csvFile.delete();
      tempDir.delete();
    }
  }

  private File createTempDirectory() throws IOException {
    File tempDir = File.createTempFile("csv-test", ".tmp");
    tempDir.delete();
    tempDir.mkdirs();
    tempDir.deleteOnExit();
    return tempDir;
  }

  private Connection createConnection(File directory, String flavor) throws SQLException {
    Properties info = new Properties();
    info.put("model", jsonPath(directory, flavor));
    return DriverManager.getConnection("jdbc:calcite:", info);
  }

  private String jsonPath(File directory, String flavor) {
    return "inline:"
        + "{\n"
        + "  version: '1.0',\n"
        + "  defaultSchema: 'SALES',\n"
        + "  schemas: [\n"
        + "    {\n"
        + "      name: 'SALES',\n"
        + "      type: 'custom',\n"
        + "      factory: '" + CsvSchemaFactory.class.getName() + "',\n"
        + "      operand: {\n"
        + "        directory: '" + directory.getAbsolutePath() + "',\n"
        + "        flavor: '" + flavor + "'\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
  }
}

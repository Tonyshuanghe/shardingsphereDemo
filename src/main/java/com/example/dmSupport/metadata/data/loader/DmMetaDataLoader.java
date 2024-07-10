/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.dmSupport.metadata.data.loader;

import com.example.constant.CommonConstants;
import com.google.common.collect.Lists;
import org.apache.shardingsphere.infra.database.core.GlobalDataSourceRegistry;
import org.apache.shardingsphere.infra.database.core.metadata.data.loader.DialectMetaDataLoader;
import org.apache.shardingsphere.infra.database.core.metadata.data.loader.MetaDataLoaderConnection;
import org.apache.shardingsphere.infra.database.core.metadata.data.loader.MetaDataLoaderMaterial;
import org.apache.shardingsphere.infra.database.core.metadata.data.model.ColumnMetaData;
import org.apache.shardingsphere.infra.database.core.metadata.data.model.ConstraintMetaData;
import org.apache.shardingsphere.infra.database.core.metadata.data.model.IndexMetaData;
import org.apache.shardingsphere.infra.database.core.metadata.data.model.SchemaMetaData;
import org.apache.shardingsphere.infra.database.core.metadata.data.model.TableMetaData;
import org.apache.shardingsphere.infra.database.core.metadata.database.datatype.DataTypeLoader;
import org.apache.shardingsphere.infra.database.core.type.DatabaseType;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Meta data loader for dm.
 */
public final class DmMetaDataLoader implements DialectMetaDataLoader {
    private static final String TABLE_META_DATA_SQL_NO_ORDER =
            "SELECT OWNER AS TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, NULLABLE, DATA_TYPE, COLUMN_ID, HIDDEN_COLUMN %s FROM ALL_TAB_COLS WHERE OWNER = ?";

    private static final String ORDER_BY_COLUMN_ID = " ORDER BY COLUMN_ID";

    private static final String TABLE_META_DATA_SQL = TABLE_META_DATA_SQL_NO_ORDER + ORDER_BY_COLUMN_ID;

    private static final String TABLE_META_DATA_SQL_IN_TABLES = TABLE_META_DATA_SQL_NO_ORDER + " AND TABLE_NAME IN (%s)" + ORDER_BY_COLUMN_ID;

    private static final String INDEX_META_DATA_SQL = "SELECT OWNER AS TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, UNIQUENESS FROM ALL_INDEXES WHERE OWNER = ? AND TABLE_NAME IN (%s)";

    private static final String PRIMARY_KEY_META_DATA_SQL = "SELECT A.OWNER AS TABLE_SCHEMA, A.TABLE_NAME AS TABLE_NAME, B.COLUMN_NAME AS COLUMN_NAME FROM ALL_CONSTRAINTS A INNER JOIN"
            + " ALL_CONS_COLUMNS B ON A.CONSTRAINT_NAME = B.CONSTRAINT_NAME WHERE CONSTRAINT_TYPE = 'P' AND A.OWNER = '%s'";

    private static final String PRIMARY_KEY_META_DATA_SQL_IN_TABLES = PRIMARY_KEY_META_DATA_SQL + " AND A.TABLE_NAME IN (%s)";

    private static final String INDEX_COLUMN_META_DATA_SQL = "SELECT COLUMN_NAME FROM ALL_IND_COLUMNS WHERE INDEX_OWNER = ? AND TABLE_NAME = ? AND INDEX_NAME = ?";

    private static final int COLLATION_START_MAJOR_VERSION = 12;

    private static final int COLLATION_START_MINOR_VERSION = 2;

    private static final int IDENTITY_COLUMN_START_MINOR_VERSION = 1;

    private static final int MAX_EXPRESSION_SIZE = 1000;

    @Override
    public Collection<SchemaMetaData> load(final MetaDataLoaderMaterial material) throws SQLException {
        Map<String, Collection<ColumnMetaData>> columnMetaDataMap = new HashMap<>(material.getActualTableNames().size(), 1F);
        Map<String, Collection<IndexMetaData>> indexMetaDataMap = new HashMap<>(material.getActualTableNames().size(), 1F);
        try (Connection connection = new MetaDataLoaderConnection(TypedSPILoader.getService(DatabaseType.class, CommonConstants.DM), material.getDataSource().getConnection())) {
            for (List<String> each : Lists.partition(new ArrayList<>(material.getActualTableNames()), MAX_EXPRESSION_SIZE)) {
                columnMetaDataMap.putAll(loadColumnMetaDataMap(connection, each));
                indexMetaDataMap.putAll(loadIndexMetaData(connection, each));
            }
        }
        Collection<TableMetaData> tableMetaDataList = new LinkedList<>();
        for (Entry<String, Collection<ColumnMetaData>> entry : columnMetaDataMap.entrySet()) {
            tableMetaDataList.add(new TableMetaData(entry.getKey(), entry.getValue(), indexMetaDataMap.getOrDefault(entry.getKey(), Collections.emptyList()), Collections.emptyList()));
        }
        // TODO Load views from dm database.
        return Collections.singletonList(new SchemaMetaData(material.getDefaultSchemaName(), tableMetaDataList));
    }

    private Map<String, Collection<ColumnMetaData>> loadColumnMetaDataMap(final Connection connection, final Collection<String> tables) throws SQLException {
        Map<String, Collection<ColumnMetaData>> result = new HashMap<>(tables.size(), 1F);
        try (PreparedStatement preparedStatement = connection.prepareStatement(getTableMetaDataSQL(tables, connection.getMetaData()))) {
            Map<String, Integer> dataTypes = new DataTypeLoader().load(connection.getMetaData(), getType());
            Map<String, Collection<String>> tablePrimaryKeys = loadTablePrimaryKeys(connection, tables);
            preparedStatement.setString(1, connection.getSchema());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    ColumnMetaData columnMetaData = loadColumnMetaData(dataTypes, resultSet, tablePrimaryKeys.getOrDefault(tableName, Collections.emptyList()), connection.getMetaData());
                    if (!result.containsKey(tableName)) {
                        result.put(tableName, new LinkedList<>());
                    }
                    result.get(tableName).add(columnMetaData);
                }
            }
        }
        return result;
    }

    private ColumnMetaData loadColumnMetaData(final Map<String, Integer> dataTypeMap, final ResultSet resultSet, final Collection<String> primaryKeys,
                                              final DatabaseMetaData databaseMetaData) throws SQLException {
        String columnName = resultSet.getString("COLUMN_NAME");
        String dataType = getOriginalDataType(resultSet.getString("DATA_TYPE"));
        boolean primaryKey = primaryKeys.contains(columnName);
        boolean generated = versionContainsIdentityColumn(databaseMetaData) && "YES".equals(resultSet.getString("IDENTITY_COLUMN"));
        // TODO need to support caseSensitive when version < 12.2.
        String collation = versionContainsCollation(databaseMetaData) ? resultSet.getString("COLLATION") : null;
        boolean caseSensitive = null != collation && collation.endsWith("_CS");
        boolean isVisible = "NO".equals(resultSet.getString("HIDDEN_COLUMN"));
        boolean nullable = "Y".equals(resultSet.getString("NULLABLE"));
        return new ColumnMetaData(columnName, dataTypeMap.get(dataType), primaryKey, generated, caseSensitive, isVisible, false, nullable);
    }

    private String getOriginalDataType(final String dataType) {
        int index = dataType.indexOf('(');
        if (index > 0) {
            return dataType.substring(0, index);
        }
        return dataType;
    }

    private String getTableMetaDataSQL(final Collection<String> tables, final DatabaseMetaData databaseMetaData) throws SQLException {
        StringBuilder stringBuilder = new StringBuilder(28);
        if (versionContainsIdentityColumn(databaseMetaData)) {
            stringBuilder.append(", IDENTITY_COLUMN");
        }
        if (versionContainsCollation(databaseMetaData)) {
            stringBuilder.append(", COLLATION");
        }
        String collation = stringBuilder.toString();
        return tables.isEmpty() ? String.format(TABLE_META_DATA_SQL, collation)
                : String.format(TABLE_META_DATA_SQL_IN_TABLES, collation, tables.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(",")));
    }

    private boolean versionContainsCollation(final DatabaseMetaData databaseMetaData) throws SQLException {
        return databaseMetaData.getDatabaseMajorVersion() >= COLLATION_START_MAJOR_VERSION && databaseMetaData.getDatabaseMinorVersion() >= COLLATION_START_MINOR_VERSION;
    }

    private boolean versionContainsIdentityColumn(final DatabaseMetaData databaseMetaData) throws SQLException {
        return databaseMetaData.getDatabaseMajorVersion() >= COLLATION_START_MAJOR_VERSION && databaseMetaData.getDatabaseMinorVersion() >= IDENTITY_COLUMN_START_MINOR_VERSION;
    }

    private Map<String, Collection<IndexMetaData>> loadIndexMetaData(final Connection connection, final Collection<String> tableNames) throws SQLException {
        Map<String, Collection<IndexMetaData>> result = new HashMap<>(tableNames.size(), 1F);
        try (PreparedStatement preparedStatement = connection.prepareStatement(getIndexMetaDataSQL(tableNames))) {
            preparedStatement.setString(1, connection.getSchema());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String indexName = resultSet.getString("INDEX_NAME");
                    String tableName = resultSet.getString("TABLE_NAME");
                    boolean isUnique = "UNIQUE".equals(resultSet.getString("UNIQUENESS"));
                    if (!result.containsKey(tableName)) {
                        result.put(tableName, new LinkedList<>());
                    }
                    IndexMetaData indexMetaData = new IndexMetaData(indexName);
                    indexMetaData.setUnique(isUnique);
                    indexMetaData.getColumns().addAll(loadIndexColumnNames(connection, tableName, indexName));
                    result.get(tableName).add(indexMetaData);
                }
            }
        }
        return result;
    }

    private List<String> loadIndexColumnNames(final Connection connection, final String tableName, final String indexName) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(INDEX_COLUMN_META_DATA_SQL)) {
            preparedStatement.setString(1, connection.getSchema());
            preparedStatement.setString(2, tableName);
            preparedStatement.setString(3, indexName);
            List<String> result = new LinkedList<>();
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                result.add(resultSet.getString("COLUMN_NAME"));
            }
            return result;
        }
    }

    private String getIndexMetaDataSQL(final Collection<String> tableNames) {
        // TODO The table name needs to be in uppercase, otherwise the index cannot be found.
        return String.format(INDEX_META_DATA_SQL, tableNames.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(",")));
    }

    private Map<String, Collection<String>> loadTablePrimaryKeys(final Connection connection, final Collection<String> tableNames) throws SQLException {
        Map<String, Collection<String>> result = new HashMap<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(getPrimaryKeyMetaDataSQL(connection.getSchema(), tableNames))) {
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String columnName = resultSet.getString("COLUMN_NAME");
                    String tableName = resultSet.getString("TABLE_NAME");
                    result.computeIfAbsent(tableName, k -> new LinkedList<>()).add(columnName);
                }
            }
        }
        return result;
    }

    private String getPrimaryKeyMetaDataSQL(final String schemaName, final Collection<String> tables) {
        return tables.isEmpty() ? String.format(PRIMARY_KEY_META_DATA_SQL, schemaName)
                : String.format(PRIMARY_KEY_META_DATA_SQL_IN_TABLES, schemaName, tables.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(",")));
    }

    @Override
    public String getDatabaseType() {
        return CommonConstants.DM;
    }
}
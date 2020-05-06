package com.dremio.exec.store.jdbc.conf;

import javax.sql.DataSource;

import com.dremio.exec.store.jdbc.JdbcSchemaFetcher;
import com.dremio.exec.store.jdbc.JdbcStoragePlugin;
import com.dremio.exec.store.jdbc.JdbcStoragePlugin.Config;
import com.dremio.exec.store.jdbc.dialect.arp.ArpDialect;
import com.dremio.exec.store.jdbc.dialect.arp.ArpYaml;


/**
 * Custom Dialect for BigQuery.
 * See Apache Calcite project for Dialect info
 * https://github.com/apache/calcite/blob/4208d0ba6f2a749692fe64181a1373af07d55db5/core/src/main/java/org/apache/calcite/sql/dialect/BigQuerySqlDialect.java
 */
public class BigQueryDialect extends ArpDialect {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BigQueryDialect.class);

  public BigQueryDialect(final ArpYaml yaml) {
    super(yaml);
  }

  /**
   * Custom Dialect for BigQuery.
   * The following block is required as BigQuery reports integers as NUMBER(38,0).
   */
  static class BigQuerySchemaFetcher extends JdbcSchemaFetcher {
    public BigQuerySchemaFetcher(String name, DataSource dataSource, int timeout, Config config) {
      super(name, dataSource, timeout, config);
    }

    @Override
    protected boolean usePrepareForColumnMetadata() {
      return true;
    }
  }

  @Override
  public JdbcSchemaFetcher getSchemaFetcher(final String name, final DataSource dataSource, final int timeout,
      final JdbcStoragePlugin.Config config) {
    return new BigQuerySchemaFetcher(name, dataSource, timeout, config);
  }

  @Override
  public boolean supportsNestedAggregations() {
    return false;
  }
}

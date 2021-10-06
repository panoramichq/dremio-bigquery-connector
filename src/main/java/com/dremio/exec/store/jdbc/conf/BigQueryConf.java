package com.dremio.exec.store.jdbc.conf;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.hibernate.validator.constraints.NotBlank;

import com.dremio.options.OptionManager;
import com.dremio.security.CredentialsService;

import com.dremio.exec.catalog.conf.DisplayMetadata;
import com.dremio.exec.catalog.conf.NotMetadataImpacting;
import com.dremio.exec.catalog.conf.Secret;
import com.dremio.exec.catalog.conf.SourceType;
import com.dremio.exec.server.SabotContext;
import com.dremio.exec.store.jdbc.CloseableDataSource;
import com.dremio.exec.store.jdbc.DataSources;
import com.dremio.exec.store.jdbc.JdbcPluginConfig;
import com.dremio.exec.store.jdbc.JdbcStoragePlugin;
import com.dremio.exec.store.jdbc.dialect.arp.ArpDialect;
import com.google.common.annotations.VisibleForTesting;

import io.protostuff.Tag;

/**
 * Configuration for BigQuery.
 */
@SourceType(value = "BIGQUERY", label = "BigQuery", uiConfig = "bigquery-layout.json", externalQuerySupported = true)
public class BigQueryConf extends AbstractArpConf<BigQueryConf> {

  private static final String ARP_FILENAME = "arp/implementation/bigquery-arp.yaml";
  private static final ArpDialect ARP_DIALECT =
      AbstractArpConf.loadArpFile(ARP_FILENAME, (BigQueryDialect::new));
  private static final String DRIVER = "com.simba.googlebigquery.jdbc42.Driver";
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BigQueryConf.class);

  /*
     Check BigQuery JDBC docs for more details
   */
  @NotBlank
  @Tag(1)
  @DisplayMetadata(label = "Project ID (Ex: MyBigQueryProject)")
  public String projectId;

  @NotBlank
  @Tag(2)
  @DisplayMetadata(label = "Service Account Email Address")
  @NotMetadataImpacting
  public String email;

  @NotBlank
  @Tag(3)
  @Secret
  @DisplayMetadata(label = "Service Account JSON Key")
  @NotMetadataImpacting
  public String jsonKey;

  @Tag(4)
  @DisplayMetadata(label = "Customer KMS Key Name")
  @NotMetadataImpacting
  public String customerKmsKeyName;

  @Tag(5)
  @DisplayMetadata(label = "Record fetch size")
  @NotMetadataImpacting
  public int fetchSize = 2000;

  @Tag(6)
  @DisplayMetadata(label = "Query timeout (s)")
  @NotMetadataImpacting
  public int queryTimeout = 10;


  @Tag(7)
  @DisplayMetadata(label = "Maximum idle connections")
  @NotMetadataImpacting
  public int maxIdleConns = 8;

  @Tag(8)
  @DisplayMetadata(label = "Connection idle time (s)")
  @NotMetadataImpacting
  public int idleTimeSec = 60;
  
  @VisibleForTesting
  public String toJdbcConnectionString() {
    final String prjId = checkNotNull(this.projectId, "Missing project ID.");
    final String emailAddr = checkNotNull(this.email, "Missing service account email address.");
    checkNotNull(this.jsonKey, "Missing service account JSON key");
    String keyPath = null;
    try {
      keyPath = getOauthPrivateKeyPath();
    } catch (final IOException e) {
      // This will cause an error in auth, no need to do anything here...
      logger.warn("Unable to write BigQuery authentication key", e);
    }

    // Setup the parameters required to connect to BigQuery
    final Map<String, String> attributes = new HashMap<>();
    attributes.put("ProjectId", prjId);
    attributes.put("OAuthServiceAcctEmail", emailAddr);
    attributes.put("OAuthPvtKeyPath", keyPath);
    // Service-account based auth
    attributes.put("OAuthType", "0");
    // Query timeout
    attributes.put("Timeout", Integer.toString(this.queryTimeout));
    // Allow result sets larger than 128MB
    attributes.put("AllowLargeResults", "1");
    // Use modern BQ SQL dialect
    attributes.put("QueryDialect", "SQL");
    // Check if we have a custom KMS key, use it if provided
    if (this.customerKmsKeyName != null && !this.customerKmsKeyName.isEmpty()) {
      attributes.put("KMSKeyName", this.customerKmsKeyName);
    }

    // Compile the final connection string
    final String connectAttribs = attributes.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).collect(joining(";"));
    return String.format("jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;%s;", connectAttribs);
  }

  @Override
  @VisibleForTesting
  public JdbcPluginConfig buildPluginConfig(
            JdbcPluginConfig.Builder configBuilder,
            CredentialsService credentialsService,
            OptionManager optionManager
  ) {
    logger.info("Connecting to BigQuery");
    return configBuilder.withDialect(getDialect())
        .withDialect(getDialect())
        .withFetchSize(fetchSize)
        .withDatasourceFactory(this::newDataSource)
        .clearHiddenSchemas()
        .addHiddenSchema("SYSTEM")
        .build();
  }

  private String getOauthPrivateKeyPath() throws IOException {
    final File temp = File.createTempFile("bqk", ".json");
    temp.deleteOnExit();
    final BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
    bw.write(this.jsonKey);
    bw.close();
    return temp.getAbsolutePath();
  }

  private CloseableDataSource newDataSource() {
    final Properties properties = new Properties();
    return DataSources.newGenericConnectionPoolDataSource(DRIVER,
        toJdbcConnectionString(), null, null, properties,
        DataSources.CommitMode.DRIVER_SPECIFIED_COMMIT_MODE, maxIdleConns, idleTimeSec);
  }

  @Override
  public ArpDialect getDialect() {
    return ARP_DIALECT;
  }

  @VisibleForTesting
  public static ArpDialect getDialectSingleton() {
    return ARP_DIALECT;
  }
}

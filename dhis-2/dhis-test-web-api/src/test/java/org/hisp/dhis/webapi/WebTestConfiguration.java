/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.webapi;

import java.beans.PropertyVetoException;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.hisp.dhis.association.jdbc.JdbcOrgUnitAssociationStoreConfiguration;
import org.hisp.dhis.commons.jackson.config.JacksonObjectMapperConfig;
import org.hisp.dhis.commons.util.DebugUtils;
import org.hisp.dhis.config.AnalyticsDataSourceConfig;
import org.hisp.dhis.config.DataSourceConfig;
import org.hisp.dhis.config.HibernateConfig;
import org.hisp.dhis.config.HibernateEncryptionConfig;
import org.hisp.dhis.config.ServiceConfig;
import org.hisp.dhis.config.StartupConfig;
import org.hisp.dhis.config.StoreConfig;
import org.hisp.dhis.configuration.NotifierConfiguration;
import org.hisp.dhis.datasource.DatabasePoolUtils;
import org.hisp.dhis.db.migration.config.FlywayConfig;
import org.hisp.dhis.external.conf.ConfigurationKey;
import org.hisp.dhis.external.conf.DhisConfigurationProvider;
import org.hisp.dhis.h2.H2SqlFunction;
import org.hisp.dhis.jdbc.config.JdbcConfig;
import org.hisp.dhis.leader.election.LeaderElectionConfiguration;
import org.hisp.dhis.security.Authorities;
import org.hisp.dhis.security.SystemAuthoritiesProvider;
import org.hisp.dhis.webapi.mvc.ContentNegotiationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Gintare Vilkelyte <vilkelyte.gintare@gmail.com
 */
@Configuration
@ImportResource(locations = {"classpath*:/META-INF/dhis/beans.xml"})
@ComponentScan(
    basePackages = {"org.hisp.dhis"},
    useDefaultFilters = false,
    includeFilters = {
      @Filter(type = FilterType.ANNOTATION, value = Service.class),
      @Filter(type = FilterType.ANNOTATION, value = Component.class),
      @Filter(type = FilterType.ANNOTATION, value = Repository.class)
    },
    excludeFilters = @Filter(Configuration.class))
@Import({
  HibernateConfig.class,
  DataSourceConfig.class,
  AnalyticsDataSourceConfig.class,
  JdbcConfig.class,
  FlywayConfig.class,
  HibernateEncryptionConfig.class,
  ServiceConfig.class,
  StoreConfig.class,
  LeaderElectionConfiguration.class,
  NotifierConfiguration.class,
  org.hisp.dhis.setting.config.ServiceConfig.class,
  org.hisp.dhis.external.config.ServiceConfig.class,
  org.hisp.dhis.dxf2.config.ServiceConfig.class,
  org.hisp.dhis.support.config.ServiceConfig.class,
  org.hisp.dhis.validation.config.ServiceConfig.class,
  org.hisp.dhis.validation.config.StoreConfig.class,
  org.hisp.dhis.programrule.config.ProgramRuleConfig.class,
  org.hisp.dhis.reporting.config.StoreConfig.class,
  org.hisp.dhis.analytics.config.ServiceConfig.class,
  JacksonObjectMapperConfig.class,
  ContentNegotiationConfig.class,
  JdbcOrgUnitAssociationStoreConfiguration.class,
  StartupConfig.class
})
@Transactional
@Slf4j
@Order(10)
public class WebTestConfiguration {
  @Bean
  public static SessionRegistry sessionRegistry() {
    return new org.springframework.security.core.session.SessionRegistryImpl();
  }

  @Autowired private DhisConfigurationProvider dhisConfigurationProvider;

  @Bean(name = {"namedParameterJdbcTemplate", "analyticsNamedParameterJdbcTemplate"})
  @Primary
  public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
      @Qualifier("dataSource") DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  @Bean(name = {"dataSource", "analyticsDataSource"})
  @Primary
  public DataSource actualDataSource() {
    final DhisConfigurationProvider config = dhisConfigurationProvider;
    String jdbcUrl = config.getProperty(ConfigurationKey.CONNECTION_URL);
    String username = config.getProperty(ConfigurationKey.CONNECTION_USERNAME);
    String dbPoolType = config.getProperty(ConfigurationKey.DB_POOL_TYPE);

    DatabasePoolUtils.PoolConfig.PoolConfigBuilder builder = DatabasePoolUtils.PoolConfig.builder();
    builder.dhisConfig(config);
    builder.dbPoolType(dbPoolType);

    try {
      final DataSource dbPool = DatabasePoolUtils.createDbPool(builder.build());

      // H2 JSON functions
      H2SqlFunction.registerH2Functions(dbPool);

      return dbPool;
    } catch (SQLException | PropertyVetoException e) {
      String message =
          String.format(
              "Connection test failed for main database pool, " + "jdbcUrl: '%s', user: '%s'",
              jdbcUrl, username);

      log.error(message);
      log.error(DebugUtils.getStackTrace(e));

      throw new IllegalStateException(message, e);
    }
  }

  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  //  @Bean
  //  public LdapAuthenticator ldapAuthenticator() {
  //    return authentication -> null;
  //  }

  //  @Bean
  //  public LdapAuthoritiesPopulator ldapAuthoritiesPopulator() {
  //    return (dirContextOperations, s) -> null;
  //  }

  @Bean
  public DefaultAuthenticationEventPublisher authenticationEventPublisher() {
    DefaultAuthenticationEventPublisher defaultAuthenticationEventPublisher =
        new DefaultAuthenticationEventPublisher();
    defaultAuthenticationEventPublisher.setAdditionalExceptionMappings(
        Map.of(
            OAuth2AuthenticationException.class, AuthenticationFailureBadCredentialsEvent.class));
    return defaultAuthenticationEventPublisher;
  }

  @Bean
  public SystemAuthoritiesProvider systemAuthoritiesProvider() {
    return Authorities::getAllAuthorities;
  }
}

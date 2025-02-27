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
package org.hisp.dhis.trackedentity.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;
import static org.hisp.dhis.common.IdentifiableObjectUtils.getIdentifiers;
import static org.hisp.dhis.commons.util.TextUtils.getCommaDelimitedString;
import static org.hisp.dhis.commons.util.TextUtils.getQuotedCommaDelimitedString;
import static org.hisp.dhis.commons.util.TextUtils.getTokens;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.CREATED_ID;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.DELETED;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.INACTIVE_ID;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.LAST_UPDATED_ID;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.MAIN_QUERY_ALIAS;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.ORG_UNIT_ID;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.ORG_UNIT_NAME;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.OrderColumn.ENROLLED_AT;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.OrderColumn.findColumn;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.POTENTIAL_DUPLICATE;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.PROGRAM_INSTANCE_ALIAS;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.TRACKED_ENTITY_ID;
import static org.hisp.dhis.trackedentity.TrackedEntityQueryParams.TRACKED_ENTITY_TYPE_ID;
import static org.hisp.dhis.util.DateUtils.getLongDateString;
import static org.hisp.dhis.util.DateUtils.getLongGmtDateString;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.query.Query;
import org.hisp.dhis.common.AssignedUserSelectionMode;
import org.hisp.dhis.common.DimensionalItemObject;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.common.OrganisationUnitSelectionMode;
import org.hisp.dhis.common.QueryFilter;
import org.hisp.dhis.common.QueryItem;
import org.hisp.dhis.common.QueryOperator;
import org.hisp.dhis.common.hibernate.SoftDeleteHibernateObjectStore;
import org.hisp.dhis.commons.collection.CollectionUtils;
import org.hisp.dhis.commons.util.SqlHelper;
import org.hisp.dhis.event.EventStatus;
import org.hisp.dhis.jdbc.StatementBuilder;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitStore;
import org.hisp.dhis.security.acl.AclService;
import org.hisp.dhis.setting.SettingKey;
import org.hisp.dhis.setting.SystemSettingManager;
import org.hisp.dhis.trackedentity.TrackedEntity;
import org.hisp.dhis.trackedentity.TrackedEntityQueryParams;
import org.hisp.dhis.trackedentity.TrackedEntityStore;
import org.hisp.dhis.user.User;
import org.hisp.dhis.util.DateUtils;
import org.hisp.dhis.webapi.controller.event.mapper.OrderParam;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;

/**
 * @author Abyot Asalefew Gizaw
 */
@Slf4j
@Repository("org.hisp.dhis.trackedentity.TrackedEntityStore")
public class HibernateTrackedEntityStore extends SoftDeleteHibernateObjectStore<TrackedEntity>
    implements TrackedEntityStore {
  private static final String TE_HQL_BY_UIDS = "from TrackedEntity as te where te.uid in (:uids)";

  private static final String TE_HQL_BY_IDS = "from TrackedEntity as te where te.id in (:ids)";

  private static final String OFFSET = "OFFSET";

  private static final String LIMIT = "LIMIT";

  private static final String EV_EXECUTIONDATE = "EV.occurreddate";

  private static final String EV_DUEDATE = "EV.scheduleddate";

  private static final String IS_NULL = "IS NULL";

  private static final String IS_NOT_NULL = "IS NOT NULL";

  private static final String SPACE = " ";

  private static final String SINGLE_QUOTE = "'";

  private static final String EQUALS = " = ";

  private static final String EV_STATUS = "EV.status";

  private static final String UID_VALUE_SEPARATOR = ":";

  private static final String UID_VALUE_PAIR_SEPARATOR = ";@//@;";

  // -------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------

  private final OrganisationUnitStore organisationUnitStore;

  private final StatementBuilder statementBuilder;

  private final SystemSettingManager systemSettingManager;

  // TODO too many arguments in constructor. This needs to be refactored.
  public HibernateTrackedEntityStore(
      EntityManager entityManager,
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher publisher,
      AclService aclService,
      StatementBuilder statementBuilder,
      OrganisationUnitStore organisationUnitStore,
      SystemSettingManager systemSettingManager) {
    super(entityManager, jdbcTemplate, publisher, TrackedEntity.class, aclService, false);

    checkNotNull(statementBuilder);
    checkNotNull(organisationUnitStore);
    checkNotNull(systemSettingManager);

    this.statementBuilder = statementBuilder;
    this.organisationUnitStore = organisationUnitStore;
    this.systemSettingManager = systemSettingManager;
  }

  // -------------------------------------------------------------------------
  // Implementation methods
  // -------------------------------------------------------------------------

  @Override
  public List<TrackedEntity> getTrackedEntities(TrackedEntityQueryParams params) {
    List<Long> teIds = getTrackedEntityIds(params);
    List<TrackedEntity> sortedTe = new ArrayList<>();
    List<List<Long>> idsPartitions = Lists.partition(Lists.newArrayList(teIds), 20000);

    for (List<Long> idsPartition : idsPartitions) {
      if (!idsPartition.isEmpty()) {
        List<TrackedEntity> trackedEntities =
            getSession()
                .createQuery(TE_HQL_BY_IDS, TrackedEntity.class)
                .setParameter("ids", idsPartition)
                .list();

        trackedEntities.sort(comparing(te -> idsPartition.indexOf(te.getId())));

        sortedTe.addAll(trackedEntities);
      }
    }

    return sortedTe;
  }

  @Override
  public List<Long> getTrackedEntityIds(TrackedEntityQueryParams params) {
    String sql = getQuery(params, false);
    log.debug("Tracked entity query SQL: " + sql);
    SqlRowSet rowSet = jdbcTemplate.queryForRowSet(sql);

    checkMaxTeiCountReached(params, rowSet);

    List<Long> ids = new ArrayList<>();

    while (rowSet.next()) {
      ids.add(rowSet.getLong("teId"));
    }

    return ids;
  }

  private String encodeAndQuote(Collection<String> elements) {
    return getQuotedCommaDelimitedString(
        elements.stream()
            .map(element -> statementBuilder.encode(element, false))
            .collect(Collectors.toList()));
  }

  @Override
  public List<Map<String, String>> getTrackedEntitiesGrid(TrackedEntityQueryParams params) {
    String sql = getQuery(params, true);
    log.debug("Tracked entity query SQL: " + sql);

    SqlRowSet rowSet = jdbcTemplate.queryForRowSet(sql);

    checkMaxTeiCountReached(params, rowSet);

    List<Map<String, String>> list = new ArrayList<>();

    while (rowSet.next()) {
      final Map<String, String> map = new HashMap<>();

      map.put(TRACKED_ENTITY_ID, rowSet.getString(TRACKED_ENTITY_ID));
      map.put(CREATED_ID, rowSet.getString(CREATED_ID));
      map.put(LAST_UPDATED_ID, rowSet.getString(LAST_UPDATED_ID));
      map.put(ORG_UNIT_ID, rowSet.getString(ORG_UNIT_ID));
      map.put(ORG_UNIT_NAME, rowSet.getString(ORG_UNIT_NAME));
      map.put(TRACKED_ENTITY_TYPE_ID, rowSet.getString(TRACKED_ENTITY_TYPE_ID));
      map.put(INACTIVE_ID, rowSet.getString(INACTIVE_ID));
      map.put(POTENTIAL_DUPLICATE, rowSet.getString(POTENTIAL_DUPLICATE));

      if (params.isIncludeDeleted()) {
        map.put(DELETED, rowSet.getString(DELETED));
      }

      if (!params.getAttributesAndFilters().isEmpty()) {
        HashMap<String, String> attributeValues = new HashMap<>();
        String teavString = rowSet.getString("tea_values");

        extractFromTeavAggregatedString(attributeValues, teavString);

        for (QueryItem item : params.getAttributes()) {
          map.put(
              item.getItemId(),
              isOrgUnit(item) && attributeValues.containsKey(item.getItemId())
                  ? getOrgUnitNameByUid(attributeValues.get(item.getItemId()))
                  : attributeValues.get(item.getItemId()));
        }
      }

      list.add(map);
    }

    return list;
  }

  private void extractFromTeavAggregatedString(
      HashMap<String, String> attributeValues, String teavString) {
    if (teavString != null) {
      String[] pairs = teavString.split(UID_VALUE_PAIR_SEPARATOR);

      for (String pair : pairs) {
        String[] teav = pair.split(UID_VALUE_SEPARATOR, 2);

        if (teav.length == 2) {
          attributeValues.put(teav[0], teav[1]);
        }
      }
    }
  }

  private void checkMaxTeiCountReached(TrackedEntityQueryParams params, SqlRowSet rowSet) {
    if (params.getMaxTeLimit() > 0 && rowSet.last()) {
      if (rowSet.getRow() > params.getMaxTeLimit()) {
        throw new IllegalQueryException("maxteicountreached");
      }
      rowSet.beforeFirst();
    }
  }

  @Override
  public int getTrackedEntityCountForGrid(TrackedEntityQueryParams params) {
    // ---------------------------------------------------------------------
    // Select clause
    // ---------------------------------------------------------------------

    String sql = getCountQuery(params);

    // ---------------------------------------------------------------------
    // Query
    // ---------------------------------------------------------------------

    log.debug("Tracked entity count SQL: " + sql);

    return jdbcTemplate.queryForObject(sql, Integer.class);
  }

  @Override
  public int getTrackedEntityCountForGridWithMaxTeiLimit(TrackedEntityQueryParams params) {
    String sql = getCountQueryWithMaxTeiLimit(params);

    log.debug("Tracked entity count SQL: " + sql);

    return jdbcTemplate.queryForObject(sql, Integer.class);
  }

  /**
   * Generates SQL based on "params". The purpose of the SQL is to retrieve a list of tracked entity
   * instances, and additionally any requested attributes (If defined in params).
   *
   * <p>The params are validated before we generate the SQL, so the only access-related SQL is the
   * inner join o organisation units.
   *
   * <p>The general structure of the query is as follows:
   *
   * <p>select (main_projection) from (constraint_subquery) left join (additional_information) group
   * by (main_groupby) order by (order)
   *
   * <p>The constraint_subquery looks as follows:
   *
   * <p>select (subquery_projection) from (tracked entity instances) inner join
   * (attribute_constraints) [inner join (program_owner)] inner join (organisation units) left join
   * (attribute_orderby) where exist(program_constraint) order by (order) limit (limit_offset)
   *
   * <p>main_projection: Will have an aggregate string of attributevalues (uid:value) as well as
   * basic te-info. constraint_subquery: Includes all SQL related to narrowing down the number of
   * te's we are looking for. We use inner join primarily for this, as well as exists for program
   * instances. Do make sure we get the right selection, we also use left join on attributes, when
   * we are sorting by attributes, before we sort and finally limit the selection.
   * subquery_projection: Has all the required information for knowing what tracked entities to
   * return and how to order them attribute_constraints: We inner join the attributes, and add 3
   * conditions: te id, tea id and value. This uses a (te, tea, lower(value)) index. For each
   * attribute constraints, we add subsequent inner joins. program_owner: Only included when a
   * program is specified. If included, it will join on 3 columns: te, program and ou. We have an
   * index for this (program, ou, te) which allows a scan only lookup attribute_orderby: When a user
   * specified an attribute in the order param, we need to join that attribute (We do left join, in
   * case the value is not there. This join is not for removing resulting records). After joining it
   * and projecting it, we can order by it. program_constraint: If a program is specified, it
   * indicates the te must be enrolled in that program. Since the relation between te and
   * enrollments are not 1:1, but 1:many, we use exists to avoid duplicate rows of te, allowing us
   * to avoid grouping the result before we order and limit. This saves a lot of time. NOTE: Within
   * the program_constraint, we also have a subquery to deal with any event-related constraints.
   * These can either be constraints on any static properties, or user assignment. For user
   * assignment, we also join with the userinfo table. For events, we have an index (status,
   * occurreddate) which speeds up the lookup significantly order: Order is used both in the
   * subquery and the main query. The sort depends on the params (see more info on the related
   * method). We order the subquery to make sure we get correct results before we limit. We order
   * the main query since the aggregation mixes up the order, so to return a consistent order, we
   * order again. limit_offset: The limit and offset is set based on a combination of params:
   * program and tet can have a maxte limit, which only applies during a search outside the users
   * capture scope. If applied, it will throw an error if the number of results exceeds the limit.
   * Otherwise we use paging. If no paging is set, there is no limit. additional_information: Here
   * we do a left join with any relevant information needed for the result: tet name, any attributes
   * to project, etc. We left join, since we don't want to reduce the results, just add information.
   * main_groupby: The purpose of this group by, is to aggregate any attributes added in
   * additional_information
   *
   * @param params params defining the query
   * @return SQL string
   */
  private String getQuery(TrackedEntityQueryParams params, boolean isGridQuery) {
    if (params.isOrQuery() && params.getAttributesAndFilters().isEmpty()) {
      throw new IllegalArgumentException(
          "A query parameter is used in the request but there aren't filterable attributes");
    }

    StringBuilder stringBuilder = new StringBuilder(getQuerySelect(params));

    if (!isGridQuery) {
      stringBuilder.append(", TE.trackedentityid AS teId ");
    }

    return stringBuilder
        .append("FROM ")
        .append(getFromSubQuery(params, false, isGridQuery))
        .append(getQueryRelatedTables(params))
        .append(getQueryGroupBy(params))
        .append(getQueryOrderBy(false, params, isGridQuery))
        .toString();
  }

  /**
   * Uses the same basis as the getQuery method, but replaces the projection with a count and
   * ignores order and limit
   *
   * @param params params defining the query
   * @return a count SQL query
   */
  private String getCountQuery(TrackedEntityQueryParams params) {
    return new StringBuilder()
        .append(getQueryCountSelect(params))
        .append(getQuerySelect(params))
        .append("FROM ")
        .append(getFromSubQuery(params, true, true))
        .append(getQueryRelatedTables(params))
        .append(getQueryGroupBy(params))
        .append(" ) tecount")
        .toString();
  }

  /**
   * Uses the same basis as the getQuery method, but replaces the projection with a count, ignores
   * order but uses the TE limit set on the program if higher than 0
   *
   * @param params params defining the query
   * @return a count SQL query
   */
  private String getCountQueryWithMaxTeiLimit(TrackedEntityQueryParams params) {
    return new StringBuilder()
        .append(getQueryCountSelect(params))
        .append(getQuerySelect(params))
        .append("FROM ")
        .append(getFromSubQuery(params, true, true))
        .append(getQueryRelatedTables(params))
        .append(getQueryGroupBy(params))
        .append(
            params.getProgram().getMaxTeiCountToReturn() > 0
                ? getLimitClause(params.getProgram().getMaxTeiCountToReturn() + 1)
                : "")
        .append(" ) tecount")
        .toString();
  }

  /**
   * Generates the projection of the main query. Includes two optional columns, deleted and
   * tea_values
   *
   * @param params
   * @return an SQL projection
   */
  private String getQuerySelect(TrackedEntityQueryParams params) {
    LinkedHashSet<String> select =
        new LinkedHashSet<>(
            List.of(
                "SELECT TE.uid AS " + TRACKED_ENTITY_ID,
                "TE.created AS " + CREATED_ID,
                "TE.lastupdated AS " + LAST_UPDATED_ID,
                "TE.ou AS " + ORG_UNIT_ID,
                "TE.ouname AS " + ORG_UNIT_NAME,
                "TET.uid AS " + TRACKED_ENTITY_TYPE_ID,
                "TE.inactive AS " + INACTIVE_ID,
                "TE.potentialduplicate AS " + POTENTIAL_DUPLICATE,
                params.isIncludeDeleted() ? "TE.deleted AS " + DELETED : "",
                params.hasAttributes()
                    ? "string_agg(TEA.uid || '"
                        + UID_VALUE_SEPARATOR
                        + "' || TEAV.value, '"
                        + UID_VALUE_PAIR_SEPARATOR
                        + "') AS tea_values"
                    : ""));

    params.getOrders().stream()
        .map(o -> findColumn(o.getField()))
        .filter(Optional::isPresent)
        .forEach(c -> select.add(c.get().getSqlColumnWithMainTable()));

    return select.stream().filter(c -> !c.isEmpty()).collect(Collectors.joining(", ")) + SPACE;
  }

  /**
   * Generates the projection of the main query when doing a count query.
   *
   * @param params
   * @return an SQL projection
   */
  private String getQueryCountSelect(TrackedEntityQueryParams params) {
    return "SELECT count(instance) FROM ( ";
  }

  /**
   * Generates the SQL of the subquery, used to find the correct subset of tracked entity instances
   * to return. Orchestrates all the different segments of the SQL into a complete subquery.
   *
   * @param params
   * @return an SQL subquery
   */
  private String getFromSubQuery(
      TrackedEntityQueryParams params, boolean isCountQuery, boolean isGridQuery) {
    SqlHelper whereAnd = new SqlHelper(true);
    StringBuilder fromSubQuery =
        new StringBuilder()
            .append("(")
            .append(getFromSubQuerySelect(params))
            .append(" FROM trackedentity TE ")

            // INNER JOIN on constraints
            .append(getFromSubQueryJoinAttributeConditions(params))
            .append(getFromSubQueryJoinProgramOwnerConditions(params))
            .append(getFromSubQueryJoinOrgUnitConditions(params))
            .append(getFromSubQueryJoinEnrollmentConditions(params))

            // LEFT JOIN attributes we need to sort on.
            .append(getFromSubQueryJoinOrderByAttributes(params))

            // WHERE
            .append(getFromSubQueryTrackedEntityConditions(whereAnd, params))
            .append(getFromSubQueryEnrollmentConditions(whereAnd, params));

    if (!isCountQuery) {
      // SORT
      fromSubQuery
          .append(getQueryOrderBy(true, params, isGridQuery))

          // LIMIT, OFFSET
          .append(getFromSubQueryLimitAndOffset(params));
    }

    return fromSubQuery.append(") TE ").toString();
  }

  /**
   * The sub-query projection. If we are sorting by attribute, we need to include the value in the
   * sub-query projection.
   *
   * @param params
   * @return a SQL projection
   */
  private String getFromSubQuerySelect(TrackedEntityQueryParams params) {
    LinkedHashSet<String> columns =
        new LinkedHashSet<>(
            List.of(
                "TE.trackedentityid",
                "TE.uid",
                "TE.created",
                "TE.lastupdated",
                "TE.inactive",
                "TE.trackedentitytypeid",
                "TE.potentialduplicate",
                "TE.deleted",
                "OU.uid as ou",
                "OU.name as ouname "));

    for (OrderParam orderParam : params.getOrders()) {
      Optional<TrackedEntityQueryParams.OrderColumn> orderColumn =
          findColumn(orderParam.getField());

      if (orderColumn.isPresent()) {
        columns.add(orderColumn.get().getSqlColumnWithTableAlias());
      } else {
        if (sortableAttributesAndFilters(params).stream()
            .anyMatch(i -> i.getItem().getUid().equals(orderParam.getField()))) {
          columns.add(
              statementBuilder.columnQuote(orderParam.getField())
                  + ".value AS "
                  + statementBuilder.columnQuote(orderParam.getField()));
        }
      }
    }

    return "SELECT " + String.join(", ", columns);
  }

  /**
   * Get a set of QueryItem that contains sortable attributes also defined as filers
   *
   * @param params
   * @return List of QueryItem
   */
  private Set<QueryItem> sortableAttributesAndFilters(TrackedEntityQueryParams params) {
    List<String> ordersIdentifier =
        params.getOrders().stream().map(OrderParam::getField).collect(Collectors.toList());
    return params.getAttributesAndFilters().stream()
        .filter(queryItem -> ordersIdentifier.contains(queryItem.getItemId()))
        .collect(Collectors.toSet());
  }

  /**
   * Generates the WHERE-clause of the subquery SQL related to tracked entity instances.
   *
   * @param whereAnd tracking if where has been invoked or not
   * @param params
   * @return a SQL segment for the WHERE clause used in the subquery
   */
  private String getFromSubQueryTrackedEntityConditions(
      SqlHelper whereAnd, TrackedEntityQueryParams params) {
    StringBuilder trackedEntity = new StringBuilder();

    if (params.hasTrackedEntityType()) {
      trackedEntity
          .append(whereAnd.whereAnd())
          .append("TE.trackedentitytypeid = ")
          .append(params.getTrackedEntityType().getId())
          .append(SPACE);
    } else if (!CollectionUtils.isEmpty(params.getTrackedEntityTypes())) {
      trackedEntity
          .append(whereAnd.whereAnd())
          .append("TE.trackedentitytypeid IN (")
          .append(getCommaDelimitedString(getIdentifiers(params.getTrackedEntityTypes())))
          .append(") ");
    }

    if (params.hasTrackedEntities()) {
      trackedEntity
          .append(whereAnd.whereAnd())
          .append("TE.uid IN (")
          .append(encodeAndQuote(params.getTrackedEntityUids()))
          .append(") ");
    }

    if (params.hasLastUpdatedDuration()) {
      trackedEntity
          .append(whereAnd.whereAnd())
          .append(" TE.lastupdated >= '")
          .append(getLongGmtDateString(DateUtils.nowMinusDuration(params.getLastUpdatedDuration())))
          .append(SINGLE_QUOTE);
    } else {
      if (params.hasLastUpdatedStartDate()) {
        trackedEntity
            .append(whereAnd.whereAnd())
            .append(" TE.lastupdated >= '")
            .append(getLongDateString(params.getLastUpdatedStartDate()))
            .append(SINGLE_QUOTE);
      }
      if (params.hasLastUpdatedEndDate()) {
        trackedEntity
            .append(whereAnd.whereAnd())
            .append(" TE.lastupdated <='")
            .append(getLongDateString(params.getLastUpdatedEndDate()))
            .append(SINGLE_QUOTE);
      }
    }
    if (params.isSynchronizationQuery()) {
      trackedEntity.append(whereAnd.whereAnd()).append(" TE.lastupdated >= TE.lastsynchronized ");
      if (params.getSkipChangedBefore() != null) {
        trackedEntity
            .append(" AND TE.lastupdated >= '")
            .append(getLongDateString(params.getSkipChangedBefore()))
            .append(SINGLE_QUOTE);
      }
    }

    if (!params.isIncludeDeleted()) {
      trackedEntity.append(whereAnd.whereAnd()).append("TE.deleted IS FALSE ");
    }

    if (params.hasPotentialDuplicateFilter()) {
      trackedEntity
          .append(whereAnd.whereAnd())
          .append("TE.potentialduplicate=")
          .append(params.getPotentialDuplicate())
          .append(SPACE);
    }

    return trackedEntity.toString();
  }

  /**
   * Generates SQL for INNER JOINing attribute values. One INNER JOIN for each attribute to search
   * for.
   *
   * @param params
   * @return a series of 1 or more SQL INNER JOINs, or empty string if no query or attribute filters
   *     exists.
   */
  private String getFromSubQueryJoinAttributeConditions(TrackedEntityQueryParams params) {
    if (!params.isOrQuery()) {
      return joinAttributeValueWithoutQueryParameter(params);
    } else {
      return joinAttributeValueWithQueryParameter(params);
    }
  }

  /**
   * Generates a single INNER JOIN for searching for an attribute by query strings. Searches are
   * done using lower() expression, since attribute values are case insensitive. The query search is
   * extremely slow compared to alternatives. A query string (Can be multiple) has to match at least
   * 1 attribute value for each attribute we have access to. We use Regex to search, allowing both
   * exact match and with wildcards (EQ or LIKE).
   *
   * @param params
   */
  private String joinAttributeValueWithQueryParameter(TrackedEntityQueryParams params) {
    StringBuilder attributes = new StringBuilder();

    final String regexp = statementBuilder.getRegexpMatch();
    final String wordStart = statementBuilder.getRegexpWordStart();
    final String wordEnd = statementBuilder.getRegexpWordEnd();
    final String anyChar = "\\.*?";
    final String start = params.getQuery().isOperator(QueryOperator.LIKE) ? anyChar : wordStart;
    final String end = params.getQuery().isOperator(QueryOperator.LIKE) ? anyChar : wordEnd;
    SqlHelper orHlp = new SqlHelper(true);

    List<Long> itemIds =
        params.getAttributesAndFilters().stream()
            .map(QueryItem::getItem)
            .map(DimensionalItemObject::getId)
            .collect(Collectors.toList());

    attributes
        .append("INNER JOIN trackedentityattributevalue Q ")
        .append("ON Q.trackedentityid = TE.trackedentityid ")
        .append("AND Q.trackedentityattributeid IN (")
        .append(getCommaDelimitedString(itemIds))
        .append(") AND (");

    for (String queryToken : getTokens(params.getQuery().getFilter())) {
      final String query = statementBuilder.encode(queryToken, false);

      attributes
          .append(orHlp.or())
          .append("lower(Q.value) ")
          .append(regexp)
          .append(" '")
          .append(start)
          .append(StringUtils.lowerCase(query))
          .append(end)
          .append(SINGLE_QUOTE);
    }

    return attributes.append(")").toString();
  }

  /**
   * Generates a single INNER JOIN for each attribute we are searching on. We can search by a range
   * of operators. All searching is using lower() since attribute values are case insensitive.
   *
   * @param params
   */
  private String joinAttributeValueWithoutQueryParameter(TrackedEntityQueryParams params) {
    StringBuilder attributes = new StringBuilder();

    List<QueryItem> filterItems =
        params.getAttributesAndFilters().stream()
            .filter(QueryItem::hasFilter)
            .collect(Collectors.toList());

    for (QueryItem queryItem : filterItems) {
      String col = statementBuilder.columnQuote(queryItem.getItemId());
      String teaId = col + ".trackedentityattributeid";
      String teav = "lower(" + col + ".value)";
      String ted = col + ".trackedentityid";

      attributes
          .append(" INNER JOIN trackedentityattributevalue ")
          .append(col)
          .append(" ON ")
          .append(teaId)
          .append(EQUALS)
          .append(queryItem.getItem().getId())
          .append(" AND ")
          .append(ted)
          .append(" = TE.trackedentityid ");

      for (QueryFilter filter : queryItem.getFilters()) {
        String encodedFilter = statementBuilder.encode(filter.getFilter(), false);
        attributes
            .append("AND ")
            .append(teav)
            .append(SPACE)
            .append(filter.getSqlOperator())
            .append(SPACE)
            .append(StringUtils.lowerCase(filter.getSqlFilter(encodedFilter)));
      }
    }

    return attributes.toString();
  }

  /**
   * Generates the LEFT JOINs used for attributes we are ordering by (If any). We use LEFT JOIN to
   * avoid removing any rows if there is no value for a given attribute and te. The result of this
   * LEFT JOIN is used in the subquery projection, and ordering in the subquery and main query.
   *
   * @param params
   * @return a SQL LEFT JOIN for attributes used for ordering, or empty string if not attributes is
   *     used in order.
   */
  private String getFromSubQueryJoinOrderByAttributes(TrackedEntityQueryParams params) {
    StringBuilder joinOrderAttributes = new StringBuilder();

    for (QueryItem orderAttribute : sortableAttributesAndFilters(params)) {
      if (orderAttribute.hasFilter()) { // We already joined this if it is a filter.
        continue;
      }

      joinOrderAttributes
          .append(" LEFT JOIN trackedentityattributevalue AS ")
          .append(statementBuilder.columnQuote(orderAttribute.getItemId()))
          .append(" ON ")
          .append(statementBuilder.columnQuote(orderAttribute.getItemId()))
          .append(".trackedentityid = TE.trackedentityid ")
          .append("AND ")
          .append(statementBuilder.columnQuote(orderAttribute.getItemId()))
          .append(".trackedentityattributeid = ")
          .append(orderAttribute.getItem().getId())
          .append(SPACE);
    }

    return joinOrderAttributes.toString();
  }

  /**
   * Generates an INNER JOIN for program owner. This segment is only included if program is
   * specified or user is not super.
   *
   * @param params
   * @return a SQL INNER JOIN for program owner, or empty string if no program is specified.
   */
  private String getFromSubQueryJoinProgramOwnerConditions(TrackedEntityQueryParams params) {
    if (!params.hasProgram() || skipOwnershipCheck(params)) {
      return "";
    }

    return new StringBuilder()
        .append(" INNER JOIN trackedentityprogramowner PO ")
        .append("ON PO.programid = ")
        .append(params.getProgram().getId())
        .append(" AND PO.trackedentityid = TE.trackedentityid ")
        .toString();
  }

  /**
   * Generates an INNER JOIN for organisation units. If a program is specified, we join on program
   * ownership (PO), if not we join by tracked entity (TE). Based on the ouMode, they will boil down
   * to either DESCENDANTS (requiring matching on PATH), ALL (No constraints) or not DESCENDANTS or
   * ALL (SELECTED) which will match against a collection of ids.
   *
   * @param params
   * @return a SQL INNER JOIN for organisation units
   */
  private String getFromSubQueryJoinOrgUnitConditions(TrackedEntityQueryParams params) {
    StringBuilder orgUnits = new StringBuilder();

    params.handleOrganisationUnits();

    orgUnits
        .append(" INNER JOIN organisationunit OU ")
        .append("ON OU.organisationunitid = ")
        .append(
            params.hasProgram() && !skipOwnershipCheck(params)
                ? "PO.organisationunitid "
                : "TE.organisationunitid ");

    if (!params.hasOrganisationUnits()) {
      return orgUnits.toString();
    }

    if (params.isOrganisationUnitMode(OrganisationUnitSelectionMode.DESCENDANTS)) {
      SqlHelper orHlp = new SqlHelper(true);

      orgUnits.append("AND (");

      for (OrganisationUnit organisationUnit : params.getOrgUnits()) {

        OrganisationUnit ou = organisationUnitStore.getByUid(organisationUnit.getUid());
        if (ou != null) {
          orgUnits.append(orHlp.or()).append("OU.path LIKE '").append(ou.getPath()).append("%'");
        }
      }

      orgUnits.append(") ");
    } else if (!params.isOrganisationUnitMode(OrganisationUnitSelectionMode.ALL)) {
      orgUnits
          .append("AND OU.organisationunitid IN (")
          .append(getCommaDelimitedString(getIdentifiers(params.getOrgUnits())))
          .append(") ");
    }

    return orgUnits.toString();
  }

  /**
   * Generates an INNER JOIN for enrollments. If the param we need to order by is enrolledAt, we
   * need to join the enrollment table to be able to select and order by this value
   *
   * @param params
   * @return a SQL INNER JOIN for enrollments
   */
  private String getFromSubQueryJoinEnrollmentConditions(TrackedEntityQueryParams params) {
    if (params.getOrders().stream().anyMatch(p -> ENROLLED_AT.isPropertyEqualTo(p.getField()))) {
      return new StringBuilder(" INNER JOIN enrollment ")
          .append(PROGRAM_INSTANCE_ALIAS)
          .append(" ON ")
          .append(PROGRAM_INSTANCE_ALIAS + "." + "trackedentityid")
          .append("= TE.trackedentityid ")
          .toString();
    }

    return "";
  }

  /**
   * Generates an EXISTS condition for enrollment (and event if specified). The EXIST will allow us
   * to filter by enrollments with a low overhead. This condition only applies when a program is
   * specified.
   *
   * @param whereAnd indicator tracking whether WHERE has been invoked or not
   * @param params
   * @return an SQL EXISTS clause for enrollment, or empty string if not program is specified.
   */
  private String getFromSubQueryEnrollmentConditions(
      SqlHelper whereAnd, TrackedEntityQueryParams params) {
    StringBuilder program = new StringBuilder();

    if (!params.hasProgram()) {
      return "";
    }

    program
        .append(whereAnd.whereAnd())
        .append("EXISTS (")
        .append("SELECT EN.trackedentityid ")
        .append("FROM enrollment EN ");

    if (params.hasFilterForEvents()) {
      program.append(getFromSubQueryEvent(params));
    }

    program
        .append("WHERE EN.trackedentityid = TE.trackedentityid ")
        .append("AND EN.programid = ")
        .append(params.getProgram().getId())
        .append(SPACE);

    if (params.hasProgramStatus()) {
      program.append("AND EN.status = '").append(params.getProgramStatus()).append("' ");
    }

    if (params.hasFollowUp()) {
      program.append("AND EN.followup IS ").append(params.getFollowUp()).append(SPACE);
    }

    if (params.hasProgramEnrollmentStartDate()) {
      program
          .append("AND EN.enrollmentdate >= '")
          .append(getLongDateString(params.getProgramEnrollmentStartDate()))
          .append("' ");
    }

    if (params.hasProgramEnrollmentEndDate()) {
      program
          .append("AND EN.enrollmentdate <= '")
          .append(getLongDateString(params.getProgramEnrollmentEndDate()))
          .append("' ");
    }

    if (params.hasProgramIncidentStartDate()) {
      program
          .append("AND EN.occurreddate >= '")
          .append(getLongDateString(params.getProgramIncidentStartDate()))
          .append("' ");
    }

    if (params.hasProgramIncidentEndDate()) {
      program
          .append("AND EN.occurreddate <= '")
          .append(getLongDateString(params.getProgramIncidentEndDate()))
          .append("' ");
    }

    if (!params.isIncludeDeleted()) {
      program.append("AND EN.deleted is false ");
    }

    program.append(") ");

    return program.toString();
  }

  /**
   * Generates an INNER JOIN with the enrollments if event-filters are specified. In the case of
   * user assignment is part of the filter, we join with the userinfo table as well.
   *
   * @param params
   * @return an SQL INNER JOIN for filtering on events.
   */
  private String getFromSubQueryEvent(TrackedEntityQueryParams params) {
    StringBuilder events = new StringBuilder();
    SqlHelper whereHlp = new SqlHelper(true);

    events.append("INNER JOIN (").append("SELECT EV.enrollmentid ").append("FROM event EV ");

    if (params.getAssignedUserQueryParam().hasAssignedUsers()) {
      events
          .append("INNER JOIN (")
          .append("SELECT userinfoid AS userid ")
          .append("FROM userinfo ")
          .append("WHERE uid IN (")
          .append(encodeAndQuote(params.getAssignedUserQueryParam().getAssignedUsers()))
          .append(") ")
          .append(") AU ON AU.userid = EV.assigneduserid");
    }

    if (params.hasEventStatus()) {
      String start = getLongDateString(params.getEventStartDate());
      String end = getLongDateString(params.getEventEndDate());

      if (params.isEventStatus(EventStatus.COMPLETED)) {
        events
            .append(getQueryDateConditionBetween(whereHlp, EV_EXECUTIONDATE, start, end))
            .append(whereHlp.whereAnd())
            .append(EV_STATUS)
            .append(EQUALS)
            .append(SINGLE_QUOTE)
            .append(EventStatus.COMPLETED.name())
            .append(SINGLE_QUOTE)
            .append(SPACE);
      } else if (params.isEventStatus(EventStatus.VISITED)
          || params.isEventStatus(EventStatus.ACTIVE)) {
        events
            .append(getQueryDateConditionBetween(whereHlp, EV_EXECUTIONDATE, start, end))
            .append(whereHlp.whereAnd())
            .append(EV_STATUS)
            .append(EQUALS)
            .append(SINGLE_QUOTE)
            .append(EventStatus.ACTIVE.name())
            .append(SINGLE_QUOTE)
            .append(SPACE);
      } else if (params.isEventStatus(EventStatus.SCHEDULE)) {
        events
            .append(getQueryDateConditionBetween(whereHlp, EV_DUEDATE, start, end))
            .append(whereHlp.whereAnd())
            .append(EV_STATUS)
            .append(SPACE)
            .append(IS_NOT_NULL)
            .append(whereHlp.whereAnd())
            .append(EV_EXECUTIONDATE)
            .append(SPACE)
            .append(IS_NULL)
            .append(whereHlp.whereAnd())
            .append("date(now()) <= date(EV.scheduleddate) ");
      } else if (params.isEventStatus(EventStatus.OVERDUE)) {
        events
            .append(getQueryDateConditionBetween(whereHlp, EV_DUEDATE, start, end))
            .append(whereHlp.whereAnd())
            .append(EV_STATUS)
            .append(SPACE)
            .append(IS_NOT_NULL)
            .append(whereHlp.whereAnd())
            .append(EV_EXECUTIONDATE)
            .append(SPACE)
            .append(IS_NULL)
            .append(whereHlp.whereAnd())
            .append("date(now()) > date(EV.scheduleddate) ");
      } else if (params.isEventStatus(EventStatus.SKIPPED)) {
        events
            .append(getQueryDateConditionBetween(whereHlp, EV_DUEDATE, start, end))
            .append(whereHlp.whereAnd())
            .append(EV_STATUS)
            .append(EQUALS)
            .append(SINGLE_QUOTE)
            .append(EventStatus.SKIPPED.name())
            .append(SINGLE_QUOTE)
            .append(SPACE);
      }
    }

    if (params.hasProgramStage()) {
      events
          .append(whereHlp.whereAnd())
          .append("EV.programstageid = ")
          .append(params.getProgramStage().getId())
          .append(SPACE);
    }

    if (AssignedUserSelectionMode.NONE == params.getAssignedUserQueryParam().getMode()) {
      events.append(whereHlp.whereAnd()).append("EV.assigneduserid IS NULL ");
    }

    if (AssignedUserSelectionMode.ANY == params.getAssignedUserQueryParam().getMode()) {
      events.append(whereHlp.whereAnd()).append("EV.assigneduserid IS NOT NULL ");
    }

    if (!params.isIncludeDeleted()) {
      events.append(whereHlp.whereAnd()).append("EV.deleted IS FALSE");
    }

    events.append(") EV ON EV.enrollmentid = EN.enrollmentid ");

    return events.toString();
  }

  /**
   * Helper method for making a date condition. The format is "[WHERE|AND] date >= start AND date <=
   * end".
   *
   * @param whereHelper tracking whether WHERE has been invoked or not
   * @param column the column for filter on
   * @param start the start date
   * @param end the end date
   * @return a SQL filter for finding dates between two dates.
   */
  private String getQueryDateConditionBetween(
      SqlHelper whereHelper, String column, String start, String end) {
    StringBuilder dateBetween = new StringBuilder();

    dateBetween
        .append(whereHelper.whereAnd())
        .append(column)
        .append(" >= '")
        .append(start)
        .append(SINGLE_QUOTE)
        .append(whereHelper.whereAnd())
        .append(column)
        .append(" <= '")
        .append(end)
        .append("' ");

    return dateBetween.toString();
  }

  /**
   * Generates SQL for LEFT JOINing relevant tables with the tracked entities we have in our result.
   * After the subquery, we know which tracked entities we are returning, but these LEFT JOINs will
   * add any extra information we need. For example attribute values, tet uid, tea uid, etc.
   *
   * @param params
   * @return a SQL with several LEFT JOINS, one for each relevant table to retrieve information
   *     from.
   */
  private String getQueryRelatedTables(TrackedEntityQueryParams params) {
    List<QueryItem> attributes = params.getAttributes();
    StringBuilder relatedTables = new StringBuilder();

    relatedTables.append(
        "LEFT JOIN trackedentitytype TET ON TET.trackedentitytypeid = TE.trackedentitytypeid ");

    if (!attributes.isEmpty()) {
      String attributeString =
          getCommaDelimitedString(
              attributes.stream()
                  .map(QueryItem::getItem)
                  .map(IdentifiableObject::getId)
                  .collect(Collectors.toList()));

      relatedTables
          .append("LEFT JOIN trackedentityattributevalue TEAV ")
          .append("ON TEAV.trackedentityid = TE.trackedentityid ")
          .append("AND TEAV.trackedentityattributeid IN (")
          .append(attributeString)
          .append(") ");

      relatedTables
          .append("LEFT JOIN trackedentityattribute TEA ")
          .append("ON TEA.trackedentityattributeid = TEAV.trackedentityattributeid ");
    }

    return relatedTables.toString();
  }

  /**
   * Generates the GROUP BY clause of the query. This is only needed when we are projecting any
   * attributes. If any attributes are present we are aggregating them into a string. In case we are
   * ordering by an attribute, we also need to include that column in the group by.
   *
   * @param params
   * @return a SQL GROUP BY clause, or empty string if no attributes are specified.
   */
  private String getQueryGroupBy(TrackedEntityQueryParams params) {
    if (params.getAttributes().isEmpty()) {
      return "";
    }

    StringBuilder groupBy =
        new StringBuilder()
            .append("GROUP BY TE.trackedentityid, ")
            .append("TE.uid, ")
            .append("TE.created, ")
            .append("TE.lastupdated, ")
            .append("TE.ou, ")
            .append("TE.ouname, ")
            .append("TET.uid, ")
            .append("TE.potentialduplicate, ")
            .append("TE.inactive ")
            .append(params.isIncludeDeleted() ? ", TE.deleted " : "");

    for (QueryItem queryItem : sortableAttributesAndFilters(params)) {
      groupBy
          .append(", TE.")
          .append(statementBuilder.columnQuote(queryItem.getItemId()))
          .append(SPACE);
    }

    return groupBy.toString();
  }

  private String getLimitClause(int limit) {
    return "LIMIT " + limit;
  }

  /**
   * Generates the ORDER BY clause. This clause is used both in the subquery and main query. When
   * using it in the subquery, we want to make sure we get the right tracked entities. When we order
   * in the main query, it's to make sure we return the results in the correct order, since order
   * might be mixed after GROUP BY.
   *
   * @param innerOrder indicates whether this is the subquery order by or main query order by
   * @param params
   * @param isGridQuery indicates whether this is used for grid query or not.
   * @return a SQL ORDER BY clause.
   */
  private String getQueryOrderBy(
      boolean innerOrder, TrackedEntityQueryParams params, boolean isGridQuery) {
    if (!isGridQuery || !params.getAttributes().isEmpty()) {
      List<String> orderFields = new ArrayList<>();
      Set<QueryItem> sortableAttributesAndFilters = sortableAttributesAndFilters(params);

      for (OrderParam order : params.getOrders()) {
        Optional<TrackedEntityQueryParams.OrderColumn> orderColumn = findColumn(order.getField());

        if (orderColumn.isPresent()) {
          String orderField =
              innerOrder
                  ? orderColumn.get().getSqlColumnWithTableAlias()
                  : orderColumn.get().getSqlColumnWithMainTable();

          orderFields.add(orderField + SPACE + order.getDirection());
        } else if (sortableAttributesAndFilters.stream()
            .anyMatch(i -> i.getItem().getUid().equals(order.getField()))) {
          String orderField =
              innerOrder
                  ? statementBuilder.columnQuote(order.getField()) + ".value "
                  : MAIN_QUERY_ALIAS + "." + statementBuilder.columnQuote(order.getField());

          orderFields.add(orderField + SPACE + order.getDirection());
        }
      }

      if (!orderFields.isEmpty()) {
        return "ORDER BY " + StringUtils.join(orderFields, ',') + SPACE;
      }
    }

    if (params.getAttributesAndFilters().stream()
        .noneMatch(qi -> qi.hasFilter() && qi.isUnique())) {
      return "ORDER BY TE.trackedentityid ASC ";
    } else {
      return "";
    }
  }

  /**
   * Generates the LIMIT and OFFSET part of the subquery. The limit is decided by several factors:
   * 1. maxtelimit in a TET or Program 2. PageSize and Offset 3. No paging
   * (TRACKER_TRACKED_ENTITY_QUERY_LIMIT will apply in this case)
   *
   * <p>If maxtelimit is not 0, it means this is the hard limit of the number of results. In the
   * case where there exists more results than maxtelimit, we should return an error to the user
   * (This prevents snooping outside the users capture scope to some degree). 0 means no maxtelimit,
   * or it's not applicable.
   *
   * <p>If we have maxtelimit and paging on, we set the limit to maxtelimit.
   *
   * <p>If we dont have maxtelimit, and paging on, we set normal paging parameters
   *
   * <p>If neither maxtelimit or paging is set, we have no limit set by the user, so system will set
   * the limit to TRACKED_ENTITY_MAX_LIMIT which can be configured in system settings.
   *
   * <p>The limit is set in the subquery, so the latter joins have fewer rows to consider.
   *
   * @param params
   * @return a SQL LIMIT and OFFSET clause, or empty string if no LIMIT can be deducted.
   */
  private String getFromSubQueryLimitAndOffset(TrackedEntityQueryParams params) {
    StringBuilder limitOffset = new StringBuilder();
    int limit = params.getMaxTeLimit();
    int teQueryLimit = resolveTrackedEntityMaxLimit();

    if (limit == 0 && !params.isPaging()) {
      if (teQueryLimit > 0) {
        return limitOffset
            .append(LIMIT)
            .append(SPACE)
            .append(teQueryLimit)
            .append(SPACE)
            .toString();
      }

      return limitOffset.toString();
    } else if (limit == 0 && params.isPaging()) {
      return limitOffset
          .append(LIMIT)
          .append(SPACE)
          .append(params.getPageSizeWithDefault())
          .append(SPACE)
          .append(OFFSET)
          .append(SPACE)
          .append(params.getOffset())
          .append(SPACE)
          .toString();
    } else if (params.isPaging()) {
      return limitOffset
          .append(LIMIT)
          .append(SPACE)
          .append(Math.min(limit + 1, params.getPageSizeWithDefault()))
          .append(SPACE)
          .append(OFFSET)
          .append(SPACE)
          .append(params.getOffset())
          .append(SPACE)
          .toString();
    } else {
      return limitOffset
          .append(LIMIT)
          .append(SPACE)
          .append(limit + 1) // We add +1, since we use this limit to
          // restrict a user to search to wide.
          .append(SPACE)
          .toString();
    }
  }

  @Override
  public boolean exists(String uid) {
    Query<?> query =
        getSession()
            .createNativeQuery(
                "select count(*) from trackedentity where uid=:uid and deleted is false");
    query.setParameter("uid", uid);
    int count = ((Number) query.getSingleResult()).intValue();

    return count > 0;
  }

  @Override
  public boolean existsIncludingDeleted(String uid) {
    Query<?> query =
        getSession().createNativeQuery("select count(*) from trackedentity where uid=:uid");
    query.setParameter("uid", uid);
    int count = ((Number) query.getSingleResult()).intValue();

    return count > 0;
  }

  @Override
  public List<String> getUidsIncludingDeleted(List<String> uids) {
    String hql = "select te.uid " + TE_HQL_BY_UIDS;
    List<String> resultUids = new ArrayList<>();
    List<List<String>> uidsPartitions = Lists.partition(Lists.newArrayList(uids), 20000);

    for (List<String> uidsPartition : uidsPartitions) {
      if (!uidsPartition.isEmpty()) {
        resultUids.addAll(
            getSession().createQuery(hql, String.class).setParameter("uids", uidsPartition).list());
      }
    }

    return resultUids;
  }

  @Override
  public List<TrackedEntity> getIncludingDeleted(List<String> uids) {
    List<TrackedEntity> trackedEntities = new ArrayList<>();
    List<List<String>> uidsPartitions = Lists.partition(Lists.newArrayList(uids), 20000);

    for (List<String> uidsPartition : uidsPartitions) {
      if (!uidsPartition.isEmpty()) {
        trackedEntities.addAll(
            getSession()
                .createQuery(TE_HQL_BY_UIDS, TrackedEntity.class)
                .setParameter("uids", uidsPartition)
                .list());
      }
    }

    return trackedEntities;
  }

  @Override
  public void updateTrackedEntitySyncTimestamp(
      List<String> trackedEntityUIDs, Date lastSynchronized) {
    final String hql =
        "update TrackedEntity set lastSynchronized = :lastSynchronized WHERE uid in :trackedEntityInstances";

    getQuery(hql)
        .setParameter("lastSynchronized", lastSynchronized)
        .setParameter("trackedEntityInstances", trackedEntityUIDs)
        .executeUpdate();
  }

  @Override
  public void updateTrackedEntityLastUpdated(Set<String> trackedEntityUIDs, Date lastUpdated) {
    List<List<String>> uidsPartitions =
        Lists.partition(Lists.newArrayList(trackedEntityUIDs), 20000);

    uidsPartitions.stream()
        .filter(trackedEntities -> !trackedEntities.isEmpty())
        .forEach(
            trackedEntities ->
                getSession()
                    .getNamedQuery("updateTeisLastUpdated")
                    .setParameter("trackedEntities", trackedEntities)
                    .setParameter("lastUpdated", lastUpdated)
                    .executeUpdate());
  }

  @Override
  public List<TrackedEntity> getTrackedEntityByUid(List<String> uids, User user) {
    List<List<String>> uidPartitions = Lists.partition(uids, 20000);

    List<TrackedEntity> instances = new ArrayList<>();

    for (List<String> partition : uidPartitions) {
      instances.addAll(
          getList(
              getCriteriaBuilder(),
              newJpaParameters().addPredicate(root -> root.get("uid").in(partition))));
    }

    return instances;
  }

  @Override
  protected void preProcessPredicates(
      CriteriaBuilder builder, List<Function<Root<TrackedEntity>, Predicate>> predicates) {
    predicates.add(root -> builder.equal(root.get("deleted"), false));
  }

  @Override
  protected TrackedEntity postProcessObject(TrackedEntity trackedEntity) {
    return (trackedEntity == null || trackedEntity.isDeleted()) ? null : trackedEntity;
  }

  private boolean isOrgUnit(QueryItem item) {
    return item.getValueType().isOrganisationUnit();
  }

  private String getOrgUnitNameByUid(String uid) {
    if (uid != null) {
      return Optional.ofNullable(organisationUnitStore.getByUid(uid))
          .orElseGet(() -> new OrganisationUnit(""))
          .getName();
    }

    return StringUtils.EMPTY;
  }

  private boolean skipOwnershipCheck(TrackedEntityQueryParams params) {
    return params.getUser() != null && params.getUser().isSuper();
  }

  // TODO(tracker): remove once SettingKey.TrackedEntityInstanceMaxLimit is removed
  private int resolveTrackedEntityMaxLimit() {
    int deprecatedTeiMaxLimit =
        systemSettingManager.getIntegerSetting(SettingKey.DEPRECATED_TRACKED_ENTITY_MAX_LIMIT);
    int newTeiMaxLimit =
        systemSettingManager.getIntegerSetting(SettingKey.TRACKED_ENTITY_MAX_LIMIT);

    if (isSet(deprecatedTeiMaxLimit)
        && isSet(newTeiMaxLimit)
        && deprecatedTeiMaxLimit != newTeiMaxLimit) {
      throw new IllegalStateException(
          String.format(
              "Only one parameter of '%s' and '%s' must be specified. Prefer '%s' as '%s' will be removed.",
              SettingKey.TRACKED_ENTITY_MAX_LIMIT.getName(),
              SettingKey.DEPRECATED_TRACKED_ENTITY_MAX_LIMIT.getName(),
              SettingKey.TRACKED_ENTITY_MAX_LIMIT.getName(),
              SettingKey.DEPRECATED_TRACKED_ENTITY_MAX_LIMIT.getName()));
    }

    // both settings are disabled
    if (!isSet(deprecatedTeiMaxLimit) && !isSet(newTeiMaxLimit)) {
      return -1;
    }

    return isSet(newTeiMaxLimit) ? newTeiMaxLimit : deprecatedTeiMaxLimit;
  }

  private boolean isSet(Integer i) {
    return i > 0;
  }
}

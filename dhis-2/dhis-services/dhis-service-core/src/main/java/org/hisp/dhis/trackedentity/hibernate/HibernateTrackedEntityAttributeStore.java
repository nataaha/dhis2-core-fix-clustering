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

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.query.Query;
import org.hisp.dhis.common.QueryFilter;
import org.hisp.dhis.common.QueryItem;
import org.hisp.dhis.common.hibernate.HibernateIdentifiableObjectStore;
import org.hisp.dhis.commons.util.SqlHelper;
import org.hisp.dhis.jdbc.StatementBuilder;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramTrackedEntityAttribute;
import org.hisp.dhis.security.acl.AclService;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeStore;
import org.hisp.dhis.trackedentity.TrackedEntityQueryParams;
import org.hisp.dhis.trackedentity.TrackedEntityTypeAttribute;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @author Abyot Asalefew Gizaw
 */
@Repository("org.hisp.dhis.trackedentity.TrackedEntityAttributeStore")
public class HibernateTrackedEntityAttributeStore
    extends HibernateIdentifiableObjectStore<TrackedEntityAttribute>
    implements TrackedEntityAttributeStore {
  private final StatementBuilder statementBuilder;

  public HibernateTrackedEntityAttributeStore(
      EntityManager entityManager,
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher publisher,
      AclService aclService,
      StatementBuilder statementBuilder) {
    super(entityManager, jdbcTemplate, publisher, TrackedEntityAttribute.class, aclService, true);
    this.statementBuilder = statementBuilder;
  }

  // -------------------------------------------------------------------------
  // Implementation methods
  // -------------------------------------------------------------------------

  @Override
  public List<TrackedEntityAttribute> getByDisplayOnVisitSchedule(boolean displayOnVisitSchedule) {
    CriteriaBuilder builder = getCriteriaBuilder();

    return getList(
        builder,
        newJpaParameters()
            .addPredicate(
                root -> builder.equal(root.get("displayOnVisitSchedule"), displayOnVisitSchedule)));
  }

  @Override
  public List<TrackedEntityAttribute> getDisplayInListNoProgram() {
    CriteriaBuilder builder = getCriteriaBuilder();

    return getList(
        builder,
        newJpaParameters()
            .addPredicate(root -> builder.equal(root.get("displayInListNoProgram"), true)));
  }

  @Override
  public Optional<String> getTrackedEntityUidWithUniqueAttributeValue(
      TrackedEntityQueryParams params) {
    // ---------------------------------------------------------------------
    // Select clause
    // ---------------------------------------------------------------------

    SqlHelper hlp = new SqlHelper(true);

    String hql = "select te.uid from TrackedEntity te ";

    if (params.hasOrganisationUnits()) {
      String orgUnitUids =
          params.getOrgUnits().stream()
              .map(OrganisationUnit::getUid)
              .collect(Collectors.joining(", ", "'", "'"));

      hql += "inner join te.organisationUnit as ou ";
      hql += hlp.whereAnd() + " ou.uid in (" + orgUnitUids + ") ";
    }

    for (QueryItem item : params.getAttributes()) {
      for (QueryFilter filter : item.getFilters()) {
        final String encodedFilter =
            filter.getSqlFilter(
                statementBuilder.encode(StringUtils.lowerCase(filter.getFilter()), false));

        hql +=
            hlp.whereAnd()
                + " exists (from TrackedEntityAttributeValue teav where teav.trackedEntity=te";
        hql += " and teav.attribute.uid='" + item.getItemId() + "'";

        if (item.isNumeric()) {
          hql += " and teav.plainValue " + filter.getSqlOperator() + encodedFilter + ")";
        } else {
          hql += " and lower(teav.plainValue) " + filter.getSqlOperator() + encodedFilter + ")";
        }
      }
    }

    if (!params.isIncludeDeleted()) {
      hql += hlp.whereAnd() + " te.deleted is false";
    }

    Query<String> query = getTypedQuery(hql);

    Iterator<String> it = query.iterate();

    if (it.hasNext()) {
      return Optional.of(it.next());
    }

    return Optional.empty();
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Set<TrackedEntityAttribute> getTrackedEntityAttributesByTrackedEntityTypes() {
    Query query =
        getSession().createQuery("select trackedEntityTypeAttributes from TrackedEntityType");

    Set<TrackedEntityTypeAttribute> trackedEntityTypeAttributes = new HashSet<>(query.list());

    return trackedEntityTypeAttributes.stream()
        .map(TrackedEntityTypeAttribute::getTrackedEntityAttribute)
        .collect(Collectors.toSet());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<TrackedEntityAttribute> getAllSearchableAndUniqueTrackedEntityAttributes() {
    Set<TrackedEntityAttribute> result = new HashSet<>();

    Query<TrackedEntityAttribute> programTeaQuery =
        getSession()
            .createQuery(
                "select attribute from ProgramTrackedEntityAttribute ptea where ptea.searchable=true and ptea.attribute.valueType in ('TEXT','LONG_TEXT','PHONE_NUMBER','EMAIL','USERNAME','URL')");
    Query<TrackedEntityAttribute> tetypeAttributeQuery =
        getSession()
            .createQuery(
                "select trackedEntityAttribute from TrackedEntityTypeAttribute teta where teta.searchable=true and teta.trackedEntityAttribute.valueType in ('TEXT','LONG_TEXT','PHONE_NUMBER','EMAIL','USERNAME','URL')");
    Query<TrackedEntityAttribute> uniqueAttributeQuery =
        getSession().createQuery("from TrackedEntityAttribute tea where tea.unique=true");

    List<TrackedEntityAttribute> programSearchableTrackedEntityAttributes = programTeaQuery.list();
    List<TrackedEntityAttribute> trackedEntityTypeSearchableAttributes =
        tetypeAttributeQuery.list();
    List<TrackedEntityAttribute> uniqueAttributes = uniqueAttributeQuery.list();

    result.addAll(programSearchableTrackedEntityAttributes);
    result.addAll(trackedEntityTypeSearchableAttributes);
    result.addAll(uniqueAttributes);

    return result;
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Map<Program, Set<TrackedEntityAttribute>> getTrackedEntityAttributesByProgram() {
    Map<Program, Set<TrackedEntityAttribute>> result = new HashMap<>();

    Query query = getSession().createQuery("select p.programAttributes from Program p");

    List<ProgramTrackedEntityAttribute> programTrackedEntityAttributes = query.list();

    for (ProgramTrackedEntityAttribute programTrackedEntityAttribute :
        programTrackedEntityAttributes) {
      if (!result.containsKey(programTrackedEntityAttribute.getProgram())) {
        result.put(
            programTrackedEntityAttribute.getProgram(),
            Sets.newHashSet(programTrackedEntityAttribute.getAttribute()));
      } else {
        result
            .get(programTrackedEntityAttribute.getProgram())
            .add(programTrackedEntityAttribute.getAttribute());
      }
    }
    return result;
  }
}

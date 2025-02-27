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
package org.hisp.dhis.dxf2.deprecated.tracker;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hisp.dhis.user.UserRole.AUTHORITY_ALL;
import static org.hisp.dhis.util.DateUtils.getIso8601NoTz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.EntityManager;
import org.exparity.hamcrest.date.DateMatchers;
import org.hamcrest.CoreMatchers;
import org.hisp.dhis.category.Category;
import org.hisp.dhis.category.CategoryCombo;
import org.hisp.dhis.category.CategoryOption;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.DataDimensionType;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.common.ValueType;
import org.hisp.dhis.dataelement.DataElement;
import org.hisp.dhis.dxf2.common.ImportOptions;
import org.hisp.dhis.dxf2.deprecated.tracker.event.DataValue;
import org.hisp.dhis.dxf2.deprecated.tracker.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.dxf2.deprecated.tracker.trackedentity.TrackedEntityInstanceService;
import org.hisp.dhis.dxf2.importsummary.ImportStatus;
import org.hisp.dhis.dxf2.importsummary.ImportSummaries;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.event.EventStatus;
import org.hisp.dhis.eventdatavalue.EventDataValue;
import org.hisp.dhis.importexport.ImportStrategy;
import org.hisp.dhis.organisationunit.FeatureType;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Enrollment;
import org.hisp.dhis.program.EnrollmentService;
import org.hisp.dhis.program.Event;
import org.hisp.dhis.program.EventService;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramStage;
import org.hisp.dhis.program.ProgramStageDataElement;
import org.hisp.dhis.program.ProgramStageDataElementService;
import org.hisp.dhis.program.ProgramStatus;
import org.hisp.dhis.program.ProgramType;
import org.hisp.dhis.test.integration.TransactionalIntegrationTest;
import org.hisp.dhis.trackedentity.TrackedEntity;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.trackedentity.TrackedEntityTypeService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserService;
import org.hisp.dhis.util.DateUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Ameen Mohamed <ameen@dhis2.org>
 */
class EventImportTest extends TransactionalIntegrationTest {
  private static final String DUE_DATE = "2021-02-28T13:05:00";

  private static final String EVENT_DATE = "2021-02-25T12:15:00";

  @Autowired private org.hisp.dhis.dxf2.deprecated.tracker.event.EventService eventService;

  @Autowired private TrackedEntityTypeService trackedEntityTypeService;

  @Autowired private TrackedEntityInstanceService trackedEntityInstanceService;

  @Autowired private ProgramStageDataElementService programStageDataElementService;

  @Autowired
  private org.hisp.dhis.dxf2.deprecated.tracker.enrollment.EnrollmentService enrollmentService;

  @Autowired private IdentifiableObjectManager manager;

  @Autowired private EnrollmentService programInstanceService;

  @Autowired private EventService programStageInstanceService;

  @Autowired private UserService _userService;

  @Autowired private EntityManager entityManager;

  @Autowired JdbcTemplate jdbcTemplate;

  private TrackedEntityInstance trackedEntityInstanceMaleA;

  private OrganisationUnit organisationUnitA;

  private OrganisationUnit organisationUnitB;

  private DataElement dataElementA;

  private DataElement dataElementA2;

  private DataElement dataElementB;

  private Program programA;

  private Program programB;

  private ProgramStage programStageA;

  private ProgramStage programStageA2;

  private ProgramStage programStageB;

  private Enrollment enrollment;

  private org.hisp.dhis.dxf2.deprecated.tracker.event.Event event;

  private User superUser;

  private static final SimpleDateFormat simpleDateFormat =
      new SimpleDateFormat(DateUtils.ISO8601_NO_TZ_PATTERN);

  @Override
  protected void setUpTest() throws Exception {
    userService = _userService;
    superUser = userService.getUserByUsername("admin_test");
    injectSecurityContextUser(superUser);

    organisationUnitA = createOrganisationUnit('A');
    organisationUnitB = createOrganisationUnit('B');
    manager.save(organisationUnitA);
    manager.save(organisationUnitB);
    TrackedEntityType trackedEntityType = createTrackedEntityType('A');
    trackedEntityTypeService.addTrackedEntityType(trackedEntityType);
    TrackedEntity maleA = createTrackedEntity(organisationUnitA);
    maleA.setTrackedEntityType(trackedEntityType);
    manager.save(maleA);
    trackedEntityInstanceMaleA = trackedEntityInstanceService.getTrackedEntityInstance(maleA);
    CategoryOption categoryOption1 = new CategoryOption("male");
    categoryOption1.setAutoFields();
    CategoryOption categoryOption2 = new CategoryOption("female");
    categoryOption2.setAutoFields();
    manager.save(Lists.newArrayList(categoryOption1, categoryOption2));
    Category cat1 = new Category("cat1", DataDimensionType.DISAGGREGATION);
    cat1.setShortName(cat1.getName());
    cat1.setCategoryOptions(Lists.newArrayList(categoryOption1, categoryOption2));
    manager.save(Lists.newArrayList(cat1));
    CategoryCombo categoryCombo = manager.getByName(CategoryCombo.class, "default");
    categoryCombo.setCategories(Lists.newArrayList(cat1));
    dataElementA = createDataElement('A');
    dataElementA.setValueType(ValueType.INTEGER);
    dataElementA.setCategoryCombo(categoryCombo);
    manager.save(dataElementA);
    dataElementA2 = createDataElement('a');
    dataElementA2.setValueType(ValueType.INTEGER);
    dataElementA2.setCategoryCombo(categoryCombo);
    manager.save(dataElementA2);
    dataElementB = createDataElement('B');
    dataElementB.setValueType(ValueType.INTEGER);
    dataElementB.setCategoryCombo(categoryCombo);
    manager.save(dataElementB);
    programStageA = createProgramStage('A', 0);
    programStageA.setFeatureType(FeatureType.POINT);
    manager.save(programStageA);
    programStageA2 = createProgramStage('a', 0);
    programStageA2.setFeatureType(FeatureType.POINT);
    programStageA2.setRepeatable(true);
    manager.save(programStageA2);
    programStageB = createProgramStage('B', 0);
    programStageB.setFeatureType(FeatureType.POINT);
    manager.save(programStageB);
    programA = createProgram('A', new HashSet<>(), organisationUnitA);
    programA.setProgramType(ProgramType.WITH_REGISTRATION);
    programA.setCategoryCombo(categoryCombo);
    manager.save(programA);
    programB = createProgram('B', new HashSet<>(), organisationUnitB);
    programB.setProgramType(ProgramType.WITHOUT_REGISTRATION);
    programB.setCategoryCombo(categoryCombo);
    manager.save(programB);
    ProgramStageDataElement programStageDataElement = new ProgramStageDataElement();
    programStageDataElement.setDataElement(dataElementA);
    programStageDataElement.setProgramStage(programStageA);
    programStageDataElementService.addProgramStageDataElement(programStageDataElement);
    ProgramStageDataElement programStageDataElementA2 = new ProgramStageDataElement();
    programStageDataElementA2.setDataElement(dataElementA2);
    programStageDataElementA2.setProgramStage(programStageA2);
    programStageDataElementService.addProgramStageDataElement(programStageDataElementA2);
    ProgramStageDataElement programStageDataElementB = new ProgramStageDataElement();
    programStageDataElementB.setDataElement(dataElementB);
    programStageDataElementB.setProgramStage(programStageB);
    programStageDataElementService.addProgramStageDataElement(programStageDataElementB);
    programStageA.getProgramStageDataElements().add(programStageDataElement);
    programStageA2.getProgramStageDataElements().add(programStageDataElementA2);
    programStageA.setProgram(programA);
    programStageA2.setProgram(programA);
    programA.getProgramStages().add(programStageA);
    programA.getProgramStages().add(programStageA2);
    programStageB.getProgramStageDataElements().add(programStageDataElementB);
    programStageB.setProgram(programB);
    programB.getProgramStages().add(programStageB);
    manager.update(programStageA);
    manager.update(programStageA2);
    manager.update(programA);
    manager.update(programStageB);
    manager.update(programB);
    enrollment = new Enrollment();
    enrollment.setEnrollmentDate(new Date());
    enrollment.setOccurredDate(new Date());
    enrollment.setProgram(programB);
    enrollment.setStatus(ProgramStatus.ACTIVE);
    enrollment.setStoredBy("test");
    enrollment.setName("EventImportTestPI");
    enrollment.setUid(CodeGenerator.generateUid());
    manager.save(enrollment);
    event = createEvent("eventUid001");
    superUser = createAndAddAdminUser(AUTHORITY_ALL);
    injectSecurityContextUser(superUser);
  }

  @Test
  void shouldUpdateEventDataValues_whenAddingDataValuesToEvent() throws IOException {
    InputStream is =
        createEventJsonInputStream(
            programB.getUid(),
            programStageB.getUid(),
            organisationUnitB.getUid(),
            trackedEntityInstanceMaleA.getTrackedEntityInstance(),
            dataElementB,
            "10");
    String uid = eventService.addEventsJson(is, null).getImportSummaries().get(0).getReference();

    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event = createEvent(uid);

    Event ev = programStageInstanceService.getEvent(event.getUid());

    assertNotNull(ev);
    assertEquals(1, ev.getEventDataValues().size());

    // add a new data value and update an existing one

    DataValue dataValueA = new DataValue();
    dataValueA.setValue("10");
    dataValueA.setDataElement(dataElementA.getUid());
    dataValueA.setStoredBy(superUser.getName());

    DataValue dataValueB = new DataValue();
    dataValueB.setValue("20");
    dataValueB.setDataElement(dataElementB.getUid());
    dataValueB.setStoredBy(superUser.getName());

    event.setDataValues(Set.of(dataValueA, dataValueB));

    Date now = new Date();

    eventService.updateEventDataValues(event);

    manager.clear();

    ev = programStageInstanceService.getEvent(event.getUid());

    assertNotNull(ev);
    assertNotNull(ev.getEventDataValues());
    assertEquals(2, ev.getEventDataValues().size());

    EventDataValue eventDataValueA =
        ev.getEventDataValues().stream()
            .filter(edv -> edv.getDataElement().equals(dataValueA.getDataElement()))
            .findFirst()
            .orElse(null);

    assertNotNull(eventDataValueA);
    assertEquals(eventDataValueA.getValue(), dataValueA.getValue());
    assertEquals(eventDataValueA.getStoredBy(), superUser.getName());
    assertNotNull(eventDataValueA.getCreatedByUserInfo());
    assertNotNull(eventDataValueA.getLastUpdatedByUserInfo());
    assertNotNull(eventDataValueA.getCreated());
    assertNotNull(eventDataValueA.getLastUpdated());

    EventDataValue eventDataValueB =
        ev.getEventDataValues().stream()
            .filter(edv -> edv.getDataElement().equals(dataValueB.getDataElement()))
            .findFirst()
            .orElse(null);

    assertNotNull(eventDataValueB);
    assertEquals(eventDataValueB.getValue(), dataValueB.getValue());
    assertEquals(eventDataValueB.getStoredBy(), superUser.getName());
    assertNotNull(eventDataValueB.getCreatedByUserInfo());
    assertNotNull(eventDataValueB.getLastUpdatedByUserInfo());
    assertNotNull(eventDataValueB.getCreated());
    assertNotNull(eventDataValueB.getLastUpdated());

    TrackedEntityInstance trackedEntityInstance =
        trackedEntityInstanceService.getTrackedEntityInstance(
            trackedEntityInstanceMaleA.getTrackedEntityInstance());
    assertTrue(trackedEntityInstance.getLastUpdated().compareTo(getIso8601NoTz(now)) > 0);
  }

  @Test
  void testAddEventOnProgramWithoutRegistration() throws IOException {
    InputStream is =
        createEventJsonInputStream(
            programB.getUid(),
            programStageB.getUid(),
            organisationUnitB.getUid(),
            null,
            dataElementB,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.SUCCESS, importSummaries.getStatus());
  }

  @Test
  void testAddEventWithDueDateForProgramWithoutRegistration() {
    String eventUid = CodeGenerator.generateUid();

    org.hisp.dhis.dxf2.deprecated.tracker.enrollment.Enrollment enrollment =
        createEnrollment(programA.getUid(), trackedEntityInstanceMaleA.getTrackedEntityInstance());
    ImportSummary importSummary = enrollmentService.addEnrollment(enrollment, null, null);
    assertEquals(ImportStatus.SUCCESS, importSummary.getStatus());

    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event =
        createScheduledTrackerEvent(
            eventUid, programA, programStageA, EventStatus.SCHEDULE, organisationUnitA);

    ImportSummary summary = eventService.addEvent(event, null, false);
    assertEquals(ImportStatus.SUCCESS, summary.getStatus());

    DataValue dataValue = new DataValue();
    dataValue.setValue("10");
    dataValue.setDataElement(dataElementA.getUid());
    event.setDataValues(Set.of(dataValue));
    event.setStatus(EventStatus.COMPLETED);

    summary = eventService.updateEvent(event, true, null, false);
    assertEquals(ImportStatus.SUCCESS, summary.getStatus());

    Event psi = programStageInstanceService.getEvent(eventUid);

    assertEquals(DUE_DATE, DateUtils.getLongDateString(psi.getScheduledDate()));
  }

  /**
   * TODO: LUCIANO: this test has been ignored because the Importer should not import an event
   * linked to a Program with 2 or more Enrollments
   */
  @Test
  @Disabled
  void testAddEventOnProgramWithoutRegistrationAndExistingEnrollment() throws IOException {
    Enrollment dbEnrollment = new Enrollment();
    dbEnrollment.setEnrollmentDate(new Date());
    dbEnrollment.setOccurredDate(new Date());
    dbEnrollment.setProgram(programB);
    dbEnrollment.setStatus(ProgramStatus.ACTIVE);
    dbEnrollment.setStoredBy("test");
    programInstanceService.addEnrollment(dbEnrollment);
    InputStream is =
        createEventJsonInputStream(
            programB.getUid(),
            programStageB.getUid(),
            organisationUnitB.getUid(),
            null,
            dataElementB,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.SUCCESS, importSummaries.getStatus());
  }

  @Test
  void testAddEventOnNonExistentProgram() throws IOException {
    InputStream is =
        createEventJsonInputStream(
            "null", programStageB.getUid(), organisationUnitB.getUid(), null, dataElementB, "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.ERROR, importSummaries.getStatus());
    assertThat(
        importSummaries.getImportSummaries().get(0).getDescription(),
        CoreMatchers.containsString("does not point to a valid program"));
  }

  @Test
  void testAddEventOnNonExistentProgramStage() throws IOException {
    InputStream is =
        createEventJsonInputStream(
            programA.getUid(), "null", organisationUnitA.getUid(), null, dataElementA, "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.ERROR, importSummaries.getStatus());
    assertThat(
        importSummaries.getImportSummaries().get(0).getDescription(),
        CoreMatchers.containsString("does not point to a valid programStage"));
  }

  @Test
  void testAddEventOnProgramWithRegistration() throws IOException, ParseException {
    String lastUpdateDateBefore =
        trackedEntityInstanceService
            .getTrackedEntityInstance(trackedEntityInstanceMaleA.getTrackedEntityInstance())
            .getLastUpdated();
    org.hisp.dhis.dxf2.deprecated.tracker.enrollment.Enrollment enrollment =
        createEnrollment(programA.getUid(), trackedEntityInstanceMaleA.getTrackedEntityInstance());
    ImportSummary importSummary = enrollmentService.addEnrollment(enrollment, null, null);
    assertEquals(ImportStatus.SUCCESS, importSummary.getStatus());
    InputStream is =
        createEventJsonInputStream(
            programA.getUid(),
            programStageA.getUid(),
            organisationUnitA.getUid(),
            trackedEntityInstanceMaleA.getTrackedEntityInstance(),
            dataElementA,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.SUCCESS, importSummaries.getStatus());
    cleanSession();

    // We use JDBC to get the timestamp, since it's stored using JDBC not
    // hibernate.
    String lastUpdateDateNew =
        DateUtils.getIso8601NoTz(
            this.jdbcTemplate.queryForObject(
                "SELECT lastupdated FROM trackedentity WHERE uid IN ('"
                    + trackedEntityInstanceMaleA.getTrackedEntityInstance()
                    + "')",
                Timestamp.class));

    assertTrue(
        simpleDateFormat.parse(lastUpdateDateNew).getTime()
            > simpleDateFormat.parse(lastUpdateDateBefore).getTime());
  }

  @Test
  void testAddEventOnProgramWithRegistrationWithoutTei() throws IOException {
    InputStream is =
        createEventJsonInputStream(
            programA.getUid(),
            programStageA.getUid(),
            organisationUnitA.getUid(),
            null,
            dataElementA,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.ERROR, importSummaries.getStatus());
    assertThat(
        importSummaries.getImportSummaries().get(0).getDescription(),
        CoreMatchers.containsString(
            "Event.trackedEntityInstance does not point to a valid tracked entity instance: null"));
  }

  @Test
  void testAddEventOnProgramWithRegistrationWithInvalidTei() throws IOException {
    InputStream is =
        createEventJsonInputStream(
            programA.getUid(),
            programStageA.getUid(),
            organisationUnitA.getUid(),
            "null",
            dataElementA,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.ERROR, importSummaries.getStatus());
    assertThat(
        importSummaries.getImportSummaries().get(0).getDescription(),
        CoreMatchers.containsString(
            "Event.trackedEntityInstance does not point to a valid tracked entity instance: null"));
  }

  @Test
  void testAddEventOnProgramWithRegistrationButWithoutEnrollment() throws IOException {
    InputStream is =
        createEventJsonInputStream(
            programA.getUid(),
            programStageA.getUid(),
            organisationUnitA.getUid(),
            trackedEntityInstanceMaleA.getTrackedEntityInstance(),
            dataElementA,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.ERROR, importSummaries.getStatus());
    assertThat(
        importSummaries.getImportSummaries().get(0).getDescription(),
        CoreMatchers.containsString("is not enrolled in program"));
  }

  @Test
  void testAddEventOnRepeatableProgramStageWithRegistration() throws IOException {
    org.hisp.dhis.dxf2.deprecated.tracker.enrollment.Enrollment enrollment =
        createEnrollment(programA.getUid(), trackedEntityInstanceMaleA.getTrackedEntityInstance());
    ImportSummary importSummary = enrollmentService.addEnrollment(enrollment, null, null);
    assertEquals(ImportStatus.SUCCESS, importSummary.getStatus());
    InputStream is =
        createEventJsonInputStream(
            programA.getUid(),
            programStageA2.getUid(),
            organisationUnitA.getUid(),
            trackedEntityInstanceMaleA.getTrackedEntityInstance(),
            dataElementA2,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.SUCCESS, importSummaries.getStatus());
  }

  @Test
  void testAddOneValidAndOneInvalidEvent() throws IOException {
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event validEvent = createEvent("eventUid004");
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event invalidEvent = createEvent("eventUid005");
    invalidEvent.setOrgUnit("INVALID");
    InputStream is =
        createEventsJsonInputStream(
            Lists.newArrayList(validEvent, invalidEvent), dataElementA, "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.ERROR, importSummaries.getStatus());
    assertEquals(1, importSummaries.getImported());
    assertEquals(1, importSummaries.getIgnored());
    assertEquals(0, importSummaries.getDeleted());
    assertEquals(0, importSummaries.getUpdated());
  }

  @Test
  void testAddValidEnrollmentWithOneValidAndOneInvalidEvent() {
    org.hisp.dhis.dxf2.deprecated.tracker.enrollment.Enrollment enrollment =
        createEnrollment(programA.getUid(), trackedEntityInstanceMaleA.getTrackedEntityInstance());
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event validEvent = createEvent("eventUid004");
    validEvent.setOrgUnit(organisationUnitA.getUid());
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event invalidEvent = createEvent("eventUid005");
    invalidEvent.setOrgUnit("INVALID");
    enrollment.setEvents(Lists.newArrayList(validEvent, invalidEvent));
    ImportSummary importSummary = enrollmentService.addEnrollment(enrollment, null);
    assertEquals(ImportStatus.SUCCESS, importSummary.getStatus());
    assertEquals(1, importSummary.getImportCount().getImported());
    assertEquals(0, importSummary.getImportCount().getIgnored());
    assertEquals(0, importSummary.getImportCount().getDeleted());
    assertEquals(0, importSummary.getImportCount().getUpdated());
    ImportSummaries eventImportSummaries = importSummary.getEvents();
    assertEquals(ImportStatus.ERROR, eventImportSummaries.getStatus());
    assertEquals(1, eventImportSummaries.getImported());
    assertEquals(1, eventImportSummaries.getIgnored());
    assertEquals(0, eventImportSummaries.getDeleted());
    assertEquals(0, eventImportSummaries.getUpdated());
  }

  @Test
  void testEventDeletion() {
    programInstanceService.addEnrollment(enrollment);
    ImportOptions importOptions = new ImportOptions();
    ImportSummary importSummary = eventService.addEvent(event, importOptions, false);
    assertEquals(ImportStatus.SUCCESS, importSummary.getStatus());
    Event psi = programStageInstanceService.getEvent(event.getUid());
    assertNotNull(psi);
    importSummary = eventService.deleteEvent(event.getUid());
    assertEquals(ImportStatus.SUCCESS, importSummary.getStatus());
    psi = programStageInstanceService.getEvent(event.getUid());
    assertNull(psi);
    boolean existsDeleted = programStageInstanceService.eventExistsIncludingDeleted(event.getUid());
    assertTrue(existsDeleted);
  }

  @Test
  void testAddAlreadyDeletedEvent() {
    programInstanceService.addEnrollment(enrollment);
    ImportOptions importOptions = new ImportOptions();
    eventService.addEvent(event, importOptions, false);
    eventService.deleteEvent(event.getUid());
    manager.flush();
    importOptions.setImportStrategy(ImportStrategy.CREATE);
    event.setDeleted(true);
    ImportSummary importSummary = eventService.addEvent(event, importOptions, false);
    assertEquals(ImportStatus.ERROR, importSummary.getStatus());
    assertEquals(1, importSummary.getImportCount().getIgnored());
    assertTrue(importSummary.getDescription().contains("already exists or was deleted earlier"));
  }

  @Test
  void testAddAlreadyDeletedEventInBulk() {
    programInstanceService.addEnrollment(enrollment);
    ImportOptions importOptions = new ImportOptions();
    eventService.addEvent(event, importOptions, false);
    eventService.deleteEvent(event.getUid());
    manager.flush();
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event2 = createEvent("eventUid002");
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event3 = createEvent("eventUid003");
    importOptions.setImportStrategy(ImportStrategy.CREATE);
    event.setDeleted(true);
    List<org.hisp.dhis.dxf2.deprecated.tracker.event.Event> events = new ArrayList<>();
    events.add(event);
    events.add(event2);
    events.add(event3);
    ImportSummaries importSummaries = eventService.addEvents(events, importOptions, true);
    assertEquals(ImportStatus.ERROR, importSummaries.getStatus());
    assertEquals(1, importSummaries.getIgnored());
    assertEquals(2, importSummaries.getImported());
    assertTrue(
        importSummaries.getImportSummaries().stream()
            .anyMatch(is -> is.getDescription().contains("already exists or was deleted earlier")));
    manager.flush();
    List<String> uids = new ArrayList<>();
    uids.add("eventUid001");
    uids.add("eventUid002");
    uids.add("eventUid003");
    List<String> fetchedUids = programStageInstanceService.getEventUidsIncludingDeleted(uids);
    assertTrue(Sets.difference(new HashSet<>(uids), new HashSet<>(fetchedUids)).isEmpty());
  }

  @Test
  void testGeometry() throws IOException {
    InputStream is =
        createEventJsonInputStream(
            programB.getUid(),
            programStageB.getUid(),
            organisationUnitB.getUid(),
            null,
            dataElementB,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    assertEquals(ImportStatus.SUCCESS, importSummaries.getStatus());
  }

  //
  // UPDATE EVENT TESTS
  //
  @Test
  void testVerifyEventCanBeUpdatedUsingProgramOnly2() throws IOException {
    // CREATE A NEW EVENT
    InputStream is =
        createEventJsonInputStream(
            programB.getUid(),
            programStageB.getUid(),
            organisationUnitB.getUid(),
            null,
            dataElementB,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    String uid = importSummaries.getImportSummaries().get(0).getReference();
    assertEquals(ImportStatus.SUCCESS, importSummaries.getStatus());
    // FETCH NEWLY CREATED EVENT
    programStageInstanceService.getEvent(uid);
    // UPDATE EVENT - Program is not specified
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event =
        new org.hisp.dhis.dxf2.deprecated.tracker.event.Event();
    event.setEvent(uid);
    event.setStatus(EventStatus.COMPLETED);
    final ImportSummary summary =
        eventService.updateEvent(event, false, ImportOptions.getDefaultImportOptions(), false);
    assertThat(summary.getStatus(), is(ImportStatus.ERROR));
    assertThat(
        summary.getDescription(), is("Event.program does not point to a valid program: null"));
    assertThat(summary.getReference(), is(uid));
  }

  @Test
  void testVerifyEventCanBeUpdatedUsingProgramOnly() throws IOException {
    // CREATE A NEW EVENT
    InputStream is =
        createEventJsonInputStream(
            programB.getUid(),
            programStageB.getUid(),
            organisationUnitB.getUid(),
            null,
            dataElementB,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    String uid = importSummaries.getImportSummaries().get(0).getReference();
    assertEquals(ImportStatus.SUCCESS, importSummaries.getStatus());
    // FETCH NEWLY CREATED EVENT
    Event psi = programStageInstanceService.getEvent(uid);
    // UPDATE EVENT (no actual changes, except for empty data value)
    // USE ONLY PROGRAM
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event =
        new org.hisp.dhis.dxf2.deprecated.tracker.event.Event();
    event.setEvent(uid);
    event.setProgram(programB.getUid());
    event.setStatus(EventStatus.COMPLETED);
    assertEquals(
        ImportStatus.SUCCESS,
        eventService
            .updateEvent(event, false, ImportOptions.getDefaultImportOptions(), false)
            .getStatus());

    // cleanSession();
    dbmsManager.clearSession();
    Event psi2 = programStageInstanceService.getEvent(uid);

    assertThat(psi.getLastUpdated(), DateMatchers.before(psi2.getLastUpdated()));
    assertThat(psi.getCreated(), is(psi2.getCreated()));
    assertThat(psi.getEnrollment().getUid(), is(psi2.getEnrollment().getUid()));
    assertThat(psi.getProgramStage().getUid(), is(psi2.getProgramStage().getUid()));
    assertThat(psi.getOrganisationUnit().getUid(), is(psi2.getOrganisationUnit().getUid()));
    assertThat(psi.getAttributeOptionCombo().getUid(), is(psi2.getAttributeOptionCombo().getUid()));
    assertThat(psi.getStatus().getValue(), is(psi2.getStatus().getValue()));
    assertThat(psi.getOccurredDate(), is(psi2.getOccurredDate()));
    assertThat(psi.getCompletedDate(), is(psi2.getCompletedDate()));
    assertThat(psi.getCompletedBy(), is(psi2.getCompletedBy()));
    assertThat(psi.isDeleted(), is(psi2.isDeleted()));
    assertThat(psi.getEventDataValues().size(), is(1));
    assertThat(psi2.getEventDataValues().size(), is(0));
  }

  @Test
  void testVerifyEventUncompleteSetsCompletedDateToNull() throws IOException {
    // CREATE A NEW EVENT
    InputStream is =
        createEventJsonInputStream(
            programB.getUid(),
            programStageB.getUid(),
            organisationUnitB.getUid(),
            null,
            dataElementB,
            "10");
    ImportSummaries importSummaries = eventService.addEventsJson(is, null);
    String uid = importSummaries.getImportSummaries().get(0).getReference();
    assertEquals(ImportStatus.SUCCESS, importSummaries.getStatus());
    // FETCH NEWLY CREATED EVENT
    Event psi = programStageInstanceService.getEvent(uid);
    // UPDATE EVENT (no actual changes, except for empty data value and
    // status
    // change)
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event =
        new org.hisp.dhis.dxf2.deprecated.tracker.event.Event();
    event.setEvent(uid);
    event.setProgram(programB.getUid());
    event.setStatus(EventStatus.ACTIVE);
    assertEquals(
        ImportStatus.SUCCESS,
        eventService
            .updateEvent(event, false, ImportOptions.getDefaultImportOptions(), false)
            .getStatus());
    dbmsManager.clearSession();

    Event psi2 = programStageInstanceService.getEvent(uid);
    assertThat(psi.getLastUpdated(), DateMatchers.before(psi2.getLastUpdated()));
    assertThat(psi.getCreated(), is(psi2.getCreated()));
    assertThat(psi.getEnrollment().getUid(), is(psi2.getEnrollment().getUid()));
    assertThat(psi.getProgramStage().getUid(), is(psi2.getProgramStage().getUid()));
    assertThat(psi.getOrganisationUnit().getUid(), is(psi2.getOrganisationUnit().getUid()));
    assertThat(psi.getAttributeOptionCombo().getUid(), is(psi2.getAttributeOptionCombo().getUid()));
    assertThat(psi2.getStatus(), is(EventStatus.ACTIVE));
    assertThat(psi.getOccurredDate(), is(psi2.getOccurredDate()));
    assertThat(psi2.getCompletedDate(), is(nullValue()));
    assertThat(psi.getCompletedBy(), is(psi2.getCompletedBy()));
    assertThat(psi.isDeleted(), is(psi2.isDeleted()));
    assertThat(psi.getEventDataValues().size(), is(1));
    assertThat(psi2.getEventDataValues().size(), is(0));
  }

  @Test
  void testVerifyEventUpdatedForEventDateHasActiveStatus() {
    String eventUid = CodeGenerator.generateUid();

    org.hisp.dhis.dxf2.deprecated.tracker.enrollment.Enrollment enrollment =
        createEnrollment(programA.getUid(), trackedEntityInstanceMaleA.getTrackedEntityInstance());
    ImportSummary importSummary = enrollmentService.addEnrollment(enrollment, null, null);
    assertEquals(ImportStatus.SUCCESS, importSummary.getStatus());

    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event =
        createScheduledTrackerEvent(
            eventUid, programA, programStageA, EventStatus.SCHEDULE, organisationUnitA);

    ImportSummary summary = eventService.addEvent(event, null, false);
    assertEquals(ImportStatus.SUCCESS, summary.getStatus());

    event.setEventDate(EVENT_DATE);

    eventService.updateEventForEventDate(event);

    dbmsManager.clearSession();

    Event psi = programStageInstanceService.getEvent(eventUid);
    assertThat(psi.getStatus(), is(EventStatus.ACTIVE));
  }

  private void cleanSession() {
    entityManager.flush();
    entityManager.clear();
  }

  private InputStream createEventsJsonInputStream(
      List<org.hisp.dhis.dxf2.deprecated.tracker.event.Event> events,
      DataElement dataElement,
      String value) {
    JsonArray jsonArrayEvents = new JsonArray();
    events.stream().forEach(e -> jsonArrayEvents.add(createEventJsonObject(e, dataElement, value)));
    JsonObject jsonEvents = new JsonObject();
    jsonEvents.add("events", jsonArrayEvents);

    return new ByteArrayInputStream(jsonEvents.toString().getBytes());
  }

  private InputStream createEventJsonInputStream(
      String program,
      String programStage,
      String orgUnit,
      String person,
      DataElement dataElement,
      String value) {
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event = createEvent(null);
    event.setProgram(program);
    event.setProgramStage(programStage);
    event.setOrgUnit(orgUnit);
    event.setTrackedEntityInstance(person);
    return new ByteArrayInputStream(
        createEventJsonObject(event, dataElement, value).toString().getBytes());
  }

  private JsonObject createEventJsonObject(
      org.hisp.dhis.dxf2.deprecated.tracker.event.Event event,
      DataElement dataElement,
      String value) {
    JsonObject eventJsonPayload = new JsonObject();
    eventJsonPayload.addProperty("program", event.getProgram());
    eventJsonPayload.addProperty("programStage", event.getProgramStage());
    eventJsonPayload.addProperty("orgUnit", event.getOrgUnit());
    eventJsonPayload.addProperty("status", "COMPLETED");
    eventJsonPayload.addProperty("eventDate", "2018-08-20");
    eventJsonPayload.addProperty("completedDate", "2018-08-27");
    eventJsonPayload.addProperty("trackedEntityInstance", event.getTrackedEntityInstance());
    JsonObject dataValue = new JsonObject();
    dataValue.addProperty("dataElement", dataElement.getUid());
    dataValue.addProperty("value", value);
    // JsonObject geometry = new JsonObject();
    // geometry.put( "type", "Point" );
    // JsonArray coordinates = new JsonArray();
    // coordinates.add( "1.33343" );
    // coordinates.add( "-21.9954" );
    // geometry.put( "coordinates", coordinates );
    // eventJsonPayload.put( "geometry", geometry );
    JsonArray dataValues = new JsonArray();
    dataValues.add(dataValue);
    eventJsonPayload.add("dataValues", dataValues);
    return eventJsonPayload;
  }

  private org.hisp.dhis.dxf2.deprecated.tracker.enrollment.Enrollment createEnrollment(
      String program, String person) {
    org.hisp.dhis.dxf2.deprecated.tracker.enrollment.Enrollment enrollment =
        new org.hisp.dhis.dxf2.deprecated.tracker.enrollment.Enrollment();
    enrollment.setOrgUnit(organisationUnitA.getUid());
    enrollment.setProgram(program);
    enrollment.setTrackedEntityInstance(person);
    enrollment.setEnrollmentDate(new Date());
    enrollment.setIncidentDate(new Date());
    return enrollment;
  }

  private org.hisp.dhis.dxf2.deprecated.tracker.event.Event createEvent(String uid) {
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event =
        new org.hisp.dhis.dxf2.deprecated.tracker.event.Event();
    event.setUid(uid);
    event.setEvent(uid);
    event.setStatus(EventStatus.ACTIVE);
    event.setProgram(programB.getUid());
    event.setProgramStage(programStageB.getUid());
    event.setTrackedEntityInstance(trackedEntityInstanceMaleA.getTrackedEntityInstance());
    event.setOrgUnit(organisationUnitB.getUid());
    event.setEnrollment(enrollment.getUid());
    event.setEventDate(EVENT_DATE);
    event.setDeleted(false);
    return event;
  }

  private org.hisp.dhis.dxf2.deprecated.tracker.event.Event createScheduledTrackerEvent(
      String uid,
      Program program,
      ProgramStage ps,
      EventStatus eventStatus,
      OrganisationUnit organisationUnit) {
    org.hisp.dhis.dxf2.deprecated.tracker.event.Event event =
        new org.hisp.dhis.dxf2.deprecated.tracker.event.Event();
    event.setUid(uid);
    event.setEvent(uid);
    event.setStatus(eventStatus);
    event.setProgram(program.getUid());
    event.setProgramStage(ps.getUid());
    event.setTrackedEntityInstance(trackedEntityInstanceMaleA.getTrackedEntityInstance());
    event.setOrgUnit(organisationUnit.getUid());
    event.setEnrollment(enrollment.getUid());
    event.setDueDate(DUE_DATE);
    event.setDeleted(false);
    return event;
  }
}

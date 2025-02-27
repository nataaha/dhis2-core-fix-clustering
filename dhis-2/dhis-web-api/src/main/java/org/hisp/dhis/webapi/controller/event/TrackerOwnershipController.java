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
package org.hisp.dhis.webapi.controller.event;

import static org.hisp.dhis.dxf2.webmessage.WebMessageUtils.ok;
import static org.hisp.dhis.webapi.controller.tracker.export.RequestParamsValidator.validateMandatoryDeprecatedUidParameter;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.hisp.dhis.common.DhisApiVersion;
import org.hisp.dhis.common.OpenApi;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.dxf2.webmessage.WebMessage;
import org.hisp.dhis.feedback.BadRequestException;
import org.hisp.dhis.fieldfilter.FieldFilterService;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.program.ProgramService;
import org.hisp.dhis.trackedentity.TrackedEntityService;
import org.hisp.dhis.trackedentity.TrackerOwnershipManager;
import org.hisp.dhis.user.CurrentUserUtil;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserService;
import org.hisp.dhis.webapi.mvc.annotation.ApiVersion;
import org.hisp.dhis.webapi.service.ContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Ameen Mohamed <ameen@dhis2.org>
 */
@OpenApi.Tags("tracker")
@Controller
@RequestMapping(value = TrackerOwnershipController.RESOURCE_PATH)
@ApiVersion({DhisApiVersion.DEFAULT, DhisApiVersion.ALL})
public class TrackerOwnershipController {
  public static final String RESOURCE_PATH = "/tracker/ownership";

  @Autowired private UserService userService;

  @Autowired private TrackerOwnershipManager trackerOwnershipAccessManager;

  @Autowired protected FieldFilterService fieldFilterService;

  @Autowired protected ContextService contextService;

  @Autowired private TrackedEntityService trackedEntityService;

  @Autowired private ProgramService programService;

  @Autowired private OrganisationUnitService organisationUnitService;

  // -------------------------------------------------------------------------
  // 1. Transfer ownership if the logged in user is part of the owner ou.
  // 2. Break the glass and override ownership.
  // -------------------------------------------------------------------------

  @PutMapping(value = "/transfer", produces = APPLICATION_JSON_VALUE)
  @ResponseBody
  public WebMessage updateTrackerProgramOwner(
      @Deprecated(since = "2.41") @RequestParam(required = false) UID trackedEntityInstance,
      @RequestParam(required = false) UID trackedEntity,
      @RequestParam String program,
      @RequestParam String ou)
      throws BadRequestException {
    UID trackedEntityUid =
        validateMandatoryDeprecatedUidParameter(
            "trackedEntityInstance", trackedEntityInstance, "trackedEntity", trackedEntity);

    trackerOwnershipAccessManager.transferOwnership(
        trackedEntityService.getTrackedEntity(trackedEntityUid.getValue()),
        programService.getProgram(program),
        organisationUnitService.getOrganisationUnit(ou),
        false,
        false);
    return ok("Ownership transferred");
  }

  @PostMapping(value = "/override", produces = APPLICATION_JSON_VALUE)
  @ResponseBody
  public WebMessage overrideOwnershipAccess(
      @Deprecated(since = "2.41") @RequestParam(required = false) UID trackedEntityInstance,
      @RequestParam(required = false) UID trackedEntity,
      @RequestParam String reason,
      @RequestParam String program)
      throws BadRequestException {
    UID trackedEntityUid =
        validateMandatoryDeprecatedUidParameter(
            "trackedEntityInstance", trackedEntityInstance, "trackedEntity", trackedEntity);

    User currentUser = userService.getUserByUsername(CurrentUserUtil.getCurrentUsername());
    trackerOwnershipAccessManager.grantTemporaryOwnership(
        trackedEntityService.getTrackedEntity(trackedEntityUid.getValue()),
        programService.getProgram(program),
        currentUser,
        reason);

    return ok("Temporary Ownership granted");
  }
}

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
package org.hisp.dhis.webapi.controller.tracker.export.enrollment;

import static org.hisp.dhis.webapi.controller.tracker.ControllerSupport.RESOURCE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.hisp.dhis.common.DhisApiVersion;
import org.hisp.dhis.common.OpenApi;
import org.hisp.dhis.commons.util.TextUtils;
import org.hisp.dhis.feedback.BadRequestException;
import org.hisp.dhis.feedback.ForbiddenException;
import org.hisp.dhis.feedback.NotFoundException;
import org.hisp.dhis.fieldfiltering.FieldFilterService;
import org.hisp.dhis.fieldfiltering.FieldPath;
import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramInstanceQueryParams;
import org.hisp.dhis.tracker.export.enrollment.EnrollmentParams;
import org.hisp.dhis.tracker.export.enrollment.EnrollmentService;
import org.hisp.dhis.tracker.export.enrollment.Enrollments;
import org.hisp.dhis.webapi.controller.event.webrequest.PagingWrapper;
import org.hisp.dhis.webapi.controller.tracker.view.Enrollment;
import org.hisp.dhis.webapi.mvc.annotation.ApiVersion;
import org.mapstruct.factory.Mappers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.node.ObjectNode;

@OpenApi.Tags( "tracker" )
@RestController
@RequestMapping( value = RESOURCE_PATH + "/" + EnrollmentsExportController.ENROLLMENTS )
@ApiVersion( { DhisApiVersion.DEFAULT, DhisApiVersion.ALL } )
@RequiredArgsConstructor
public class EnrollmentsExportController
{
    protected static final String ENROLLMENTS = "enrollments";

    private static final String DEFAULT_FIELDS_PARAM = "*,!relationships,!events,!attributes";

    private static final EnrollmentMapper ENROLLMENT_MAPPER = Mappers.getMapper( EnrollmentMapper.class );

    private final EnrollmentCriteriaMapper enrollmentCriteriaMapper;

    private final EnrollmentService enrollmentService;

    private final FieldFilterService fieldFilterService;

    private final EnrollmentFieldsParamMapper fieldsMapper;

    @GetMapping( produces = APPLICATION_JSON_VALUE )
    PagingWrapper<ObjectNode> getInstances(
        EnrollmentCriteria enrollmentCriteria,
        @RequestParam( defaultValue = DEFAULT_FIELDS_PARAM ) List<FieldPath> fields )
        throws BadRequestException,
        ForbiddenException
    {
        PagingWrapper<ObjectNode> pagingWrapper = new PagingWrapper<>();

        List<ProgramInstance> enrollmentList;

        EnrollmentParams enrollmentParams = fieldsMapper.map( fields )
            .withIncludeDeleted( enrollmentCriteria.isIncludeDeleted() );

        if ( enrollmentCriteria.getEnrollment() == null )
        {
            ProgramInstanceQueryParams params = enrollmentCriteriaMapper.map( enrollmentCriteria );

            Enrollments enrollments = enrollmentService.getEnrollments( params );

            if ( enrollmentCriteria.isPagingRequest() )
            {
                pagingWrapper = pagingWrapper.withPager(
                    PagingWrapper.Pager.fromLegacy( enrollmentCriteria, enrollments.getPager() ) );
            }

            enrollmentList = enrollments.getEnrollments();
        }
        else
        {
            Set<String> enrollmentIds = TextUtils.splitToSet( enrollmentCriteria.getEnrollment(),
                TextUtils.SEMICOLON );
            enrollmentList = enrollmentIds != null
                ? enrollmentIds.stream().map( e -> enrollmentService.getEnrollment( e, enrollmentParams ) )
                    .toList()
                : Collections.emptyList();
        }

        List<ObjectNode> objectNodes = fieldFilterService
            .toObjectNodes( ENROLLMENT_MAPPER.fromCollection( enrollmentList ), fields );
        return pagingWrapper.withInstances( objectNodes );
    }

    @GetMapping( value = "{id}" )
    public ResponseEntity<ObjectNode> getEnrollmentById(
        @PathVariable String id,
        @RequestParam( defaultValue = DEFAULT_FIELDS_PARAM ) List<FieldPath> fields )
        throws NotFoundException
    {
        EnrollmentParams enrollmentParams = fieldsMapper.map( fields );

        Enrollment enrollment = ENROLLMENT_MAPPER
            .from( enrollmentService.getEnrollment( id, enrollmentParams ) );
        if ( enrollment == null )
        {
            throw new NotFoundException( Enrollment.class, id );
        }
        return ResponseEntity.ok( fieldFilterService.toObjectNode( enrollment, fields ) );
    }
}

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
package org.hisp.dhis.tracker.export.trackedentity;

import java.util.List;
import java.util.Set;
import org.hisp.dhis.feedback.BadRequestException;
import org.hisp.dhis.feedback.ForbiddenException;
import org.hisp.dhis.feedback.NotFoundException;
import org.hisp.dhis.trackedentity.TrackedEntity;
import org.hisp.dhis.tracker.export.Page;
import org.hisp.dhis.tracker.export.PageParams;

public interface TrackedEntityService {

  TrackedEntity getTrackedEntity(String uid, TrackedEntityParams params, boolean includeDeleted)
      throws NotFoundException, ForbiddenException;

  TrackedEntity getTrackedEntity(
      TrackedEntity trackedEntity, TrackedEntityParams params, boolean includeDeleted)
      throws ForbiddenException;

  TrackedEntity getTrackedEntity(
      String uid, String programIdentifier, TrackedEntityParams params, boolean includeDeleted)
      throws NotFoundException, ForbiddenException;

  /** Get all tracked entities matching given params. */
  List<TrackedEntity> getTrackedEntities(TrackedEntityOperationParams operationParams)
      throws BadRequestException, ForbiddenException, NotFoundException;

  /** Get a page of tracked entities matching given params. */
  Page<TrackedEntity> getTrackedEntities(TrackedEntityOperationParams params, PageParams pageParams)
      throws BadRequestException, ForbiddenException, NotFoundException;

  /**
   * Fields the {@link #getTrackedEntities(TrackedEntityOperationParams)} can order tracked entities
   * by. Ordering by fields other than these is considered a programmer error. Validation of user
   * provided field names should occur before calling {@link
   * #getTrackedEntities(TrackedEntityOperationParams)}.
   */
  Set<String> getOrderableFields();
}

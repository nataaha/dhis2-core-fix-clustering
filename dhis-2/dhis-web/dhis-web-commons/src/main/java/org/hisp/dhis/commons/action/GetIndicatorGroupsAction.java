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
package org.hisp.dhis.commons.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hisp.dhis.common.IdentifiableObjectUtils;
import org.hisp.dhis.commons.filter.FilterUtils;
import org.hisp.dhis.indicator.IndicatorGroup;
import org.hisp.dhis.indicator.IndicatorService;
import org.hisp.dhis.paging.ActionPagingSupport;
import org.hisp.dhis.system.filter.IndicatorGroupWithoutGroupSetFilter;
import org.hisp.dhis.user.CurrentUserUtil;
import org.hisp.dhis.user.UserDetails;

/**
 * @author mortenoh
 */
public class GetIndicatorGroupsAction extends ActionPagingSupport<IndicatorGroup> {
  // -------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------

  private IndicatorService indicatorService;

  public void setIndicatorService(IndicatorService indicatorService) {
    this.indicatorService = indicatorService;
  }

  // -------------------------------------------------------------------------
  // Input & Output
  // -------------------------------------------------------------------------

  private String key;

  public void setKey(String key) {
    this.key = key;
  }

  public boolean filterNoGroupSet = false;

  public void setFilterNoGroupSet(boolean filterNoGroupSet) {
    this.filterNoGroupSet = filterNoGroupSet;
  }

  private List<IndicatorGroup> indicatorGroups;

  public List<IndicatorGroup> getIndicatorGroups() {
    return indicatorGroups;
  }

  // -------------------------------------------------------------------------
  // Action implementation
  // -------------------------------------------------------------------------

  @Override
  public String execute() {
    canReadType(IndicatorGroup.class);

    indicatorGroups = new ArrayList<>(indicatorService.getAllIndicatorGroups());

    if (filterNoGroupSet) {
      FilterUtils.filter(indicatorGroups, new IndicatorGroupWithoutGroupSetFilter());
    }

    if (key != null) {
      indicatorGroups = IdentifiableObjectUtils.filterNameByKey(indicatorGroups, key, true);
    }

    Collections.sort(indicatorGroups);

    UserDetails currentUserDetails = CurrentUserUtil.getCurrentUserDetails();
    indicatorGroups.forEach(instance -> canReadInstance(instance, currentUserDetails));

    if (usePaging) {
      this.paging = createPaging(indicatorGroups.size());

      indicatorGroups = indicatorGroups.subList(paging.getStartPos(), paging.getEndPos());
    }

    return SUCCESS;
  }
}

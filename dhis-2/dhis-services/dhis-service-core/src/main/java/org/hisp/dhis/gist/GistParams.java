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
package org.hisp.dhis.gist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.hisp.dhis.common.OpenApi;
import org.hisp.dhis.feedback.BadRequestException;
import org.hisp.dhis.query.Junction;

/**
 * Web API input params for {@link GistQuery}.
 *
 * @author Jan Bernitt
 */
@Data
@OpenApi.Shared
public final class GistParams {
  String locale = "";

  GistAutoType auto;

  int page = 1;

  int pageSize = 50;

  /**
   * The name of the property in the response object that holds the list of response objects when a
   * paged response is used.
   */
  String pageListName;

  boolean translate = true;

  boolean inverse = false;

  @Deprecated(since = "2.41", forRemoval = true)
  Boolean total;

  Boolean totalPages;

  boolean absoluteUrls = false;

  Boolean headless;

  Boolean paging;

  boolean describe = false;

  boolean references = true;

  Junction.Type rootJunction = Junction.Type.AND;

  String fields;

  String filter;

  String order;

  public GistAutoType getAuto(GistAutoType defaultValue) {
    return auto == null ? defaultValue : auto;
  }

  @JsonIgnore
  public boolean isCountTotalPages() throws BadRequestException {
    if (totalPages != null && total != null && totalPages != total)
      throw new BadRequestException(
          "totalPages and total request parameters are contradicting each other");
    if (totalPages != null) return totalPages;
    if (total != null) return total;
    return false;
  }

  @JsonIgnore
  public boolean isIncludePager() throws BadRequestException {
    if (paging != null && headless != null && paging == headless)
      throw new BadRequestException(
          "paging and headless request parameters are contradicting each other");
    if (paging != null) return paging;
    if (headless != null) return !headless;
    return true;
  }
}

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
package org.hisp.dhis.scheduling.parameters;

import java.util.Optional;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.feedback.ErrorReport;
import org.hisp.dhis.scheduling.JobParameters;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Jan Bernitt
 */
@Getter
@Setter
@NoArgsConstructor
public class DisableInactiveUsersJobParameters implements JobParameters
{
    @JsonProperty( required = true )
    private int inactiveMonths;

    @JsonProperty
    private Integer reminderDaysBefore;

    public DisableInactiveUsersJobParameters( int inactiveMonths )
    {
        this.inactiveMonths = inactiveMonths;
    }

    @Override
    public Optional<ErrorReport> validate()
    {
        if ( inactiveMonths < 1 || inactiveMonths > 24 )
        {
            return Optional.of(
                new ErrorReport( getClass(), ErrorCode.E4008, "inactiveMonths", 1, 24, inactiveMonths ) );
        }
        if ( reminderDaysBefore != null && (reminderDaysBefore < 1 || reminderDaysBefore > 28) )
        {
            return Optional.of(
                new ErrorReport( getClass(), ErrorCode.E4008, "reminderDaysBefore", 1, 28, reminderDaysBefore ) );
        }
        return Optional.empty();
    }
}

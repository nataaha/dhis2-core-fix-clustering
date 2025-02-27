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
package org.hisp.dhis.analytics.common.params.dimension;

import static java.util.Objects.nonNull;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.hisp.dhis.analytics.common.params.dimension.DimensionParamObjectType.ORGANISATION_UNIT;
import static org.hisp.dhis.analytics.common.params.dimension.DimensionParamObjectType.STATIC;
import static org.hisp.dhis.analytics.common.params.dimension.DimensionParamObjectType.byForeignType;
import static org.hisp.dhis.analytics.common.params.dimension.DimensionParamType.DATE_FILTERS;
import static org.hisp.dhis.analytics.common.params.dimension.DimensionParamType.DIMENSIONS;
import static org.hisp.dhis.analytics.common.params.dimension.DimensionParamType.FILTERS;
import static org.hisp.dhis.analytics.tei.query.context.TeiStaticField.ORG_UNIT_CODE;
import static org.hisp.dhis.analytics.tei.query.context.TeiStaticField.ORG_UNIT_NAME;
import static org.hisp.dhis.analytics.tei.query.context.TeiStaticField.ORG_UNIT_NAME_HIERARCHY;
import static org.hisp.dhis.analytics.tei.query.context.TeiStaticField.TRACKED_ENTITY_INSTANCE;
import static org.hisp.dhis.common.DimensionType.PERIOD;
import static org.hisp.dhis.common.QueryOperator.EQ;
import static org.hisp.dhis.common.ValueType.COORDINATE;
import static org.hisp.dhis.common.ValueType.DATETIME;
import static org.hisp.dhis.common.ValueType.GEOJSON;
import static org.hisp.dhis.common.ValueType.TEXT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.analytics.tei.query.context.TeiHeaderProvider;
import org.hisp.dhis.analytics.tei.query.context.TeiStaticField;
import org.hisp.dhis.common.DimensionalObject;
import org.hisp.dhis.common.QueryItem;
import org.hisp.dhis.common.UidObject;
import org.hisp.dhis.common.ValueType;

/**
 * Object responsible to wrap/encapsulate instances of DimensionObject|QueryItem|StaticDimension.
 */
@Data
@Builder(access = PRIVATE)
@RequiredArgsConstructor(access = PRIVATE)
public class DimensionParam implements UidObject {
  private final DimensionalObject dimensionalObject;

  private final QueryItem queryItem;

  private final StaticDimension staticDimension;

  private final DimensionParamType type;

  @Builder.Default private final List<DimensionParamItem> items = new ArrayList<>();

  /**
   * Allows to create an instance of DimensionParam. We should pass the object to be wrapped (a
   * {@link DimensionalObject}, a {@link QueryItem} or a static dimension), the type ({@link
   * DimensionParamType}) and a list of filters ({@link List<String>}). This last can be empty.
   *
   * @param dimensionalObjectOrQueryItem either a {@link DimensionalObject} or {@link QueryItem}, or
   *     a static dimension.
   * @param dimensionParamType the {@link DimensionParamType} for the {@link DimensionParam}
   *     returned (whether it's a filter or a dimension).
   * @param items the list of items parameters for this DimensionParam.
   * @return a new instance of {@link DimensionParam}.
   */
  public static DimensionParam ofObject(
      Object dimensionalObjectOrQueryItem,
      DimensionParamType dimensionParamType,
      List<String> items) {
    Objects.requireNonNull(dimensionalObjectOrQueryItem);
    Objects.requireNonNull(dimensionParamType);

    if (dimensionParamType == DATE_FILTERS) {
      items = items.stream().map(item -> EQ + ":" + item).collect(Collectors.toList());
    }

    DimensionParamBuilder builder =
        DimensionParam.builder()
            .type(dimensionParamType)
            .items(DimensionParamItem.ofStrings(items));

    if (dimensionalObjectOrQueryItem instanceof DimensionalObject) {
      return builder.dimensionalObject((DimensionalObject) dimensionalObjectOrQueryItem).build();
    }

    if (dimensionalObjectOrQueryItem instanceof QueryItem) {
      return builder.queryItem((QueryItem) dimensionalObjectOrQueryItem).build();
    }

    // If this is neither a DimensionalObject nor a QueryItem, we try to see if it's a static
    // Dimension.
    Optional<StaticDimension> staticDimension =
        StaticDimension.of(dimensionalObjectOrQueryItem.toString());

    if (staticDimension.isPresent()) {
      return builder.staticDimension(staticDimension.get()).build();
    }

    String receivedIdentifier =
        dimensionalObjectOrQueryItem.getClass().equals(String.class)
            ? dimensionalObjectOrQueryItem.toString()
            : dimensionalObjectOrQueryItem.getClass().getName();

    throw new IllegalArgumentException(
        "Only DimensionalObject, QueryItem or static dimensions are allowed. Received "
            + receivedIdentifier
            + " instead");
  }

  /**
   * @return true if this DimensionParams has some items on it.
   */
  public boolean hasRestrictions() {
    return isNotEmpty(items);
  }

  /**
   * @return true if this DimensionParam is a filter.
   */
  public boolean isFilter() {
    return type == FILTERS;
  }

  /**
   * @return true if this DimensionParam is a dimension
   */
  public boolean isDimension() {
    return type == DIMENSIONS;
  }

  public boolean isDimensionalObject() {
    return nonNull(dimensionalObject);
  }

  public boolean isQueryItem() {
    return nonNull(queryItem);
  }

  public boolean isStaticDimension() {
    return !isQueryItem() && !isDimensionalObject();
  }

  /**
   * Returns the type of the current {@link DimensionParam} instance.
   *
   * @return the respective {@link DimensionParamObjectType}.
   */
  public DimensionParamObjectType getDimensionParamObjectType() {
    if (isDimensionalObject()) {
      return byForeignType(dimensionalObject.getDimensionType());
    }

    if (isQueryItem()) {
      return byForeignType(queryItem.getItem().getDimensionItemType());
    }

    return staticDimension.getDimensionParamObjectType();
  }

  public boolean isOfType(DimensionParamObjectType type) {
    return getDimensionParamObjectType() == type;
  }

  public ValueType getValueType() {
    if (isDimensionalObject()) {
      return dimensionalObject.getValueType();
    }

    if (isQueryItem()) {
      return queryItem.getValueType();
    }

    return staticDimension.valueType;
  }

  @Override
  public String getUid() {
    if (isDimensionalObject()) {
      return dimensionalObject.getUid();
    }

    if (isQueryItem()) {
      return queryItem.getItem().getUid();
    }

    return staticDimension.getHeaderName();
  }

  public boolean isPeriodDimension() {
    return isDimensionalObject() && dimensionalObject.getDimensionType() == PERIOD
        || isStaticDimension()
            && staticDimension.getDimensionParamObjectType() == DimensionParamObjectType.PERIOD;
  }

  public String getName() {
    if (isDimensionalObject()) {
      return dimensionalObject.getName();
    }

    if (isQueryItem()) {
      return queryItem.getItem().getName();
    }

    return staticDimension.name();
  }

  @RequiredArgsConstructor
  public enum StaticDimension implements TeiHeaderProvider {
    TRACKEDENTITYINSTANCEUID(TEXT, STATIC, TRACKED_ENTITY_INSTANCE),
    GEOMETRY(GEOJSON, STATIC, TeiStaticField.GEOMETRY),
    LONGITUDE(COORDINATE, STATIC, TeiStaticField.LONGITUDE),
    LATITUDE(COORDINATE, STATIC, TeiStaticField.LATITUDE),
    OUNAME(TEXT, ORGANISATION_UNIT, ORG_UNIT_NAME),
    OUCODE(TEXT, ORGANISATION_UNIT, ORG_UNIT_CODE),
    OUNAMEHIERARCHY(TEXT, ORGANISATION_UNIT, ORG_UNIT_NAME_HIERARCHY),
    ENROLLMENTDATE(DATETIME, DimensionParamObjectType.PERIOD),
    ENDDATE(DATETIME, DimensionParamObjectType.PERIOD),
    INCIDENTDATE(DATETIME, DimensionParamObjectType.PERIOD),
    EXECUTIONDATE(DATETIME, DimensionParamObjectType.PERIOD),
    LASTUPDATED(DATETIME, DimensionParamObjectType.PERIOD, TeiStaticField.LAST_UPDATED),
    LASTUPDATEDBYDISPLAYNAME(TEXT, STATIC),
    CREATED(DATETIME, DimensionParamObjectType.PERIOD),
    CREATEDBYDISPLAYNAME(TEXT, STATIC),
    STOREDBY(TEXT, STATIC),
    ENROLLMENT_STATUS(TEXT, STATIC, null, "enrollmentstatus"),
    PROGRAM_STATUS(
        TEXT,
        STATIC,
        null,
        "enrollmentstatus",
        "programstatus"), /* this enum is an alias for ENROLLMENT_STATUS */
    EVENT_STATUS(TEXT, STATIC, null, "status", "eventstatus");

    private final ValueType valueType;

    @Getter private final String columnName;

    @Getter private final DimensionParamObjectType dimensionParamObjectType;

    private final TeiStaticField teiStaticField;

    @Getter private final String headerName;

    StaticDimension(ValueType valueType, DimensionParamObjectType dimensionParamObjectType) {
      this(valueType, dimensionParamObjectType, null);
    }

    StaticDimension(
        ValueType valueType,
        DimensionParamObjectType dimensionParamObjectType,
        TeiStaticField teiStaticField) {
      this.valueType = valueType;

      // By default, columnName is its own "name" in lowercase.
      this.columnName = normalizedName();

      this.dimensionParamObjectType = dimensionParamObjectType;

      this.teiStaticField = teiStaticField;

      this.headerName = this.columnName;
    }

    StaticDimension(
        ValueType valueType,
        DimensionParamObjectType dimensionParamObjectType,
        TeiStaticField teiStaticField,
        String columnName) {
      this(valueType, dimensionParamObjectType, teiStaticField, columnName, columnName);
    }

    StaticDimension(
        ValueType valueType,
        DimensionParamObjectType dimensionParamObjectType,
        TeiStaticField teiStaticField,
        String columnName,
        String headerName) {
      this.valueType = valueType;
      this.dimensionParamObjectType = dimensionParamObjectType;
      this.teiStaticField = teiStaticField;
      this.columnName = columnName;
      this.headerName = headerName;
    }

    public String normalizedName() {
      return name().toLowerCase().replace("_", "");
    }

    public static Optional<StaticDimension> of(String value) {
      return Arrays.stream(StaticDimension.values())
          .filter(
              sd ->
                  equalsIgnoreCase(sd.columnName, value)
                      || equalsIgnoreCase(sd.name(), value)
                      || equalsIgnoreCase(sd.normalizedName(), value))
          .findFirst();
    }

    @Override
    public String getAlias() {
      return Optional.ofNullable(teiStaticField).map(TeiStaticField::getAlias).orElse(name());
    }

    @Override
    public String getFullName() {
      return Optional.ofNullable(teiStaticField).map(TeiStaticField::getFullName).orElse(name());
    }

    @Override
    public ValueType getType() {
      return valueType;
    }

    public boolean isTeiStaticField() {
      return nonNull(teiStaticField);
    }
  }
}

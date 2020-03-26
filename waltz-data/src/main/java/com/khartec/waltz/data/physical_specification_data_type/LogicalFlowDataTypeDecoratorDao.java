/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

package com.khartec.waltz.data.physical_specification_data_type;

import com.khartec.waltz.common.SetUtilities;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.FlowDirection;
import com.khartec.waltz.model.ImmutableEntityReference;
import com.khartec.waltz.model.authoritativesource.AuthoritativeRatingVantagePoint;
import com.khartec.waltz.model.data_flow_decorator.*;
import com.khartec.waltz.model.datatype.DataType;
import com.khartec.waltz.model.datatype.DataTypeDecorator;
import com.khartec.waltz.model.datatype.ImmutableDataTypeDecorator;
import com.khartec.waltz.model.rating.AuthoritativenessRating;
import com.khartec.waltz.schema.tables.LogicalFlow;
import com.khartec.waltz.schema.tables.records.LogicalFlowDecoratorRecord;
import com.khartec.waltz.schema.tables.records.PhysicalSpecDataTypeRecord;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.lambda.function.Function2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.common.Checks.checkOptionalIsPresent;
import static com.khartec.waltz.common.ListUtilities.newArrayList;
import static com.khartec.waltz.data.logical_flow.LogicalFlowDao.LOGICAL_NOT_REMOVED;
import static com.khartec.waltz.model.EntityKind.DATA_TYPE;
import static com.khartec.waltz.model.EntityKind.LOGICAL_DATA_FLOW;
import static com.khartec.waltz.model.EntityLifecycleStatus.REMOVED;
import static com.khartec.waltz.model.EntityReference.mkRef;
import static com.khartec.waltz.schema.tables.Application.APPLICATION;
import static com.khartec.waltz.schema.tables.EntityHierarchy.ENTITY_HIERARCHY;
import static com.khartec.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;
import static com.khartec.waltz.schema.tables.LogicalFlowDecorator.LOGICAL_FLOW_DECORATOR;
import static java.util.stream.Collectors.toList;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.when;


@Repository
public class LogicalFlowDataTypeDecoratorDao extends DataTypeDecoratorDao {


    private static final RecordMapper<Record, DataTypeDecorator> TO_DECORATOR_MAPPER = r -> {
        LogicalFlowDecoratorRecord record = r.into(LOGICAL_FLOW_DECORATOR);

        return ImmutableDataTypeDecorator.builder()
                .entityReference(mkRef(LOGICAL_DATA_FLOW, record.getLogicalFlowId()))
                .decoratorEntity(mkRef(
                        DATA_TYPE,
                        record.getDecoratorEntityId()))
                .rating(Optional.of(AuthoritativenessRating.valueOf(record.getRating())))
                .provenance(record.getProvenance())
                .lastUpdatedAt(record.getLastUpdatedAt().toLocalDateTime())
                .lastUpdatedBy(record.getLastUpdatedBy())
                .build();
    };

    private static final Function<DataTypeDecorator, LogicalFlowDecoratorRecord> TO_RECORD = d -> {
        LogicalFlowDecoratorRecord r = new LogicalFlowDecoratorRecord();
        r.setId(d.id().orElse(null));
        r.changed(LOGICAL_FLOW_DECORATOR.ID, false);
        r.setDecoratorEntityKind(d.decoratorEntity().kind().name());
        r.setDecoratorEntityId(d.decoratorEntity().id());
        r.setLogicalFlowId(d.dataFlowId());
        r.setProvenance(d.provenance());
        d.rating().ifPresent(rating -> r.setRating(rating.name()));
        r.setLastUpdatedAt(Timestamp.valueOf(d.lastUpdatedAt()));
        r.setLastUpdatedBy(d.lastUpdatedBy());
        return r;
    };

    private final DSLContext dsl;

    private static final LogicalFlow lf = LOGICAL_FLOW.as("lf");
    private static final com.khartec.waltz.schema.tables.LogicalFlowDecorator lfd = LOGICAL_FLOW_DECORATOR.as("lfd");

    @Autowired
    public LogicalFlowDataTypeDecoratorDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl cannot be null");
        this.dsl = dsl;
    }


    // --- FINDERS ---

    public DataTypeDecorator getByEntityIdAndDataTypeId(long flowId, long dataTypeId) {

        return dsl
                .selectFrom(LOGICAL_FLOW_DECORATOR)
                .where(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(flowId))
                .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(DATA_TYPE.name()))
                .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID.eq(dataTypeId))
                .fetchOne(TO_DECORATOR_MAPPER);
    }

    @Override
    public List<DataTypeDecorator> findByEntityId(long entityId) {
        return dsl
                .selectFrom(LOGICAL_FLOW_DECORATOR)
                .where(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(entityId))
                .fetch(TO_DECORATOR_MAPPER);
    }


    public List<DataTypeDecorator> findAll() {
        return dsl
                .selectFrom(LOGICAL_FLOW_DECORATOR)
                .fetch(TO_DECORATOR_MAPPER);
    }

    public List<DataTypeDecorator> findByEntityIdSelector(Select<Record1<Long>> flowSelector, Optional<EntityKind> entityKind) {
        return dsl.select(LOGICAL_FLOW_DECORATOR.fields())
                .from(LOGICAL_FLOW_DECORATOR)
                .innerJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.ID.eq(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID))
                .where(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.in(flowSelector))
                .and(LOGICAL_NOT_REMOVED)
                .fetch(TO_DECORATOR_MAPPER);
    }

    public List<DataTypeDecorator> findByAppIdSelector(Select<Record1<Long>> appIdSelector) {
        Condition condition = LOGICAL_FLOW.TARGET_ENTITY_ID.in(appIdSelector)
                .or(LOGICAL_FLOW.SOURCE_ENTITY_ID.in(appIdSelector));

        return dsl.select(LOGICAL_FLOW_DECORATOR.fields())
                .from(LOGICAL_FLOW_DECORATOR)
                .innerJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.ID.eq(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID))
                .and(LOGICAL_NOT_REMOVED)
                .where(dsl.renderInlined(condition))
                .fetch(TO_DECORATOR_MAPPER);
    }



    // --- UPDATERS ---

    public int[] deleteDecorators(Long flowId, Collection<EntityReference> decoratorReferences) {
        List<LogicalFlowDecoratorRecord> records = decoratorReferences
                .stream()
                .map(ref -> {
                    LogicalFlowDecoratorRecord record = dsl.newRecord(LOGICAL_FLOW_DECORATOR);
                    record.setLogicalFlowId(flowId);
                    record.setDecoratorEntityId(ref.id());
                    record.setDecoratorEntityKind(ref.kind().name());
                    return record;
                })
                .collect(toList());
        return dsl
                .batchDelete(records)
                .execute();
    }

    public int[] removeDataTypes(Collection<DataTypeDecorator> decorators) {
        checkNotNull(decorators, "logical flow decorators cannot be null");

        List<LogicalFlowDecoratorRecord> records = decorators
                .stream()
                .map(TO_RECORD)
                .collect(toList());

        return dsl.batchDelete(records)
                .execute();
    }


    @Deprecated
    // Replace with a method that removes decorators for a single flow
    public int removeAllDecoratorsForFlowIds(List<Long> flowIds) {
        return dsl.deleteFrom(LOGICAL_FLOW_DECORATOR)
                .where(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.in(flowIds))
                .execute();
    }


    public int[] addDataTypes(Collection<DataTypeDecorator> decorators) {
        checkNotNull(decorators, "decorators cannot be null");

        List<LogicalFlowDecoratorRecord> records = decorators.stream()
                .map(TO_RECORD)
                .collect(toList());

        Query[] queries = records.stream().map(
                record -> DSL.using(dsl.configuration())
                        .insertInto(LOGICAL_FLOW_DECORATOR)
                        .set(record)
                        .onDuplicateKeyUpdate()
                        .set(record))
                .toArray(Query[]::new);
        return dsl.batch(queries).execute();
        // todo: in jOOQ 3.10.0 this can be written as follows #2979
        // return dsl.batchInsert(records).onDuplicateKeyIgnore().execute();
    }


    public int[] updateDecorators(Set<DataTypeDecorator> decorators) {
        Set<LogicalFlowDecoratorRecord> records = SetUtilities.map(decorators, TO_RECORD);
        return dsl.batchUpdate(records).execute();
    }


    public int updateDecoratorsForAuthSource(AuthoritativeRatingVantagePoint ratingVantagePoint) {
        com.khartec.waltz.schema.tables.LogicalFlowDecorator lfd = LOGICAL_FLOW_DECORATOR.as("lfd");

        EntityReference vantagePoint = ratingVantagePoint.vantagePoint();
        Long appId = ratingVantagePoint.applicationId();
        EntityReference dataType = ratingVantagePoint.dataType();
        AuthoritativenessRating rating = ratingVantagePoint.rating();

        SelectConditionStep<Record1<Long>> orgUnitSubselect = DSL.select(ENTITY_HIERARCHY.ID)
                .from(ENTITY_HIERARCHY)
                .where(ENTITY_HIERARCHY.KIND.eq(vantagePoint.kind().name()))
                .and(ENTITY_HIERARCHY.ANCESTOR_ID.eq(vantagePoint.id()));

        SelectConditionStep<Record1<Long>> dataTypeSubselect = DSL.select(ENTITY_HIERARCHY.ID)
                .from(ENTITY_HIERARCHY)
                .where(ENTITY_HIERARCHY.KIND.eq(DATA_TYPE.name()))
                .and(ENTITY_HIERARCHY.ANCESTOR_ID.eq(dataType.id()));

        Condition usingAuthSource = LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(appId);
        Condition notUsingAuthSource = LOGICAL_FLOW.SOURCE_ENTITY_ID.ne(appId);

        Function2<Condition, String, Update<LogicalFlowDecoratorRecord>> mkQuery = (appScopingCondition, ratingName) -> dsl
                .update(LOGICAL_FLOW_DECORATOR)
                .set(LOGICAL_FLOW_DECORATOR.RATING, ratingName)
                .where(LOGICAL_FLOW_DECORATOR.ID.in(
                        DSL.select(lfd.ID)
                                .from(lfd)
                                .innerJoin(LOGICAL_FLOW).on(LOGICAL_FLOW.ID.eq(lfd.LOGICAL_FLOW_ID))
                                .innerJoin(APPLICATION)
                                .on(APPLICATION.ID.eq(LOGICAL_FLOW.TARGET_ENTITY_ID)
                                        .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(EntityKind.APPLICATION.name())))
                                .where(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name())
                                        .and(appScopingCondition)
                                        .and(APPLICATION.ORGANISATIONAL_UNIT_ID.in(orgUnitSubselect))
                                        .and(lfd.DECORATOR_ENTITY_KIND.eq(DATA_TYPE.name()))
                                        .and(lfd.DECORATOR_ENTITY_ID.in(dataTypeSubselect)))
                                .and(lfd.RATING.in(AuthoritativenessRating.NO_OPINION.name(), AuthoritativenessRating.DISCOURAGED.name()))

                ));

        Update<LogicalFlowDecoratorRecord> updateAuthSources = mkQuery.apply(usingAuthSource, rating.name());
        Update<LogicalFlowDecoratorRecord> updateNonAuthSources = mkQuery.apply(notUsingAuthSource, AuthoritativenessRating.DISCOURAGED.name());
        int authSourceUpdateCount = updateAuthSources.execute();
        int nonAuthSourceUpdateCount = updateNonAuthSources.execute();
        return authSourceUpdateCount + nonAuthSourceUpdateCount;
    }

    // --- HELPERS ---

    private List<DataTypeDecorator> findByCondition(Condition condition) {
        return dsl
                .select(LOGICAL_FLOW_DECORATOR.fields())
                .from(LOGICAL_FLOW_DECORATOR)
                .where(dsl.renderInlined(condition))
                .fetch(TO_DECORATOR_MAPPER);
    }


    private List<DecoratorRatingSummary> summarizeForCondition(Condition condition) {
        // this is intentionally TARGET only as we use to calculate auth source stats
        Condition dataFlowJoinCondition = LOGICAL_FLOW.ID
                .eq(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID);

        Collection<Field<?>> groupingFields = newArrayList(
                LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND,
                LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID,
                LOGICAL_FLOW_DECORATOR.RATING);

        Field<Integer> countField = DSL.count(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID).as("count");

        return dsl
                .select(groupingFields)
                .select(countField)
                .from(LOGICAL_FLOW_DECORATOR)
                .innerJoin(LOGICAL_FLOW)
                .on(dsl.renderInlined(dataFlowJoinCondition))
                .where(dsl.renderInlined(condition))
                .groupBy(groupingFields)
                .fetch(r -> {
                    EntityKind decoratorEntityKind = EntityKind.valueOf(r.getValue(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND));
                    long decoratorEntityId = r.getValue(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID);

                    EntityReference decoratorRef = mkRef(decoratorEntityKind, decoratorEntityId);
                    AuthoritativenessRating rating = AuthoritativenessRating.valueOf(r.getValue(LOGICAL_FLOW_DECORATOR.RATING));
                    Integer count = r.getValue(countField);

                    return ImmutableDecoratorRatingSummary.builder()
                            .decoratorEntityReference(decoratorRef)
                            .rating(rating)
                            .count(count)
                            .build();
                });
    }


    public int updateRatingsByCondition(AuthoritativenessRating rating, Condition condition) {
        return dsl
                .update(LOGICAL_FLOW_DECORATOR)
                .set(LOGICAL_FLOW_DECORATOR.RATING, rating.name())
                .where(condition)
                .execute();
    }


    public Map<DataTypeDirectionKey, List<Long>> logicalFlowIdsByTypeAndDirection(Select<Record1<Long>> selector) {
        checkNotNull(selector, "selector cannot be null");

        Table<Record1<Long>> sourceApp = selector.asTable("source_app");
        Table<Record1<Long>> targetApp = selector.asTable("target_app");
        Field<Long> sourceAppId = sourceApp.field(0, Long.class);
        Field<Long> targetAppId = targetApp.field(0, Long.class);
        Field<String> flowTypeCase =
                when(sourceAppId.isNotNull()
                        .and(targetAppId.isNotNull()), inline("INTRA"))
                        .when(sourceAppId.isNotNull(), inline("OUTBOUND"))
                        .otherwise(inline("INBOUND"));
        Field<String> flowType = DSL.field("flow_type", String.class);

        Condition condition = sourceAppId
                .isNotNull()
                .or(targetAppId.isNotNull())
                .and(lf.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name())
                        .and(lf.IS_REMOVED.isFalse()));

        return  dsl.select(
                    lfd.DECORATOR_ENTITY_ID,
                    flowTypeCase.as(flowType),
                    lf.ID)
                .from(lf)
                .innerJoin(lfd)
                .on(lf.ID.eq(lfd.LOGICAL_FLOW_ID)
                        .and(lfd.DECORATOR_ENTITY_KIND.eq(inline(DATA_TYPE.name()))))
                .leftJoin(sourceApp)
                .on(sourceAppId.eq(lf.SOURCE_ENTITY_ID))
                .leftJoin(targetApp)
                .on(targetAppId.eq(lf.TARGET_ENTITY_ID))
                .where(dsl.renderInlined(condition))
                .fetchGroups(
                        k -> DataTypeDirectionKey.mkKey(
                                k.get(lfd.DECORATOR_ENTITY_ID),
                                FlowDirection.valueOf(k.get(flowType))),
                        v -> v.get(lf.ID));

    }

    @Override
    public List<DataTypeDecorator> findByFlowIds(List<Long> flowIds) {
        checkNotNull(flowIds, "flowIds cannot be null");

        Condition condition = LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.in(flowIds);

        return findByCondition(condition);
    }


    @Override
    public List<DataTypeDecorator> findByDataTypeIdSelector(Select<Record1<Long>> decoratorEntityIdSelector) {
        checkNotNull(decoratorEntityIdSelector, "decoratorEntityIdSelector cannot be null");

        Condition condition = LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(DATA_TYPE.name())
                .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID.in(decoratorEntityIdSelector));

        return dsl
                .select(LOGICAL_FLOW_DECORATOR.fields())
                .from(LOGICAL_FLOW_DECORATOR)
                .innerJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.ID.eq(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID))
                .where(dsl.renderInlined(condition))
                .and(LOGICAL_NOT_REMOVED)
                .fetch(TO_DECORATOR_MAPPER);
    }
}

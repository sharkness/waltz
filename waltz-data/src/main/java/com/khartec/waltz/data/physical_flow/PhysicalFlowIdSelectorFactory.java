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

package com.khartec.waltz.data.physical_flow;

import com.khartec.waltz.data.IdSelectorFactory;
import com.khartec.waltz.data.logical_flow.LogicalFlowIdSelectorFactory;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityLifecycleStatus;
import com.khartec.waltz.model.IdSelectionOptions;
import com.khartec.waltz.schema.tables.PhysicalFlow;
import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.data.SelectorUtilities.ensureScopeIsExact;
import static com.khartec.waltz.schema.Tables.*;
import static com.khartec.waltz.schema.tables.FlowDiagramEntity.FLOW_DIAGRAM_ENTITY;
import static com.khartec.waltz.schema.tables.PhysicalFlow.PHYSICAL_FLOW;
import static com.khartec.waltz.schema.tables.PhysicalFlowParticipant.PHYSICAL_FLOW_PARTICIPANT;


public class PhysicalFlowIdSelectorFactory implements IdSelectorFactory {

    private static final Condition PHYSICAL_FLOW_NOT_REMOVED = PhysicalFlow.PHYSICAL_FLOW.IS_REMOVED.isFalse()
            .and(PhysicalFlow.PHYSICAL_FLOW.ENTITY_LIFECYCLE_STATUS.ne(EntityLifecycleStatus.REMOVED.name()));

    @Override
    public Select<Record1<Long>> apply(IdSelectionOptions options) {
        checkNotNull(options, "options cannot be null");
        switch(options.entityReference().kind()) {
            case PHYSICAL_SPECIFICATION:
                return mkForPhysicalSpecification(options);
            case LOGICAL_DATA_FLOW:
                return mkForLogicalFlow(options);
            case FLOW_DIAGRAM:
                return mkForFlowDiagram(options);
            case SERVER:
                return mkForServer(options);
            case CHANGE_SET:
                return mkForChangeSet(options);
            case TAG:
                return mkForTag(options);
            case ORG_UNIT:
            case CHANGE_INITIATIVE:
            case MEASURABLE:
            case PERSON:
            case DATA_TYPE:
            case SCENARIO:
                return mkViaLogicalFlowSelector(options);
            default:
                throw new UnsupportedOperationException("Cannot create physical flow selector from options: "+options);
        }
    }


    private Select<Record1<Long>> mkViaLogicalFlowSelector(IdSelectionOptions options) {
        Select<Record1<Long>> logicalFlowSelector = new LogicalFlowIdSelectorFactory().apply(options);

        return DSL
                .select(PHYSICAL_FLOW.ID)
                .from(PHYSICAL_FLOW)
                .where(PHYSICAL_FLOW.LOGICAL_FLOW_ID.in(logicalFlowSelector));

    }


    private Select<Record1<Long>> mkForTag(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        long tagId = options.entityReference().id();

        return DSL
                .select(TAG_USAGE.ENTITY_ID)
                .from(TAG_USAGE)
                .join(PhysicalFlow.PHYSICAL_FLOW)
                .on(PhysicalFlow.PHYSICAL_FLOW.ID.eq(TAG_USAGE.ENTITY_ID)
                        .and(TAG_USAGE.ENTITY_KIND.eq(EntityKind.PHYSICAL_FLOW.name())))
                .where(TAG_USAGE.TAG_ID.eq(tagId))
                .and(getLifecycleCondition(options));
    }


    private Select<Record1<Long>> mkForServer(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        long serverId = options.entityReference().id();

        return DSL
                .select(PHYSICAL_FLOW_PARTICIPANT.PHYSICAL_FLOW_ID)
                .from(PHYSICAL_FLOW_PARTICIPANT)
                .innerJoin(PhysicalFlow.PHYSICAL_FLOW)
                .on(PhysicalFlow.PHYSICAL_FLOW.ID.eq(PHYSICAL_FLOW_PARTICIPANT.PHYSICAL_FLOW_ID))
                .where(PHYSICAL_FLOW_PARTICIPANT.PARTICIPANT_ENTITY_KIND.eq(EntityKind.SERVER.name()))
                .and(PHYSICAL_FLOW_PARTICIPANT.PARTICIPANT_ENTITY_ID.eq(serverId))
                .and(getLifecycleCondition(options));
    }


    private Select<Record1<Long>> mkForFlowDiagram(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        long diagramId = options.entityReference().id();
        return DSL
                .select(PhysicalFlow.PHYSICAL_FLOW.ID)
                .from(PhysicalFlow.PHYSICAL_FLOW)
                .innerJoin(FLOW_DIAGRAM_ENTITY)
                .on(PhysicalFlow.PHYSICAL_FLOW.ID.eq(FLOW_DIAGRAM_ENTITY.ENTITY_ID))
                .where(FLOW_DIAGRAM_ENTITY.DIAGRAM_ID.eq(diagramId))
                .and(FLOW_DIAGRAM_ENTITY.ENTITY_KIND.eq(EntityKind.PHYSICAL_FLOW.name()))
                .and(getLifecycleCondition(options));
    }


    private Select<Record1<Long>> mkForLogicalFlow(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        long logicalFlowId = options.entityReference().id();
        return DSL
                .select(PhysicalFlow.PHYSICAL_FLOW.ID)
                .from(PhysicalFlow.PHYSICAL_FLOW)
                .where(PhysicalFlow.PHYSICAL_FLOW.LOGICAL_FLOW_ID.eq(logicalFlowId))
                .and(getLifecycleCondition(options));
    }


    private Select<Record1<Long>> mkForChangeSet(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        return DSL
                .select(PhysicalFlow.PHYSICAL_FLOW.ID)
                .from(PhysicalFlow.PHYSICAL_FLOW)
                .innerJoin(CHANGE_UNIT).on(PhysicalFlow.PHYSICAL_FLOW.ID.eq(CHANGE_UNIT.SUBJECT_ENTITY_ID)
                        .and(CHANGE_UNIT.SUBJECT_ENTITY_KIND.eq(EntityKind.PHYSICAL_FLOW.name())))
                .where(CHANGE_UNIT.CHANGE_SET_ID.eq(options.entityReference().id()))
                .and(getLifecycleCondition(options));
    }


    private Select<Record1<Long>> mkForPhysicalSpecification(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        long specificationId = options.entityReference().id();
        return DSL
                .select(PhysicalFlow.PHYSICAL_FLOW.ID)
                .from(PhysicalFlow.PHYSICAL_FLOW)
                .where(PhysicalFlow.PHYSICAL_FLOW.SPECIFICATION_ID.eq(specificationId));
    }


    public Condition getLifecycleCondition(IdSelectionOptions options) {
        return options.entityLifecycleStatuses().contains(EntityLifecycleStatus.REMOVED)
                ? DSL.trueCondition()
                : PHYSICAL_FLOW_NOT_REMOVED;
    }
}

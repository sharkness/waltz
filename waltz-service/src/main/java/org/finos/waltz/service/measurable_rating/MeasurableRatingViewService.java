package org.finos.waltz.service.measurable_rating;

import org.finos.waltz.common.SetUtilities;
import org.finos.waltz.data.GenericSelector;
import org.finos.waltz.data.GenericSelectorFactory;
import org.finos.waltz.data.rating_scheme.RatingSchemeDAO;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.IdSelectionOptions;
import org.finos.waltz.model.allocation.Allocation;
import org.finos.waltz.model.allocation_scheme.AllocationScheme;
import org.finos.waltz.model.application.Application;
import org.finos.waltz.model.application.ImmutableAssessmentsView;
import org.finos.waltz.model.application.ImmutableMeasurableRatingsView;
import org.finos.waltz.model.assessment_definition.AssessmentDefinition;
import org.finos.waltz.model.assessment_rating.AssessmentRating;
import org.finos.waltz.model.measurable.Measurable;
import org.finos.waltz.model.measurable.MeasurableHierarchy;
import org.finos.waltz.model.measurable_category.MeasurableCategory;
import org.finos.waltz.model.measurable_rating.ImmutableAllocationsView;
import org.finos.waltz.model.measurable_rating.ImmutableDecommissionsView;
import org.finos.waltz.model.measurable_rating.ImmutableMeasurableRatingCategoryView;
import org.finos.waltz.model.measurable_rating.ImmutableMeasurableRatingView;
import org.finos.waltz.model.measurable_rating.MeasurableRating;
import org.finos.waltz.model.measurable_rating.MeasurableRatingCategoryView;
import org.finos.waltz.model.measurable_rating.MeasurableRatingView;
import org.finos.waltz.model.measurable_rating_planned_decommission.MeasurableRatingPlannedDecommission;
import org.finos.waltz.model.measurable_rating_planned_decommission.MeasurableRatingPlannedDecommissionInfo;
import org.finos.waltz.model.measurable_rating_replacement.MeasurableRatingReplacement;
import org.finos.waltz.model.rating.RatingSchemeItem;
import org.finos.waltz.service.allocation.AllocationService;
import org.finos.waltz.service.allocation_schemes.AllocationSchemeService;
import org.finos.waltz.service.application.ApplicationService;
import org.finos.waltz.service.assessment_definition.AssessmentDefinitionService;
import org.finos.waltz.service.assessment_rating.AssessmentRatingService;
import org.finos.waltz.service.measurable.MeasurableService;
import org.finos.waltz.service.measurable_category.MeasurableCategoryService;
import org.finos.waltz.service.measurable_rating_planned_decommission.MeasurableRatingPlannedDecommissionService;
import org.finos.waltz.service.measurable_rating_replacement.MeasurableRatingReplacementService;
import org.finos.waltz.service.rating_scheme.RatingSchemeService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.finos.waltz.common.MapUtilities.indexBy;
import static org.finos.waltz.common.SetUtilities.asSet;
import static org.finos.waltz.common.SetUtilities.map;

@Service
public class MeasurableRatingViewService {

    private final MeasurableRatingService measurableRatingService;
    private final MeasurableService measurableService;
    private final MeasurableRatingPlannedDecommissionService measurableRatingPlannedDecommissionService;
    private final MeasurableRatingReplacementService measurableRatingReplacementService;
    private final RatingSchemeService ratingSchemeService;
    private final RatingSchemeDAO ratingSchemeDAO;


    private final MeasurableCategoryService measurableCategoryService;
    private final AssessmentRatingService assessmentRatingService;
    private final AssessmentDefinitionService assessmentDefinitionService;
    private final AllocationService allocationService;
    private final AllocationSchemeService allocationSchemeService;
    private final ApplicationService applicationService;

    private final GenericSelectorFactory GENERIC_SELECTOR_FACTORY = new GenericSelectorFactory();


    public MeasurableRatingViewService(MeasurableRatingService measurableRatingService,
                                       MeasurableService measurableService,
                                       MeasurableRatingPlannedDecommissionService measurableRatingPlannedDecommissionService,
                                       MeasurableRatingReplacementService measurableRatingReplacementService,
                                       RatingSchemeService ratingSchemeService,
                                       RatingSchemeDAO ratingSchemeDAO,
                                       MeasurableCategoryService measurableCategoryService,
                                       AssessmentRatingService assessmentRatingService,
                                       AssessmentDefinitionService assessmentDefinitionService,
                                       AllocationService allocationService,
                                       AllocationSchemeService allocationSchemeService,
                                       ApplicationService applicationService){

        this.measurableRatingService = measurableRatingService;
        this.measurableService = measurableService;
        this.measurableRatingPlannedDecommissionService = measurableRatingPlannedDecommissionService;
        this.measurableRatingReplacementService = measurableRatingReplacementService;
        this.ratingSchemeService = ratingSchemeService;
        this.ratingSchemeDAO = ratingSchemeDAO;
        this.measurableCategoryService = measurableCategoryService;
        this.assessmentRatingService = assessmentRatingService;
        this.assessmentDefinitionService = assessmentDefinitionService;
        this.allocationService = allocationService;
        this.allocationSchemeService = allocationSchemeService;
        this.applicationService = applicationService;
    }


    public MeasurableRatingView getViewById(long id) {

        MeasurableRating measurableRating = measurableRatingService.getById(id);

        Measurable measurable = measurableService.getById(measurableRating.measurableId());
        Application application = applicationService.getById(measurableRating.entityReference().id());

        if (measurable == null) {

            return ImmutableMeasurableRatingView.builder()
                    .measurableRating(measurableRating)
                    .application(application)
                    .measurable(null)
                    .rating(null)
                    .decommission(null)
                    .replacements(emptyList())
                    .build();
        } else {

            List<RatingSchemeItem> ratingSchemeItems = ratingSchemeService.findRatingSchemeItemsForEntityAndCategory(
                    measurableRating.entityReference(),
                    measurable.categoryId());

            Map<String, RatingSchemeItem> itemsByCode = indexBy(ratingSchemeItems, RatingSchemeItem::rating);
            RatingSchemeItem rating = itemsByCode.get(String.valueOf(measurableRating.rating()));

            MeasurableRatingPlannedDecommission decomm = measurableRatingPlannedDecommissionService.getByMeasurableRatingId(id);

            Set<MeasurableRatingReplacement> replacementApps = decomm == null
                    ? Collections.emptySet()
                    : measurableRatingReplacementService.findByDecommId(decomm.id());

            List<AllocationScheme> schemes = allocationSchemeService.findByCategoryId(measurable.categoryId());

            Set<Allocation> allocations = allocationService.findByMeasurableRatingId(id);

            return ImmutableMeasurableRatingView.builder()
                    .measurableRating(measurableRating)
                    .application(application)
                    .measurable(measurable)
                    .rating(rating)
                    .decommission(decomm)
                    .replacements(replacementApps)
                    .allocations(allocations)
                    .allocationSchemes(schemes)
                    .build();
        }

    }

    public MeasurableRatingCategoryView getViewForCategoryAndSelector(IdSelectionOptions idSelectionOptions, long categoryId) {

        GenericSelector appSelector = GENERIC_SELECTOR_FACTORY.applyForKind(EntityKind.APPLICATION, idSelectionOptions);

        MeasurableCategory category = measurableCategoryService.getById(categoryId);
        List<MeasurableRating> ratings = measurableRatingService.findForCategoryAndSelector(appSelector.selector(), categoryId);
        List<Measurable> measurables = measurableService.findByCategoryId(categoryId);
        List<AllocationScheme> allocSchemes = allocationSchemeService.findByCategoryId(categoryId);
        Collection<Allocation> allocs = allocationService.findForCategoryAndSelector(appSelector.selector(), categoryId);

        Set<AssessmentDefinition> defs = SetUtilities.filter(
                assessmentDefinitionService.findByEntityKind(EntityKind.MEASURABLE_RATING),
                d -> d.qualifierReference()
                        .map(qualifierRef -> qualifierRef.id() == categoryId)
                        .orElse(false));

        List<AssessmentRating> assessments = assessmentRatingService.findByEntityKind(EntityKind.MEASURABLE_RATING);
        Set<RatingSchemeItem> assessmentRatingSchemeItems = ratingSchemeDAO.findRatingSchemeItemsByIds(map(assessments, AssessmentRating::ratingId));
        Set<RatingSchemeItem> measurableRatingSchemeItems = ratingSchemeDAO.findRatingSchemeItemsForSchemeIds(asSet(category.ratingSchemeId()));
        Collection<MeasurableRatingPlannedDecommission> decomms = measurableRatingPlannedDecommissionService.findForCategoryAndSelector(appSelector.selector(), categoryId);
        Collection<MeasurableRatingReplacement> replacements = measurableRatingReplacementService.findForCategoryAndSelector(appSelector.selector(), categoryId);
        Collection<MeasurableRatingPlannedDecommissionInfo> replacingDecomms = measurableRatingPlannedDecommissionService.findForReplacingEntitySelectorAndCategory(appSelector.selector(), categoryId);

        Set<MeasurableHierarchy> hierarchyForCategory = measurableService.findHierarchyForCategory(categoryId);

        ImmutableMeasurableRatingsView ratingsView = ImmutableMeasurableRatingsView
                .builder()
                .measurableRatings(ratings)
                .measurables(measurables)
                .measurableCategories(asSet(category))
                .measurableHierarchy(hierarchyForCategory)
                .ratingSchemeItems(measurableRatingSchemeItems)
                .build();

        ImmutableAssessmentsView assessmentsView = ImmutableAssessmentsView
                .builder()
                .assessmentRatings(assessments)
                .assessmentDefinitions(defs)
                .ratingSchemeItems(assessmentRatingSchemeItems)
                .build();

        ImmutableAllocationsView allocationsView = ImmutableAllocationsView
                .builder()
                .allocations(allocs)
                .allocationSchemes(allocSchemes)
                .build();

        ImmutableDecommissionsView decommissionView = ImmutableDecommissionsView
                .builder()
                .plannedDecommissions(decomms)
                .plannedReplacements(replacements)
                .replacingDecommissions(replacingDecomms)
                .build();

        return ImmutableMeasurableRatingCategoryView.builder()
                .measurableRatings(ratingsView)
                .allocations(allocationsView)
                .primaryAssessments(assessmentsView)
                .decommissions(decommissionView)
                .build();
    }
}

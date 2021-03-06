SELECT
  conceptSet.concept_id                                          AS id,
  coalesce(concept_locale.name, cn.concept_full_name)            AS name,
  cdt.name                                                       AS dataType,
  conceptSet.is_set                                              AS isset,
  COALESCE(cv.code, cn.concept_full_name, cn.concept_short_name) AS title
FROM concept_view parentConcept
  INNER JOIN concept_set setMembers ON setMembers.concept_set = parentConcept.concept_id
  INNER JOIN concept conceptSet ON conceptSet.concept_id = setMembers.concept_id AND conceptSet.retired IS FALSE
  INNER JOIN concept_datatype cdt ON conceptSet.datatype_id = cdt.concept_datatype_id AND cdt.retired IS FALSE
  LEFT OUTER JOIN concept_reference_term_map_view cv
    ON (cv.concept_id = conceptSet.concept_id AND cv.concept_map_type_name = 'SAME-AS' AND
        cv.concept_reference_source_name = 'EndTB-Export')
  LEFT OUTER JOIN concept_view cn ON (cn.concept_id = conceptSet.concept_id)
  LEFT OUTER JOIN concept_name concept_locale
    ON concept_locale.concept_id = conceptSet.concept_id AND concept_locale.locale = :locale
       AND concept_name_type = 'FULLY_SPECIFIED' AND concept_locale.voided IS FALSE
  LEFT OUTER JOIN concept_name parent_concept_locale ON parent_concept_locale.concept_id = parentConcept.concept_id AND
                                                        parent_concept_locale.locale = :locale AND
                                                        parent_concept_locale.concept_name_type = 'FULLY_SPECIFIED' AND
                                                        parent_concept_locale.voided IS FALSE
WHERE (parent_concept_locale.name = :parentConceptName OR parentConcept.concept_full_name = :parentConceptName) AND
      parentConcept.retired IS FALSE
ORDER BY setMembers.sort_weight;
package com.unityskill.project;

import com.unityskill.project.entity.AutoTriggerRule;
import com.unityskill.project.entity.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AutoTriggerRuleRepository extends JpaRepository<AutoTriggerRule, UUID> {

    List<AutoTriggerRule> findAllByProjectId(UUID projectId);

    List<AutoTriggerRule> findAllByProjectIdAndTriggerType(UUID projectId, TriggerType triggerType);
}
